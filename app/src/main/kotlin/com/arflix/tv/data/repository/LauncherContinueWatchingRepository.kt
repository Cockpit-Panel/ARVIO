package com.arflix.tv.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.MainActivity
import com.arflix.tv.R
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.SportsAddonCapabilities
import com.arflix.tv.navigation.Screen
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class LauncherContinueWatchingRequest(
    val mediaType: MediaType,
    val mediaId: Int,
    val season: Int? = null,
    val episode: Int? = null,
    val startPositionMs: Long? = null
)

internal data class LauncherContinueWatchingProgram(val item: ContinueWatchingItem, val artwork: LauncherArtwork)

@Singleton
class LauncherContinueWatchingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val traktRepository: TraktRepository,
    private val remoteSyncManager: com.arflix.tv.data.repository.sync.RemoteSyncManager,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val streamRepository: StreamRepository,
    private val mediaRepository: MediaRepository,
    private val artworkRepository: LauncherArtworkRepository
) {

    private val titleCache = LruCache<String, String>(200)
    private val episodeCache = LruCache<String, TmdbEpisode>(200)
    private val publicationMutex = Mutex()
    companion object {
        private const val TAG = "LauncherCW"
        private const val CHANNEL_INTERNAL_ID = "arvio_continue_watching_channel"
        private const val PREVIEW_PROGRAM_PREFIX = "arvio_continue_preview"
        private const val WATCH_NEXT_PROGRAM_PREFIX = "arvio_continue_watchnext"
        private const val URI_SCHEME = "arvio"
        private const val URI_HOST = "continue"
    }

    suspend fun refreshForCurrentProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supportsLauncherPublishing()) return

        publicationMutex.withLock {
            val activeProfileId = runCatching { profileManager.getProfileIdSync() }.getOrDefault("")
            if (activeProfileId.isBlank()) {
                clearPublishedRows()
                return
            }
            val language = mediaRepository.contentLanguage
            val items = loadPublisherItems()
            val semaphore = Semaphore(4)
            val programs = coroutineScope {
                items.map { item -> async {
                    semaphore.withPermit {
                        val logo = withTimeoutOrNull(4_000L) { mediaRepository.getLogoUrl(item.toMediaItem()) }
                        LauncherContinueWatchingProgram(item, artworkRepository.artworkFor(item, activeProfileId, language, logo))
                    }
                } }.awaitAll()
            }
            // A switch/logout while artwork loads must not republish the old profile.
            if (profileManager.getProfileIdSync() != activeProfileId) return
            withContext(Dispatchers.IO) {
                runTvProviderCall("refresh launcher continue watching") {
                    syncPublishedRows(programs)
                }
            }
        }
    }

    suspend fun clearPublishedPrograms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supportsLauncherPublishing()) return
        publicationMutex.withLock { clearPublishedRows() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun clearPublishedRows() {
        withContext(Dispatchers.IO) {
            runTvProviderCall("clear launcher continue watching") {
                val channelId = findExistingChannelId()
                if (channelId != null) {
                    deletePreviewPrograms(channelId)
                }
                deleteWatchNextPrograms()
                artworkRepository.clear()
            }
        }
    }

    private fun supportsLauncherPublishing(): Boolean {
        val pm = context.packageManager
        val hasTvFeature = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
        if (!hasTvFeature) return false

        // Extra hardening: some phones/ROMs can expose odd feature combinations.
        // Only publish launcher channels if the TV provider authority actually exists.
        return runCatching {
            context.packageManager.resolveContentProvider("android.media.tv", 0) != null
        }.getOrDefault(false)
    }

    private suspend fun loadPublisherItems(): List<ContinueWatchingItem> {
        val installedAddons = streamRepository.installedAddons.first()
        val primaryItems = runCatching { remoteSyncManager.getContinueWatching() }.getOrDefault(emptyList())
        val filteredPrimary = primaryItems.filterNot { item ->
            SportsAddonCapabilities.isLiveStreamOrSportsItem(
                mediaType = item.mediaType,
                id = item.id,
                streamAddonId = item.streamAddonId,
                title = item.title,
                addons = installedAddons,
                isAddonNative = !item.addonNativeId.isNullOrBlank()
            )
        }

        val localItems = runCatching { traktRepository.getLocalContinueWatchingSnapshot() }.getOrDefault(emptyList())
        val historyItems = runCatching { watchHistoryRepository.getContinueWatching() }.getOrDefault(emptyList())
            .map(ContinueWatchingMerge::fromHistory)
            .sortedByDescending { it.updatedAtMs }
            .distinctBy { "${it.mediaType}:${it.id}" }
        val mergedItems = ContinueWatchingMerge.merge(
            filteredPrimary.ifEmpty { historyItems.ifEmpty { localItems } }, localItems, historyItems
        ).filterNot { item ->
            SportsAddonCapabilities.isLiveStreamOrSportsItem(
                mediaType = item.mediaType,
                id = item.id,
                streamAddonId = item.streamAddonId,
                title = item.title,
                addons = installedAddons,
                isAddonNative = !item.addonNativeId.isNullOrBlank()
            )
        }
        val selectedItems = traktRepository.filterDismissedContinueWatchingItems(mergedItems)
            .take(Constants.MAX_CONTINUE_WATCHING)
        selectedItems.filter { !it.addonNativeId.isNullOrBlank() }
            .forEach { mediaRepository.cacheItem(it.toMediaItem()) }

       // Limit of 4 simultaneous calls to avoid saturating the network
        val semaphore = Semaphore(4)
        val currentLang = mediaRepository.contentLanguage

        return coroutineScope {
            selectedItems.map { item ->
                async {
                    semaphore.withPermit {
                        // Cache keys including current language
                        val titleKey = "${currentLang}_${item.mediaType}_${item.id}"
                        val epKey = "${currentLang}_tv_${item.id}_${item.season}_${item.episode}"

                        val localizedTitle = if (item.id <= 0) item.title else titleCache.get(titleKey) ?: runCatching {
                            val t = if (item.mediaType == MediaType.TV) {
                                mediaRepository.getLightweightTvTitle(item.id, currentLang)
                            } else {
                                mediaRepository.getLightweightMovieTitle(item.id, currentLang)
                            }
                            t?.takeIf { it.isNotBlank() }?.also { titleCache.put(titleKey, it) }
                        }.getOrNull() ?: item.title

                        val episode = if (item.id > 0 && item.mediaType == MediaType.TV && item.season != null && item.episode != null) {
                            episodeCache.get(epKey) ?: runCatching {
                                mediaRepository.getLightweightEpisode(
                                    item.id,
                                    item.season,
                                    item.episode,
                                    currentLang
                                )?.also { episodeCache.put(epKey, it) }
                            }.getOrNull()
                        } else {
                            null
                        }

                        item.copy(
                            title = localizedTitle,
                            episodeTitle = episode?.name?.takeIf { it.isNotBlank() } ?: item.episodeTitle,
                            episodeStillPath = item.episodeStillPath?.takeIf { it.isNotBlank() }
                                ?: episode?.stillPath?.takeIf { it.isNotBlank() }?.let { "${Constants.IMAGE_BASE_LARGE}$it" }
                        )
                    }
                }
            }.awaitAll()
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    internal fun syncPublishedRows(items: List<LauncherContinueWatchingProgram>) {
        val channelId = ensurePreviewChannel() ?: return
        deletePreviewPrograms(channelId)
        deleteWatchNextPrograms()

        if (items.isEmpty()) {
            return
        }

        items.forEachIndexed { index, program ->
            insertPreviewProgram(channelId = channelId, item = program.item, artwork = program.artwork, index = index)
            insertWatchNextProgram(item = program.item, artwork = program.artwork, index = index)
        }
        artworkRepository.prune(items.mapNotNull { it.artwork.uri }.toSet())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun ensurePreviewChannel(): Long? {
        findExistingChannelId()?.let { return it }

        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(context.getString(R.string.continue_watching))
            .setDescription("Resume watching in Arvio")
            .setInternalProviderId(CHANNEL_INTERNAL_ID)
            .build()

        val channelUri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            channel.toContentValues()
        ) ?: return null

        val channelId = ContentUris.parseId(channelUri)
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)?.let { bitmap ->
            runCatching { ChannelLogoUtils.storeChannelLogo(context, channelId, bitmap) }
        }
        runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun insertPreviewProgram(channelId: Long, item: ContinueWatchingItem, artwork: LauncherArtwork, index: Int) {
        val program = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(item.toPreviewType())
            .setTitle(item.title.ifBlank { context.getString(R.string.continue_watching) })
            .setDescription(item.buildSubtitle())
            .setInternalProviderId(item.previewProgramId())
            .setPosterArtUri(artwork.uri)
            .setPosterArtAspectRatio(artwork.aspectRatio)
            .setThumbnailUri(artwork.uri)
            .setThumbnailAspectRatio(artwork.aspectRatio)
            .apply {
                if (item.mediaType == MediaType.TV) {
                    (item.displaySeason ?: item.season)?.let { setSeasonNumber(it) }
                    (item.displayEpisode ?: item.episode)?.let { setEpisodeNumber(it) }
                    item.episodeTitle?.takeIf { it.isNotBlank() }?.let { setEpisodeTitle(it) }
                }
            }
            .setIntentUri(buildLaunchIntent(item).toUri(Intent.URI_INTENT_SCHEME).let(Uri::parse))
            .setWeight((Constants.MAX_CONTINUE_WATCHING - index).coerceAtLeast(1))
            .build()

        context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun insertWatchNextProgram(item: ContinueWatchingItem, artwork: LauncherArtwork, index: Int) {
        val builder = WatchNextProgram.Builder()
            .setType(item.toPreviewType())
            .setTitle(item.title.ifBlank { context.getString(R.string.continue_watching) })
            .setDescription(item.buildSubtitle())
            .setInternalProviderId(item.watchNextProgramId())
            .setIntentUri(buildLaunchIntent(item).toUri(Intent.URI_INTENT_SCHEME).let(Uri::parse))
            .setPosterArtUri(artwork.uri)
            .setPosterArtAspectRatio(artwork.aspectRatio)
            .setThumbnailUri(artwork.uri)
            .setThumbnailAspectRatio(artwork.aspectRatio)
            .apply {
                if (item.mediaType == MediaType.TV) {
                    (item.displaySeason ?: item.season)?.let { setSeasonNumber(it) }
                    (item.displayEpisode ?: item.episode)?.let { setEpisodeNumber(it) }
                    item.episodeTitle?.takeIf { it.isNotBlank() }?.let { setEpisodeTitle(it) }
                }
            }
            .setWatchNextType(if (item.isUpNext) TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                else TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis() - index)

        val durationMs = item.durationSeconds.coerceAtLeast(0L) * 1000L
        val positionMs = item.resumePositionSeconds.coerceAtLeast(0L) * 1000L
        if (durationMs > 0L) {
            builder.setDurationMillis(durationMs.toInt())
        }
        if (positionMs > 0L) {
            builder.setLastPlaybackPositionMillis(positionMs.toInt())
        }

        context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, builder.build().toContentValues())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deletePreviewPrograms(channelId: Long) {
        val projection = arrayOf(BaseColumns._ID, TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID)
        runTvProviderCall("delete preview rows") {
            context.contentResolver.query(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                val channelIdIndex = cursor.getColumnIndexOrThrow(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID)
                while (cursor.moveToNext()) {
                    if (cursor.getLong(channelIdIndex) != channelId) continue
                    val rowId = cursor.getLong(idIndex)
                    context.contentResolver.delete(
                        ContentUris.withAppendedId(TvContractCompat.PreviewPrograms.CONTENT_URI, rowId),
                        null,
                        null
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deleteWatchNextPrograms() {
        val projection = arrayOf(BaseColumns._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
        runTvProviderCall("delete watch-next rows") {
            context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                val internalIdIndex = cursor.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
                while (cursor.moveToNext()) {
                    val internalId = cursor.getString(internalIdIndex).orEmpty()
                    if (!internalId.startsWith(WATCH_NEXT_PROGRAM_PREFIX)) continue
                    val rowId = cursor.getLong(idIndex)
                    context.contentResolver.delete(
                        ContentUris.withAppendedId(TvContractCompat.WatchNextPrograms.CONTENT_URI, rowId),
                        null,
                        null
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun findExistingChannelId(): Long? {
        val projection = arrayOf(BaseColumns._ID, TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID)
        return runTvProviderCall("find existing preview channel", null) {
            context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                val internalIdIndex = cursor.getColumnIndexOrThrow(TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID)
                while (cursor.moveToNext()) {
                    if (cursor.getString(internalIdIndex) == CHANNEL_INTERNAL_ID) {
                        return@runTvProviderCall cursor.getLong(idIndex)
                    }
                }
            }
            null
        }
    }

    private fun runTvProviderCall(action: String, block: () -> Unit) {
        try {
            block()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w(TAG, "Skipping launcher publish action: $action", error)
        }
    }

    private fun <T> runTvProviderCall(action: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w(TAG, "Skipping launcher publish action: $action", error)
            fallback
        }
    }

    private fun buildLaunchIntent(item: ContinueWatchingItem): Intent {
        val request = LauncherContinueWatchingRequest(
            mediaType = item.mediaType,
            mediaId = item.id,
            season = item.season,
            episode = item.episode,
            startPositionMs = item.resumePositionSeconds.takeIf { it > 0L }?.times(1000L)
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            data = request.toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private fun ContinueWatchingItem.toPreviewType(): Int {
        return if (mediaType == MediaType.TV) {
            TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
        } else {
            TvContractCompat.PreviewPrograms.TYPE_MOVIE
        }
    }

    private fun ContinueWatchingItem.previewProgramId(): String {
        return "$PREVIEW_PROGRAM_PREFIX:${mediaType.name}:${id}:${season ?: -1}:${episode ?: -1}"
    }

    private fun ContinueWatchingItem.watchNextProgramId(): String {
        return "$WATCH_NEXT_PROGRAM_PREFIX:${mediaType.name}:${id}:${season ?: -1}:${episode ?: -1}"
    }

    private fun ContinueWatchingItem.buildSubtitle(): String {
        val episodeLabel = if (mediaType == MediaType.TV && season != null && episode != null) {
            context.getString(R.string.continue_season_episode, season, episode)
        } else {
            context.getString(R.string.continue_label)
        }
        val resumeClock = resumePositionSeconds.takeIf { it > 0L }?.let(::formatResumeClock)
        return when {
            !resumeClock.isNullOrBlank() ->
                context.getString(R.string.launcher_continue_from, episodeLabel, resumeClock)
            !episodeTitle.isNullOrBlank() ->
                context.getString(R.string.launcher_continue_dash, episodeLabel, episodeTitle)
            else -> episodeLabel
        }
    }

    private fun formatResumeClock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return if (hours > 0L) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}

fun LauncherContinueWatchingRequest.toUri(): Uri {
    return Uri.Builder()
        .scheme("arvio")
        .authority("continue")
        .appendPath(mediaType.name.lowercase())
        .appendPath(mediaId.toString())
        .apply {
            season?.let { appendQueryParameter("season", it.toString()) }
            episode?.let { appendQueryParameter("episode", it.toString()) }
            startPositionMs?.let { appendQueryParameter("startPositionMs", it.toString()) }
        }
        .build()
}

fun Uri.toLauncherContinueWatchingRequest(): LauncherContinueWatchingRequest? {
    if (scheme != "arvio" || authority != "continue") return null
    val typeSegment = pathSegments.getOrNull(0) ?: return null
    val mediaId = pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
    val mediaType = if (typeSegment.equals("tv", ignoreCase = true)) MediaType.TV else MediaType.MOVIE
    return LauncherContinueWatchingRequest(
        mediaType = mediaType,
        mediaId = mediaId,
        season = getQueryParameter("season")?.toIntOrNull(),
        episode = getQueryParameter("episode")?.toIntOrNull(),
        startPositionMs = getQueryParameter("startPositionMs")?.toLongOrNull()
    )
}
