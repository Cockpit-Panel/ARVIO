package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import com.arflix.tv.data.model.Episode
import java.time.LocalDate
import org.junit.Test

class EpisodeAvailabilityTest {
    private val today = LocalDate.of(2026, 9, 13)

    @Test
    fun seasonWatchNeverUnwatchesAnExistingEpisode() {
        listOf("", "invalid", "2026-10-01").forEach { date ->
            val episode = Episode(id = 1, episodeNumber = 1, seasonNumber = 1,
                name = "Episode", airDate = date, isWatched = true)
            assertThat(EpisodeAvailability.markWatchedIfAired(episode, today)).isEqualTo(episode)
        }
    }

    @Test
    fun seasonWatchOnlyAddsWatchedStatusForConfirmedAiredEpisodes() {
        val episode = Episode(id = 1, episodeNumber = 1, seasonNumber = 1,
            name = "Episode", airDate = "2026-09-13")
        assertThat(EpisodeAvailability.markWatchedIfAired(episode, today).isWatched).isTrue()
        assertThat(EpisodeAvailability.markWatchedIfAired(episode.copy(airDate = ""), today).isWatched).isFalse()
        assertThat(EpisodeAvailability.markWatchedIfAired(episode.copy(airDate = "2026-10-01"), today).isWatched).isFalse()
    }

    @Test
    fun airedEpisodeIncludesToday() {
        assertThat(EpisodeAvailability.hasAired("2026-09-13", today)).isTrue()
    }

    @Test
    fun futureEpisodeIsNotAired() {
        assertThat(EpisodeAvailability.hasAired("2026-09-14", today)).isFalse()
    }

    @Test
    fun missingDateFailsClosedForWatchActions() {
        assertThat(EpisodeAvailability.hasAired("", today)).isFalse()
    }
}
