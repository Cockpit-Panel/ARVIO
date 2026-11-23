package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.*
import kotlin.math.abs

/** Match whole events, never a league, a single team, or a similarly named youth fixture. */
internal fun attachSportsAddonSources(events: List<SportsGuideEvent>, sources: List<SportsAddonEvent>, now: Long,
    artwork: List<SportsEventArtwork> = emptyList()): List<SportsGuideEvent> {
    val output = events.map { it.copy(addonSources = emptyList()) }.toMutableList()
    val byIdentity = output.indices.groupBy { sportsEventIdentity(output[it].title) }.mapValues { it.value.toMutableList() }.toMutableMap()
    for (source in sources.groupBy { it.key }.values.map { it.reduce(::mergeSportsAddonEvent) }) {
        val sport = GuideSport.fromText(source.genres.joinToString(" ").replace('_', ' ')) ?: GuideSport.fromText(source.title) ?: GuideSport.OTHER
        val identity = sportsEventIdentity(source.title)
        val candidates = byIdentity[identity].orEmpty().filter { index ->
            val event = output[index]
            !event.channelOnly && event.fixture?.status !in listOf("finished", "postponed") &&
                (event.sport == sport || sport == GuideSport.OTHER || event.sport == GuideSport.OTHER) &&
                sportsQualifierKey(event.title) == sportsQualifierKey(source.title) &&
                (source.startsAt?.let { abs(event.programme.startUtcMillis - it) <= 2 * 3_600_000L }
                    ?: (source.isLive(now) && event.isOnAir(now)))
        }
        // Missing sport metadata is safe only for an unambiguous whole-event match.
        val ranked = candidates.sortedBy { source.startsAt?.let { start -> abs(output[it].programme.startUtcMillis - start) } ?: Long.MAX_VALUE }
        val match = ranked.singleOrNull() ?: ranked.firstOrNull()?.takeIf { first ->
            source.startsAt != null && ranked.map { output[it].sport }.distinct().size == 1 &&
                abs(output[first].programme.startUtcMillis - source.startsAt) < abs(output[ranked[1]].programme.startUtcMillis - source.startsAt)
        }
        if (match != null) {
            val event = output[match]
            output[match] = event.copy(addonSources = event.addonSources + source,
                artwork = event.artwork ?: source.artwork.takeIf { event.teamArtwork == null })
        } else if (source.isLive(now) || source.startsAt?.let { it > now } == true) {
            val start = source.startsAt ?: source.observedAt
            val event = SportsGuideEvent("addon:${source.key}", source.title, sport,
                IptvProgram(source.title, startUtcMillis = start, endUtcMillis = start), emptyList(),
                artwork = source.artwork, addonSources = listOf(source))
            byIdentity.getOrPut(identity) { mutableListOf() }.add(output.size)
            output.add(event)
        }
    }
    // Add-on-only events arrive after the guide's artwork pass. Enrich them too,
    // without letting an add-on banner cover the subscription's team badges.
    return attachSportsArtwork(output, artwork.filter { it.isScheduleMetadata }, preferMetadata = true)
}
