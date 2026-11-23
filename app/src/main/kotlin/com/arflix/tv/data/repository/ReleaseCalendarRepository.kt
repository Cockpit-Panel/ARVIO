package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.data.api.TmdbMovieDetails
import com.arflix.tv.data.api.TmdbSeasonDetails
import com.arflix.tv.data.api.TmdbTvDetails
import com.arflix.tv.data.api.TraktApi
import com.arflix.tv.data.api.TraktCalendarEpisode
import com.arflix.tv.data.model.CalendarRelease
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.ReleaseCalendarSource
import com.arflix.tv.data.model.calendarReleaseOrder
import com.arflix.tv.data.repository.simkl.SimklAuthManager
import com.arflix.tv.data.repository.simkl.SimklSyncService
import com.arflix.tv.util.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import retrofit2.HttpException

internal data class CalendarWatchlists(
    val profileId: String,
    val items: Map<ReleaseCalendarSource, List<MediaItem>>,
    val warnings: List<String> = emptyList(),
    val failedSources: Set<ReleaseCalendarSource> = emptySet()
)

internal data class CalendarMonthResult(
    val entries: List<CalendarRelease>,
    val warnings: List<String>,
    val completedTitles: Set<Pair<MediaType, Int>> = emptySet(),
    val failedTitles: Set<Pair<MediaType, Int>> = emptySet()
)

internal data class CalendarTvSchedule(
    val details: TmdbTvDetails,
    val episodes: List<TmdbEpisode>,
    val seasonNumbers: Set<Int>,
    val expiresAt: Long
)

internal data class CalendarLoadProgress(
    val watchlists: CalendarWatchlists,
    val month: CalendarMonthResult,
    val watchlistsComplete: Boolean,
    val completedTitles: Set<Pair<MediaType, Int>> = emptySet()
)

/** Preserve cached dates only while their provider/title is pending; fresh removals win. */
internal fun mergeCalendarPreview(preview: List<CalendarRelease>, progress: CalendarLoadProgress): List<CalendarRelease> {
    val memberships = mergeCalendarWatchlists(progress.watchlists.items).associate { (it.media.mediaType to it.media.id) to it.sourceIds }
    val readSources = progress.watchlists.items.keys.map { it.id }.toSet()
    val failedSources = progress.watchlists.failedSources.map { it.id }.toSet()
    fun unresolvedSources(sources: Set<String>) =
        (if (progress.watchlistsComplete) emptySet() else sources - readSources) + (sources intersect failedSources)
    val cachedMemberships = preview.groupBy { it.media.mediaType to it.media.id }
        .mapValues { (_, entries) -> entries.flatMap { it.sourceIds }.toSet() }
    val retained = preview.mapNotNull { entry ->
        val key = entry.media.mediaType to entry.media.id
        if (key in progress.completedTitles) return@mapNotNull null
        val pendingSources = unresolvedSources(entry.sourceIds)
        val sources = memberships[key].orEmpty() + pendingSources
        if (sources.isEmpty()) null else entry.copy(sourceIds = sources)
    }
    // Refreshed metadata does not prove a failed service removed its membership.
    // Keep that provenance even when another service successfully refreshed the same title.
    val refreshed = progress.month.entries.map { entry -> entry.copy(sourceIds = entry.sourceIds +
        unresolvedSources(cachedMemberships[entry.media.mediaType to entry.media.id].orEmpty())) }
    return (retained + refreshed).associateBy { it.id }.values
        .sortedWith(calendarReleaseOrder)
}

/** Read-only metadata with disposable public caches; private month previews are profile-scoped. */
@Singleton
class ReleaseCalendarRepository @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val traktApi: TraktApi,
    private val watchlistRepository: WatchlistRepository,
    private val traktRepository: TraktRepository,
    private val simklAuthManager: SimklAuthManager,
    private val simklSyncService: SimklSyncService,
    private val mdbListRepository: MdbListRepository,
    private val mediaRepository: MediaRepository,
    private val profileManager: ProfileManager,
    private val diskCache: ReleaseCalendarCache
) {
    private val permits = Semaphore(5)
    private val backgroundTitlePermits = Semaphore(5)
    // Old seasons must not fill every metadata slot before a newly arrived title
    // can publish its next episode. Season reads still count toward the total five.
    private val seasonPermits = Semaphore(2)
    // Optional times/logos must not occupy the slots used to find confirmed dates.
    private val timePermits = Semaphore(2)
    private val artworkPermits = Semaphore(2)
    private data class Cached(val value: Any, val at: Long)
    private val metadataCache = linkedMapOf<String, Cached>()
    private val detailCache = linkedMapOf<String, Cached>()
    private val titleCache = linkedMapOf<String, CalendarTitleProjection>()
    private val schedules = linkedMapOf<String, CalendarTvSchedule>()
    private val cacheGeneration = AtomicLong()
    private val missingMetadata = linkedMapOf<String, Long>()
    internal var clock: () -> Long = System::currentTimeMillis

    private class TimeServiceHealth {
        val succeeded = AtomicBoolean()
        val timedOut = AtomicBoolean()
    }

    private class TitleRead(val cacheOnly: Boolean, val forceRefresh: Boolean = false,
        val timeHealth: TimeServiceHealth = TimeServiceHealth()) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<TitleRead>
        val expiresAt = AtomicLong(Long.MAX_VALUE)
        fun include(expiry: Long) { expiresAt.updateAndGet { minOf(it, expiry) } }
    }
    // Bounded striped locks coalesce identical requests, including a restarted/adjacent month load.
    private val metadataLocks = Array(3) { Array(64) { Mutex() } }
    private val seasonBatchLocks = Array(64) { Mutex() }
    private val gson = Gson()

    internal suspend fun loadWatchlists(
        profileId: String,
        forceRefresh: Boolean = false,
        reuseWatchlists: CalendarWatchlists? = null,
        onProgress: suspend (CalendarWatchlists) -> Unit = {}
    ): CalendarWatchlists = coroutineScope {
        ensureProfile(profileId)
        val providers = listOf(ReleaseCalendarSource.ARVIO, ReleaseCalendarSource.TRAKT,
            ReleaseCalendarSource.SIMKL, ReleaseCalendarSource.MDBLIST)
        val completed = linkedMapOf<ReleaseCalendarSource, SourceRead>()
        val progressMutex = Mutex()
        fun snapshot() = CalendarWatchlists(
            profileId,
            providers.mapNotNull { source -> completed[source]?.items?.let { source to it } }.toMap(),
            providers.mapNotNull { completed[it]?.warning },
            providers.filter { completed[it]?.failed == true }.toSet()
        )
        providers.map { source -> async {
            val result = try {
                if (reuseWatchlists?.profileId == profileId && reuseWatchlists.items.containsKey(source) && source !in reuseWatchlists.failedSources &&
                    reuseWatchlists.warnings.none { it.startsWith(source.label) }) {
                    SourceRead(reuseWatchlists.items[source], reuseWatchlists.warnings.firstOrNull { it.startsWith(source.label) })
                } else withTimeoutOrNull(45_000) {
                    var warning: String? = null
                    when (source) {
                        ReleaseCalendarSource.ARVIO -> watchlistRepository.getLocalWatchlistItems()
                        ReleaseCalendarSource.TRAKT -> if (traktRepository.isAuthenticated.first()) {
                            val planned = traktRepository.getWatchlistSyncResultWithAuthState().second?.items
                                ?: throw IOException("Trakt watchlist unavailable")
                            progressMutex.withLock {
                                ensureProfile(profileId)
                                completed[source] = SourceRead(planned)
                                onProgress(snapshot())
                            }
                            (planned + traktRepository.getCalendarWatchingShows()).distinctBy { it.mediaType to it.id }
                        } else null
                        ReleaseCalendarSource.SIMKL -> if (simklAuthManager.isConnected()) {
                            val refreshed = simklSyncService.syncIfNeeded(forceRefresh)
                            // The sync status also includes playback. A playback outage must
                            // not discard a usable library snapshot needed by the calendar.
                            if (!refreshed) warning = "SIMKL could not be fully refreshed. Showing available titles."
                            simklSyncService.getWatchlistItems() + simklSyncService.getLibraryItems("watching")
                        } else null
                        ReleaseCalendarSource.MDBLIST -> {
                            val result = mdbListRepository.getWatchlist()
                            if (result.connected) result.items ?: throw IOException("MDBList watchlist unavailable") else null
                        }
                        ReleaseCalendarSource.ALL -> null
                    }.let { SourceRead(it, warning) }
                } ?: throw IOException("Watchlist request timed out")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                SourceRead(completed[source]?.items.orEmpty(), "${source.label} could not be refreshed.", failed = true)
            }
            progressMutex.withLock {
                ensureProfile(profileId)
                completed[source] = result
                onProgress(snapshot())
            }
        } }.awaitAll()
        ensureProfile(profileId)
        snapshot()
    }

    private data class SourceRead(val items: List<MediaItem>?, val warning: String? = null, val failed: Boolean = false)

    /** Start each title once as its provider arrives; a slow provider cannot hold up another one. */
    internal suspend fun loadCalendar(
        profileId: String,
        month: YearMonth,
        timezone: ZoneId,
        region: String,
        language: String = "en-US",
        forceRefresh: Boolean = false,
        retryTitles: Set<Pair<MediaType, Int>> = emptySet(),
        reuseWatchlists: CalendarWatchlists? = null,
        onProgress: suspend (CalendarLoadProgress) -> Unit = {}
    ): CalendarMonthResult = coroutineScope {
        ensureProfile(profileId)
        val progressMutex = Mutex()
        var watchlists = CalendarWatchlists(profileId, emptyMap())
        var watchlistsComplete = false
        val titles = linkedMapOf<Pair<MediaType, Int>, CalendarWatchlistTitle>()
        val results = linkedMapOf<Pair<MediaType, Int>, CalendarMonthResult>()
        val completedTitles = mutableSetOf<Pair<MediaType, Int>>()
        val jobs = mutableListOf<Job>()
        val timeHealth = TimeServiceHealth()
        fun currentResult() = combineResults(results.map { (key, result) ->
            result.copy(entries = result.entries.map { it.copy(sourceIds = titles.getValue(key).sourceIds) })
        }, watchlists.warnings)
        suspend fun publish() {
            ensureProfile(profileId)
            onProgress(CalendarLoadProgress(watchlists, currentResult(), watchlistsComplete, completedTitles.toSet()))
        }
        loadWatchlists(profileId, forceRefresh, reuseWatchlists) { snapshot ->
            progressMutex.withLock {
                ensureProfile(profileId)
                watchlists = snapshot
                prioritizeTitles(mergeCalendarWatchlists(snapshot.items), month).forEach { title ->
                    val key = title.media.mediaType to title.media.id
                    val isNew = key !in titles
                    titles[key] = title
                    if (isNew) jobs += launch {
                        val result = withContext(TitleRead(false, key in retryTitles, timeHealth)) { prioritizedTitleReleases(title, month, timezone, region, language) { result ->
                            progressMutex.withLock {
                                results[key] = result
                                publish()
                            }
                        } }
                        progressMutex.withLock {
                            results[key] = result
                            if (result.warnings.isEmpty()) completedTitles += key
                            publish()
                        }
                    }
                }
                // Existing releases immediately acquire any newly discovered source membership.
                publish()
            }
        }
        progressMutex.withLock {
            watchlistsComplete = true
            publish()
        }
        jobs.joinAll()
        ensureProfile(profileId)
        currentResult()
    }

    internal suspend fun loadMonth(
        watchlists: CalendarWatchlists,
        month: YearMonth,
        timezone: ZoneId,
        region: String,
        language: String = "en-US",
        cacheOnly: Boolean = false,
        retryTitles: Set<Pair<MediaType, Int>> = emptySet(),
        onProgress: suspend (CalendarMonthResult) -> Unit = {}
    ): CalendarMonthResult = coroutineScope {
        ensureProfile(watchlists.profileId)
        val progressMutex = Mutex()
        val partial = linkedMapOf<Pair<MediaType, Int>, CalendarMonthResult>()
        val timeHealth = TimeServiceHealth()
        suspend fun publish(title: CalendarWatchlistTitle, result: CalendarMonthResult) {
            progressMutex.withLock {
                ensureProfile(watchlists.profileId)
                partial[title.media.mediaType to title.media.id] = result
                onProgress(combineResults(partial.values, watchlists.warnings))
            }
        }
        val results = prioritizeTitles(mergeCalendarWatchlists(watchlists.items), month).map { title -> async {
            val result = withContext(TitleRead(cacheOnly, (title.media.mediaType to title.media.id) in retryTitles, timeHealth)) {
                prioritizedTitleReleases(title, month, timezone, region, language) { publish(title, it) }
            }
            result.copy(completedTitles = if (result.warnings.isEmpty()) setOf(title.media.mediaType to title.media.id) else emptySet())
                .also { publish(title, it) }
        } }.awaitAll()
        ensureProfile(watchlists.profileId)
        combineResults(results, watchlists.warnings)
    }

    private suspend fun prioritizedTitleReleases(
        title: CalendarWatchlistTitle, month: YearMonth, timezone: ZoneId, region: String, language: String,
        onProgress: suspend (CalendarMonthResult) -> Unit
    ): CalendarMonthResult = if (titlePriority(title, month) == 2) {
        // Do not queue hundreds of old movies ahead of an active show arriving
        // from another provider. All five network slots can still be used.
        backgroundTitlePermits.withPermit { titleReleases(title, month, timezone, region, language, onProgress) }
    } else titleReleases(title, month, timezone, region, language, onProgress)

    private suspend fun titleReleases(
        title: CalendarWatchlistTitle,
        month: YearMonth,
        timezone: ZoneId,
        region: String,
        language: String,
        onProgress: suspend (CalendarMonthResult) -> Unit
    ): CalendarMonthResult {
        val key = "${profileManager.getProfileIdSync()}|${title.media.mediaType}:${title.media.id}|$month|${timezone.id}|$region|$language|${title.media.traktId}"
        val generation = cacheGeneration.get()
        val forceRefresh = coroutineContext[TitleRead]?.forceRefresh == true
        val saved = if (forceRefresh) null else (synchronized(titleCache) { titleCache[key]?.takeIf { clock() < it.expiresAt } }
            ?: diskCache.readTitle(key)?.also { rememberTitle(key, it) })
        if (saved != null) {
            return CalendarMonthResult(saved.entries.map { it.copy(sourceIds = title.sourceIds) }, emptyList())
                .also { onProgress(it) }
        }
        val read = coroutineContext[TitleRead] ?: TitleRead(cacheOnly = false)
        return withContext(read) {
            val fetched = fetchTitleReleases(title, month, timezone, region, language, onProgress)
            val result = fetched.copy(failedTitles = if (fetched.warnings.isEmpty()) emptySet() else setOf(title.media.mediaType to title.media.id))
            onProgress(result)
            // Empty, successfully verified months are reusable too. Errors are never
            // recorded as empty schedules, and source membership always comes from today's lists.
            if (!read.cacheOnly && result.warnings.isEmpty() && read.expiresAt.get() > clock() && read.expiresAt.get() != Long.MAX_VALUE &&
                generation == cacheGeneration.get()) {
                val projection = CalendarTitleProjection(result.entries, read.expiresAt.get())
                rememberTitle(key, projection)
                diskCache.writeTitle(key, projection)
            }
            result
        }
    }

    private fun rememberTitle(key: String, projection: CalendarTitleProjection) {
        synchronized(titleCache) {
            titleCache[key] = projection
            while (titleCache.size > 2048) titleCache.remove(titleCache.keys.first())
        }
    }

    private suspend fun fetchTitleReleases(
        title: CalendarWatchlistTitle, month: YearMonth, timezone: ZoneId, region: String, language: String,
        onProgress: suspend (CalendarMonthResult) -> Unit
    ): CalendarMonthResult {
        var lastPublished: CalendarMonthResult? = null
        suspend fun publish(result: CalendarMonthResult) {
            if (result != lastPublished) {
                lastPublished = result
                onProgress(result)
            }
        }
        return try {
            val result = if (title.media.mediaType == MediaType.TV) {
                televisionReleases(title, month, timezone, language, ::publish)
            } else {
                val details = cached<TmdbMovieDetails>("movie:${title.media.id}:$language") {
                    tmdbApi.getMovieDetails(title.media.id, Constants.TMDB_API_KEY, language = language)
                }
                val enriched = title.copy(media = enrich(title.media, details.title, details.posterPath, details.backdropPath))
                CalendarMonthResult(calendarMovieReleases(enriched, details, month, region), emptyList())
            }
            publish(result)
            val logo = if (result.entries.isNotEmpty() && coroutineContext[TitleRead]?.cacheOnly != true) {
                try {
                    // Include queue time in the optional-artwork budget.
                    withTimeoutOrNull(3_000) {
                        artworkPermits.withPermit { mediaRepository.getLogoUrl(title.media.mediaType, title.media.id) }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            } else null
            if (logo == null && result.entries.isNotEmpty()) {
                // A failed optional artwork read can recover quickly without redoing
                // metadata for every title whenever the Calendar is reopened.
                coroutineContext[TitleRead]?.include(clock() + 2 * 60_000L)
            }
            result.copy(entries = result.entries.map { it.copy(logoUrl = logo) }).also { publish(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A later enrichment failure must not remove already confirmed dates.
            val warning = "Release dates for ${title.media.title} are unavailable."
            val fallback = lastPublished?.let { it.copy(warnings = it.warnings + warning) }
                ?: CalendarMonthResult(emptyList(), listOf(warning))
            fallback.also { publish(it) }
        }
    }

    private fun combineResults(results: Collection<CalendarMonthResult>, warnings: List<String>) = CalendarMonthResult(
            entries = results.flatMap { it.entries }.distinctBy { it.id }
                .sortedWith(calendarReleaseOrder),
            warnings = warnings + results.flatMap { it.warnings },
            completedTitles = results.flatMap { it.completedTitles }.toSet(),
            failedTitles = results.flatMap { it.failedTitles }.toSet()
        )

    private suspend fun televisionReleases(
        original: CalendarWatchlistTitle,
        month: YearMonth,
        timezone: ZoneId,
        language: String,
        onProgress: suspend (CalendarMonthResult) -> Unit
    ): CalendarMonthResult = coroutineScope {
        val generation = cacheGeneration.get()
        val scheduleKey = "tv-schedule:${original.media.id}:$language"
        val refresh = coroutineContext[TitleRead]?.forceRefresh == true
        val savedSchedule = if (refresh) null else (synchronized(schedules) { schedules[scheduleKey] }
            ?: diskCache.readMetadata<CalendarTvSchedule>(scheduleKey, CalendarTvSchedule::class.java)?.value)
            ?.takeIf { clock() < it.expiresAt }
        savedSchedule?.let { rememberSchedule(scheduleKey, it) }
        savedSchedule?.let { coroutineContext[TitleRead]?.include(it.expiresAt) }
        val details = savedSchedule?.details ?: cached<TmdbTvDetails>("tv:${original.media.id}:$language") {
            tmdbApi.getTvDetails(original.media.id, Constants.TMDB_API_KEY, language = language)
        }
        val first = month.atDay(1).minusDays(1)
        val last = month.atEndOfMonth().plusDays(1)
        val ended = details.status in setOf("Ended", "Canceled")
        val lastAired = calendarDate(details.lastEpisodeToAir?.airDate)
        val announcedRelease = calendarDate(details.nextEpisodeToAir?.airDate)?.let { it >= first } == true ||
            details.seasons.any { calendarDate(it.airDate)?.let { date -> date >= first && date <= last } == true }
        if (ended && lastAired != null && lastAired < first && !announcedRelease) {
            return@coroutineScope CalendarMonthResult(emptyList(), emptyList())
        }
        val title = original.copy(media = enrich(original.media, details.name, details.posterPath, details.backdropPath))
        // Read every potentially relevant season, including specials and unknown season dates.
        // A next-season premiere does not prove the previous season has finished airing.
        val seasons = details.seasons.filter { calendarDate(it.airDate)?.let { date -> date <= last } != false }
            .sortedByDescending { it.seasonNumber }
        val episodeMutex = Mutex()
        val knownEpisodes = linkedMapOf<Pair<Int, Int>, TmdbEpisode>()
        listOfNotNull(details.nextEpisodeToAir, details.lastEpisodeToAir).forEach {
            knownEpisodes[it.seasonNumber to it.episodeNumber] = it
        }
        var incompleteSeasons = false
        fun warnings() = if (incompleteSeasons) listOf("Some episode dates for ${title.media.title} are unavailable.") else emptyList()
        suspend fun publishDates() {
            onProgress(CalendarMonthResult(calendarEpisodeReleases(title, knownEpisodes.values.toList(), emptyList(), month, timezone), warnings()))
        }
        // TV details can already contain the next release; render it while seasons fill in.
        publishDates()
        val schedule = savedSchedule?.takeIf { seasons.all { season -> season.seasonNumber in it.seasonNumbers } }
        if (schedule != null) {
            coroutineContext[TitleRead]?.include(schedule.expiresAt)
            schedule.episodes.forEach { knownEpisodes[it.seasonNumber to it.episodeNumber] = it }
        } else {
            readSeasons(title.media.id, seasons.map { it.seasonNumber }, language) { data ->
                episodeMutex.withLock {
                    data?.episodes.orEmpty().forEach { knownEpisodes[it.seasonNumber to it.episodeNumber] = it }
                    incompleteSeasons = incompleteSeasons || data == null
                    publishDates()
                }
            }
            if (!incompleteSeasons && coroutineContext[TitleRead]?.cacheOnly != true && generation == cacheGeneration.get()) {
                val expiry = coroutineContext[TitleRead]?.expiresAt?.get() ?: clock() + CALENDAR_ACTIVE_TTL
                val verified = CalendarTvSchedule(details, knownEpisodes.values.map { it.copy(overview = null) }, seasons.map { it.seasonNumber }.toSet(), expiry)
                rememberSchedule(scheduleKey, verified)
                diskCache.writeValue(scheduleKey, verified)
            }
        }
        val episodes = knownEpisodes.values.toList()
        val relevantSeasons = episodes.filter { calendarDate(it.airDate)?.let { date -> date >= first && date <= last } == true }
            .map { it.seasonNumber }.distinct()
        val timed = authoritativeTimes(title.media, relevantSeasons)
        CalendarMonthResult(
            calendarEpisodeReleases(title, episodes, timed, month, timezone),
            warnings()
        )
    }

    private fun rememberSchedule(key: String, schedule: CalendarTvSchedule) = synchronized(schedules) {
        schedules.remove(key)
        schedules[key] = schedule
        var episodes = schedules.values.sumOf { it.episodes.size }
        while (schedules.size > 1024 || episodes > 40_000) {
            episodes -= schedules.remove(schedules.keys.first())!!.episodes.size
        }
    }

    /** Append up to twenty seasons per HTTP request, without excluding old seasons or specials. */
    private suspend fun readSeasons(
        tvId: Int,
        seasons: List<Int>,
        language: String,
        onSeason: suspend (TmdbSeasonDetails?) -> Unit
    ) {
        val lock = seasonBatchLocks[("$tvId:$language".hashCode() and Int.MAX_VALUE) % seasonBatchLocks.size]
        lock.withLock {
            val missing = mutableListOf<Int>()
            for (season in seasons) {
                val saved = cachedValue<TmdbSeasonDetails>("season:$tvId:$season:$language")
                if (saved != null) onSeason(saved) else missing += season
            }
            for (batch in missing.chunked(20)) {
                if (coroutineContext[TitleRead]?.cacheOnly == true) {
                    batch.forEach { onSeason(null) }
                    continue
                }
                val appended = if (batch.size > 1) try {
                    val response = seasonPermits.withPermit {
                        permits.withPermit {
                            withTimeoutOrNull(20_000) {
                                tmdbApi.getTvSeasons(tvId, Constants.TMDB_API_KEY,
                                    batch.joinToString(",") { "season/$it" }, language)
                            } ?: throw IOException("Season batch timed out")
                        }
                    }
                    withContext(Dispatchers.Default) {
                        batch.mapNotNull { season ->
                            runCatching {
                                val value = response.get("season/$season")?.takeIf { it.isJsonObject }?.asJsonObject
                                if (value?.get("episodes")?.isJsonArray != true || value.get("season_number")?.asInt != season) null
                                else season to gson.fromJson(value, TmdbSeasonDetails::class.java)
                            }.getOrNull()
                        }.toMap()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptyMap()
                } else emptyMap()
                for ((season, data) in appended) {
                    val key = "season:$tvId:$season:$language"
                    rememberMetadata(key, data)
                    includeMetadata(data, clock())
                    onSeason(data)
                    diskCache.writeValue(key, data)
                }
                // A missing/malformed appended block falls back independently; other
                // seasons remain usable and the next month reuses their normal cache keys.
                coroutineScope {
                    batch.filter { it !in appended }.map { season -> async {
                        val data = try {
                            cached<TmdbSeasonDetails>("season:$tvId:$season:$language", seasonPermits) {
                                permits.withPermit {
                                    tmdbApi.getTvSeason(tvId, season, Constants.TMDB_API_KEY, language = language)
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                        onSeason(data)
                    } }.awaitAll()
                }
            }
        }
    }

    private suspend fun authoritativeTimes(media: MediaItem, seasons: List<Int>): List<TraktCalendarEpisode> = coroutineScope {
        if (seasons.isEmpty() || Constants.TRAKT_CLIENT_ID.isBlank()) return@coroutineScope emptyList()
        val found = mutableListOf<TraktCalendarEpisode>()
        // Queueing is not a network failure. Start each optional request's budget
        // only after it acquires a permit, as with the web Calendar season reader.
        try {
            val traktId = media.traktId ?: cached<Int>("trakt-id:${media.id}", timePermits, 4_000) {
                traktApi.searchByTmdb(Constants.TRAKT_CLIENT_ID, media.id, "show")
                    .firstOrNull { it.show?.ids?.tmdb == media.id }?.show?.ids?.trakt
                    ?: throw IOException("No Trakt mapping")
            }
            seasons.map { season -> async {
                try {
                    val episodes = cached<List<TraktCalendarEpisode>>("trakt-season:$traktId:$season", timePermits, 4_000) {
                        traktApi.getCalendarSeasonEpisodes(Constants.TRAKT_CLIENT_ID, traktId, season)
                    }
                    synchronized(found) { found.addAll(episodes) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Other completed seasons can still contribute authoritative times.
                    coroutineContext[TitleRead]?.include(clock() + 2 * 60_000L)
                }
            } }.awaitAll()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Public Trakt metadata is optional, including when no account is connected.
            coroutineContext[TitleRead]?.include(clock() + 2 * 60_000L)
        }
        synchronized(found) { found.toList() }
    }

    private fun enrich(media: MediaItem, title: String, poster: String?, backdrop: String?): MediaItem = media.copy(
        title = title.ifBlank { media.title },
        image = normalizeWatchlistArtworkUrl(poster, false) ?: media.image,
        backdrop = normalizeWatchlistArtworkUrl(backdrop, true) ?: media.backdrop
    )

    private fun prioritizeTitles(titles: List<CalendarWatchlistTitle>, month: YearMonth): List<CalendarWatchlistTitle> =
        titles.sortedBy { titlePriority(it, month) }

    private fun titlePriority(title: CalendarWatchlistTitle, month: YearMonth): Int {
        val media = title.media
        val release = calendarDate(media.releaseDate)
        return when {
            media.mediaType == MediaType.TV && media.status !in setOf("Ended", "Canceled") -> 0
            release != null && release >= month.atDay(1).minusMonths(2) && release <= month.atEndOfMonth() -> 0
            media.mediaType == MediaType.TV -> 1
            else -> 2
        }
    }

    private fun ensureProfile(profileId: String) {
        if (profileManager.getProfileIdSync() != profileId) throw CancellationException("Calendar profile changed")
    }

    internal suspend fun invalidateMetadata() {
        cacheGeneration.incrementAndGet()
        synchronized(metadataCache) { metadataCache.clear(); detailCache.clear(); missingMetadata.clear() }
        synchronized(titleCache) { titleCache.clear() }
        synchronized(schedules) { schedules.clear() }
        diskCache.clearMetadata()
    }

    private fun rememberMetadata(key: String, value: Any, writtenAt: Long = clock()) {
        synchronized(metadataCache) {
            // Historical seasons must not evict summaries for the whole watchlist.
            val target = if (value is TmdbTvDetails || value is TmdbMovieDetails) detailCache else metadataCache
            target.remove(key)
            target[key] = Cached(value, writtenAt)
            val limit = if (target === detailCache) 1024 else 2048
            while (target.size > limit) target.remove(target.keys.first())
        }
    }

    private suspend fun includeMetadata(value: Any, writtenAt: Long) {
        coroutineContext[TitleRead]?.include(writtenAt + calendarMetadataTtl(value, clock()))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend inline fun <reified T : Any> cachedValue(key: String): T? {
        if (coroutineContext[TitleRead]?.forceRefresh == true) return null
        val memory = synchronized(metadataCache) {
            (detailCache[key] ?: metadataCache[key])?.takeIf { clock() - it.at in 0 until calendarMetadataTtl(it.value, clock()) }
        }
        if (memory != null) {
            includeMetadata(memory.value, memory.at)
            return memory.value as T
        }
        val disk = diskCache.readMetadata<T>(key, object : TypeToken<T>() {}.type) ?: return null
        if (clock() - disk.writtenAt !in 0 until calendarMetadataTtl(disk.value, clock())) return null
        rememberMetadata(key, disk.value, disk.writtenAt)
        includeMetadata(disk.value, disk.writtenAt)
        return disk.value
    }

    @Suppress("UNCHECKED_CAST")
    private suspend inline fun <reified T : Any> cached(key: String, requestPermits: Semaphore = permits, requestTimeoutMs: Long = 20_000, crossinline block: suspend () -> T): T {
        val forceRefresh = coroutineContext[TitleRead]?.forceRefresh == true
        val memory = synchronized(metadataCache) {
            (detailCache[key] ?: metadataCache[key])?.takeIf { clock() - it.at in 0 until calendarMetadataTtl(it.value, clock()) }
        }
        if (memory != null && !forceRefresh) {
            includeMetadata(memory.value, memory.at)
            return memory.value as T
        }
        val lockPool = when {
            key.startsWith("season:") -> 1
            key.startsWith("trakt-") -> 2
            else -> 0
        }
        return metadataLocks[lockPool][(key.hashCode() and Int.MAX_VALUE) % metadataLocks[lockPool].size].withLock {
            synchronized(metadataCache) {
                if (!forceRefresh && missingMetadata[key]?.let { clock() - it in 0 until 15 * 60_000L } == true) {
                    throw IOException("Metadata not found")
                }
            }
            cachedValue<T>(key)?.let { return@withLock it }
            if (coroutineContext[TitleRead]?.cacheOnly == true) throw IOException("Metadata not cached")
            val value = requestPermits.withPermit {
                val health = coroutineContext[TitleRead]?.timeHealth?.takeIf { key.startsWith("trakt-") }
                // A whole-service outage must not cost four seconds for every
                // queued season. One slow season cannot disable a healthy service.
                if (health?.timedOut?.get() == true && !health.succeeded.get()) throw IOException("Trakt timing unavailable")
                try {
                    val response = withTimeoutOrNull(requestTimeoutMs) { block() }
                    if (response == null) {
                        health?.timedOut?.set(true)
                        throw IOException("Metadata request timed out")
                    }
                    health?.succeeded?.set(true)
                    response
                } catch (error: HttpException) {
                    // A confirmed 404 should not trigger another network request on
                    // every date/month/tab change. Explicit Retry clears this cache.
                    if (error.code() == 404) synchronized(metadataCache) {
                        missingMetadata[key] = clock()
                        while (missingMetadata.size > 512) missingMetadata.remove(missingMetadata.keys.first())
                    }
                    throw error
                }
            }
            rememberMetadata(key, value)
            includeMetadata(value, clock())
            diskCache.writeValue(key, value)
            value
        }
    }
}
