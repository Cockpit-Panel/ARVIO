package com.arflix.tv.util

import com.arflix.tv.data.model.Episode
import java.time.LocalDate

/** Shared release-date rule for episode actions and Continue Watching eligibility. */
internal object EpisodeAvailability {
    fun markWatchedIfAired(episode: Episode, today: LocalDate = LocalDate.now()): Episode =
        if (hasAired(episode.airDate, today)) episode.copy(isWatched = true) else episode

    fun hasAired(
        rawAirDate: String?,
        today: LocalDate = LocalDate.now(),
        unknownIsAired: Boolean = false,
    ): Boolean {
        val value = rawAirDate?.trim().orEmpty()
        if (value.isEmpty()) return unknownIsAired
        return runCatching { !LocalDate.parse(value).isAfter(today) }
            .getOrDefault(unknownIsAired)
    }
}
