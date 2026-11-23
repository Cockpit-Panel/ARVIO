package com.arflix.tv.ui.screens.details

import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.repository.WatchHistoryEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpisodePlaybackProgressTest {
    private val episode = Episode(
        id = 7,
        episodeNumber = 7,
        seasonNumber = 2,
        name = "Episode 7",
    )

    @Test
    fun usesExactEpisodePositionForCardProgressAndResume() {
        val result = episodePlaybackProgress(
            tmdbId = 123,
            episodes = listOf(episode),
            history = listOf(history(progress = 0.1f, position = 1_200, duration = 2_400)),
        )[episode.identity]

        assertThat(result?.percent).isEqualTo(50)
        assertThat(result?.positionMs).isEqualTo(1_200_000L)
    }

    @Test
    fun watchedEpisodeDoesNotExposePartialProgress() {
        val watched = episode.copy(isWatched = true)

        val result = episodePlaybackProgress(
            tmdbId = 123,
            episodes = listOf(watched),
            history = listOf(history(progress = 0.5f, position = 1_200, duration = 2_400)),
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun accidentalShortPlayIsIgnored() {
        val result = episodePlaybackProgress(
            tmdbId = 123,
            episodes = listOf(episode),
            history = listOf(history(progress = 0.01f, position = 20, duration = 2_400)),
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun watchedThresholdIsNotRenderedAsPartialProgress() {
        val result = episodePlaybackProgress(
            tmdbId = 123,
            episodes = listOf(episode),
            history = listOf(history(progress = 0.95f, position = 2_280, duration = 2_400)),
        )

        assertThat(result).isEmpty()
    }

    private fun history(progress: Float, position: Long, duration: Long) = WatchHistoryEntry(
        user_id = "user",
        media_type = "tv",
        show_tmdb_id = 123,
        season = 2,
        episode = 7,
        progress = progress,
        position_seconds = position,
        duration_seconds = duration,
        updated_at = "2026-09-13T10:00:00Z",
    )
}
