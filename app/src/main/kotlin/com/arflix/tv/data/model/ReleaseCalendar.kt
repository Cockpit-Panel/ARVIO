package com.arflix.tv.data.model

import java.time.Instant
import java.time.LocalDate

enum class ReleaseCalendarSource(val id: String, val label: String) {
    ALL("all", "All watchlists"),
    ARVIO("arvio", "ARVIO watchlist"),
    TRAKT("trakt", "Trakt"),
    SIMKL("simkl", "SIMKL"),
    MDBLIST("mdblist", "MDBList")
}

enum class CalendarReleaseKind(val label: String) {
    EPISODE("Episode"), CINEMA("Cinema"), DIGITAL("Digital"), PHYSICAL("Physical"), TV("TV release"), MOVIE("Release")
}

val calendarReleaseOrder: Comparator<CalendarRelease> = compareBy<CalendarRelease> { it.date }
    .thenBy { it.releaseInstant ?: Instant.MAX }.thenBy { it.media.title.lowercase(java.util.Locale.ROOT) }
    .thenBy { it.seasonNumber ?: 0 }.thenBy { it.episodeNumber ?: 0 }
    .thenBy { if (it.kind == CalendarReleaseKind.MOVIE) "release" else it.kind.name.lowercase(java.util.Locale.ROOT) }

fun calendarPreviewTitles(releases: List<CalendarRelease>): List<CalendarRelease> =
    releases.distinctBy { it.media.mediaType to it.media.id }

/** Date-only metadata never becomes a synthetic midnight timestamp. */
data class CalendarRelease(
    val id: String,
    val media: MediaItem,
    val date: LocalDate,
    val releaseInstant: Instant? = null,
    val kind: CalendarReleaseKind,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val sourceIds: Set<String> = emptySet(),
    val logoUrl: String? = null,
    val region: String? = null
)
