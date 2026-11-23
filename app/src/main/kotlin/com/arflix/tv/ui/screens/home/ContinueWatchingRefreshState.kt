package com.arflix.tv.ui.screens.home

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.ContinueWatchingItem
import com.arflix.tv.util.EpisodeAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Startup and foreground sync share one flight, with at most one forced follow-up. */
internal class ContinueWatchingRefreshRequests {
    private var sequence = 0L
    private var active: Long? = null
    private var pendingForce = false

    @Synchronized fun claim(force: Boolean): Long? {
        if (active != null) {
            pendingForce = pendingForce || force
            return null
        }
        return (++sequence).also { active = it }
    }

    @Synchronized fun finish(ticket: Long): Boolean {
        if (active != ticket) return false
        active = null
        return pendingForce.also { pendingForce = false }
    }

    @Synchronized fun reset() {
        active = null
        pendingForce = false
    }
}

internal data class HomeTrackingState(
    val profileId: String,
    val provider: String?,
    val readMode: String?,
    val connections: List<Boolean>,
    val revisions: List<Long?>
)

internal fun observeHomeTrackingState(
    profiles: Flow<String>, settings: Flow<Preferences>, credentials: Flow<Preferences>
): Flow<HomeTrackingState> = combine(profiles, settings, credentials) { profile, prefs, tokens ->
    val prefix = "profile_${profile}_"
    HomeTrackingState(
        profile, prefs[stringPreferencesKey("${prefix}sync_provider")],
        prefs[stringPreferencesKey("${prefix}tracking_continue_read_mode_v2")],
        listOf("trakt_access_token", "simkl_access_token", "mdblist_api_key", "mdblist_access_token")
            .map { !tokens[stringPreferencesKey(prefix + it)].isNullOrBlank() },
        listOf("tracking_preferences_updated_at_v3", "simkl_credential_updated_at_v3", "mdblist_credential_updated_at_v3")
            .map { prefs[longPreferencesKey(prefix + it)] }
    )
}.distinctUntilChanged()

internal fun preloadedHomeCategories(incoming: List<Category>, current: List<Category>): List<Category> {
    val ready = (current + incoming).firstOrNull { row ->
        row.id == "continue_watching" && row.items.isNotEmpty() && row.items.none { it.isPlaceholder }
    }
    val watching = ready ?: Category("continue_watching", "Continue Watching",
        (1..5).map { MediaItem(-it, "", isPlaceholder = true) })
    return listOf(watching) + incoming.filterNot { it.id == "continue_watching" }
}

internal fun validatedContinueWatchingEpisode(item: ContinueWatchingItem, episodes: List<Episode>?): ContinueWatchingItem? {
    if (item.mediaType != MediaType.TV || item.season == null || item.episode == null ||
        !item.addonNativeId.isNullOrBlank()) return item
    if (item.isUpNext && item.releaseDate.isNotBlank() && !EpisodeAvailability.hasAired(item.releaseDate)) return null
    val matched = episodes?.firstOrNull { it.episodeNumber == item.episode } ?: return item
    if (!EpisodeAvailability.hasAired(matched.airDate, unknownIsAired = !item.isUpNext)) return null
    return item.copy(episodeTitle = item.episodeTitle ?: matched.name)
}

internal suspend fun loadContinueWatchingSeasons(
    items: List<ContinueWatchingItem>,
    cacheOnly: Boolean,
    peek: (Int, Int) -> List<Episode>?,
    fetch: suspend (Int, Int) -> List<Episode>
): Map<Pair<Int, Int>, List<Episode>?> = coroutineScope {
    val permits = Semaphore(4)
    items.mapNotNull { item ->
        item.season?.takeIf { item.mediaType == MediaType.TV && item.addonNativeId.isNullOrBlank() &&
            validatedContinueWatchingEpisode(item, null) != null }?.let { item.id to it }
    }.distinct().associateWith { (id, season) ->
        async {
            peek(id, season) ?: if (cacheOnly) null else permits.withPermit {
                try { fetch(id, season) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
            }
        }
    }.mapValues { (_, request) -> request.await() }
}
