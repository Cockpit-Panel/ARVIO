package com.arflix.tv.ui.screens.details

import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.EpisodeIdentity
import com.arflix.tv.data.repository.WatchHistoryEntry
import com.arflix.tv.util.Constants

data class EpisodePlaybackProgress(
    val percent: Int,
    val positionMs: Long,
    val durationMs: Long,
)

internal fun episodePlaybackProgress(
    tmdbId: Int,
    episodes: List<Episode>,
    history: List<WatchHistoryEntry>,
): Map<EpisodeIdentity, EpisodePlaybackProgress> {
    if (episodes.isEmpty() || history.isEmpty()) return emptyMap()
    val episodeByCanonicalCoordinates = episodes.associateBy {
        it.tmdbSeasonNumber to it.tmdbEpisodeNumber
    }

    return history.asSequence()
        .filter { entry ->
            entry.media_type == "tv" &&
                entry.show_tmdb_id == tmdbId &&
                entry.season != null &&
                entry.episode != null
        }
        .sortedByDescending { it.updated_at ?: it.paused_at.orEmpty() }
        .mapNotNull { entry ->
            val episode = episodeByCanonicalCoordinates[entry.season to entry.episode]
                ?: return@mapNotNull null
            if (episode.isWatched) return@mapNotNull null

            val positionSeconds = normalizeStoredSeconds(entry.position_seconds)
            val durationSeconds = normalizeStoredSeconds(entry.duration_seconds)
            val storedPercent = (entry.progress.coerceIn(0f, 1f) * 100f).toInt()
            val percent = when {
                durationSeconds > 0L && positionSeconds > 0L ->
                    ((positionSeconds.toDouble() / durationSeconds.toDouble()) * 100.0).toInt()
                storedPercent > 0 -> storedPercent
                positionSeconds > 0L -> 1
                else -> 0
            }.coerceIn(0, 100)

            if (percent >= Constants.WATCHED_THRESHOLD) return@mapNotNull null
            if (percent < Constants.MIN_PROGRESS_THRESHOLD && positionSeconds < 60L) {
                return@mapNotNull null
            }

            episode.identity to EpisodePlaybackProgress(
                percent = percent,
                positionMs = positionSeconds.coerceAtLeast(0L) * 1_000L,
                durationMs = durationSeconds.coerceAtLeast(0L) * 1_000L,
            )
        }
        .distinctBy { it.first }
        .toMap()
}

private fun normalizeStoredSeconds(value: Long): Long =
    if (value > 86_400L) value / 1_000L else value
