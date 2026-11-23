package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.api.StremioMetaPreview
import com.arflix.tv.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SportsAddonMatchingTest {
    private val now = Instant.parse("2026-09-21T18:00:00Z").toEpochMilli()
    private val catalog = AddonCatalog("sport", "sports_live", "Live Now")
    private val addon = Addon("sports", "Sports", "1", "Live sports", true, type = AddonType.COMMUNITY,
        url = "https://example.com/config/manifest.json?token=private", manifest = AddonManifest("sports", "Sports", "1",
            resources = listOf(AddonResource("catalog"), AddonResource("stream")), catalogs = listOf(catalog)))
    private val meta = StremioMetaPreview(id = "event:1", type = "sport", name = "North vs South", genres = listOf("Football"), releaseInfo = "LIVE")
    private val source get() = meta.toSportsAddonEvent(addon, catalog, now)!!
    private val programme = IptvProgram("North vs South", startUtcMillis = now - 60_000, endUtcMillis = now + 60_000)
    private val channel = IptvChannel("provider:1", "Sport One", "https://example.com/live.m3u8", "Sport")
    private val guide get() = SportsGuideEvent("guide", "North vs South", GuideSport.FOOTBALL, programme, listOf(channel))

    private val paidArt = SportsEventArtwork("North vs South", "https://example.com/official.jpg", listOf("Football"), now,
        homeBadge = "https://example.com/north.png", awayBadge = "https://example.com/south.png", source = "TheSportsDB")

    @Test fun subscriptionArtworkEnrichesAddonOnlyAndCombinedEvents() {
        for (events in listOf(emptyList(), listOf(guide))) {
            val result = attachSportsAddonSources(events, listOf(source.copy(artwork = "https://example.com/addon.jpg")), now, listOf(paidArt)).single()
            assertEquals(paidArt.background, result.artwork)
            assertEquals(source.eventId, result.addonSources.single().eventId)
        }
    }

    @Test fun subscriptionBadgesOutrankAddonAndSecondaryFeedBanners() {
        val badges = paidArt.copy(background = "")
        val addon = source.copy(artwork = "https://example.com/addon.jpg")
        val result = attachSportsAddonSources(emptyList(), listOf(addon), now, listOf(paidArt.copy(source = "ESPN"), badges)).single()
        assertNull(result.artwork)
        assertEquals(badges, result.teamArtwork)
        assertNull(attachSportsAddonSources(listOf(guide.copy(teamArtwork = badges)), listOf(addon), now).single().artwork)
    }

    @Test fun unrelatedOrMissingMetadataPreservesAddonFallback() {
        val addon = source.copy(artwork = "https://example.com/addon.jpg")
        for (art in listOf(emptyList(), listOf(paidArt.copy(startsAt = now + 86_400_000)),
            listOf(paidArt.copy(genres = listOf("Basketball"))), listOf(paidArt.copy(title = "North U21 vs South U21")),
            listOf(paidArt.copy(background = "", homeBadge = null, awayBadge = null)))) {
            val result = attachSportsAddonSources(emptyList(), listOf(addon), now, art).single()
            assertEquals(addon.artwork, result.artwork)
            assertNull(result.teamArtwork)
        }
    }

    @Test fun iptvAndMultipleAddonsShareOneEvent() {
        val result = attachSportsAddonSources(listOf(guide), listOf(source, source, source.copy(installation = "second", addonName = "Second")), now)
        assertEquals(1, result.size)
        assertEquals(listOf(channel), result.single().channels)
        assertEquals(2, result.single().addonSources.size)
    }
    @Test fun addonOnlyHasLiveAndUpcomingRowsWithoutPlaylist() {
        val upcoming = source.copy(eventId = "next", title = "East vs West", startsAt = now + 3_600_000, live = false)
        val events = attachSportsAddonSources(emptyList(), listOf(source, upcoming), now)
        assertEquals(2, events.size)
        assertTrue(events.first().isOnAir(now))
        assertFalse(events.last().isOnAir(now))
        assertTrue(sportsPresentationRows(events, now, emptySet()).isNotEmpty())
    }
    @Test fun doesNotMatchWrongDaySportOrYouthTeam() {
        for (candidate in listOf(source.copy(startsAt = now + 86_400_000, live = false), source.copy(genres = listOf("Basketball")), source.copy(title = "North U21 vs South U21"))) {
            assertTrue(attachSportsAddonSources(listOf(guide), listOf(candidate), now).first().addonSources.isEmpty())
        }
    }
    @Test fun undatedLiveSourceDoesNotMatchTomorrow() {
        val future = guide.copy(programme = programme.copy(startUtcMillis = now + 86_400_000, endUtcMillis = now + 90_000_000), schedules = emptyMap())
        assertTrue(attachSportsAddonSources(listOf(future), listOf(source), now).first().addonSources.isEmpty())
    }
    @Test fun expiredLiveSignalDoesNotInventLiveEvent() {
        assertTrue(attachSportsAddonSources(emptyList(), listOf(source), now + 300_001).isEmpty())
    }
    @Test fun freshLiveResultDoesNotWaitForTheGuideClock() {
        val events = attachSportsAddonSources(emptyList(), listOf(source.copy(observedAt = now + 1000)), now)
        assertTrue(events.single().isOnAir(now))
    }
    @Test fun sportsDestinationExistsWithoutAnyPlaylistCategories() {
        val section = LiveSection("empty", "", emptyList())
        val tree = LiveCategoryTree(emptyList(), section, section, section).withSportsDestination()
        assertEquals(listOf(SPORTS_GUIDE_CATEGORY), tree.top.map { it.id })
        assertEquals(tree.top, tree.withSportsDestination().top)
    }
    @Test fun datesAreUtcAndConfiguredUrlsArePreserved() {
        val next = meta.copy(releaseInfo = "21 Sep 2026 · 22:35 UTC").toSportsAddonEvent(addon, catalog, now)!!
        assertEquals(Instant.parse("2026-09-21T22:35:00Z").toEpochMilli(), next.startsAt)
        assertEquals("https://example.com/config/stream/sport/event:1.json?token=private", sportsAddonUrl(addon, "stream", "sport", "event:1"))
        assertFalse(source.installation.contains("private"))
    }
    @Test fun excludesReplaysRecordingsDisabledAndRequiredSearchCatalogs() {
        assertNull(meta.copy(id = "leaf:channel").toSportsAddonEvent(addon, catalog, now))
        assertNull(meta.copy(releaseInfo = "Replay").toSportsAddonEvent(addon, catalog, now))
        assertTrue(sportsEventCatalogs(addon.copy(isEnabled = false)).isEmpty())
        val catalogs = listOf(catalog, catalog.copy(id = "sports_replays"), catalog.copy(extra = listOf(AddonCatalogExtra("search", true))))
        assertEquals(1, sportsEventCatalogs(addon.copy(manifest = addon.manifest!!.copy(catalogs = catalogs))).size)
    }
    @Test fun missingSportMatchesOnlyUnambiguousEvents() {
        val missing = source.copy(genres = emptyList())
        assertEquals(1, attachSportsAddonSources(listOf(guide), listOf(missing), now).size)
        val ambiguous = attachSportsAddonSources(listOf(guide, guide.copy(id = "basketball", sport = GuideSport.BASKETBALL)), listOf(missing), now)
        assertTrue(ambiguous.take(2).all { it.addonSources.isEmpty() })
    }
    @Test fun equalTimeCandidatesAreNotArbitrarilyMatched() {
        val result = attachSportsAddonSources(listOf(guide, guide.copy(id = "second")), listOf(source.copy(startsAt = now)), now)
        assertTrue(result.take(2).all { it.addonSources.isEmpty() })
    }
    @Test fun overlappingCatalogsPreserveLiveEvidenceAndMetadata() {
        val sparse = source.copy(live = false, observedAt = now + 10_000, startsAt = now - 60_000, genres = emptyList())
        val merged = mergeSportsAddonEvent(source.copy(artwork = "https://example.com/art.jpg"), sparse)
        assertTrue(merged.live)
        assertEquals(now, merged.observedAt)
        assertEquals(source.genres, merged.genres)
        assertEquals("https://example.com/art.jpg", merged.artwork)
        assertEquals(sparse.startsAt, merged.startsAt)
        assertFalse(merged.isLive(now + 300_001))
        assertEquals(1, attachSportsAddonSources(emptyList(), listOf(source, sparse), now).single().addonSources.size)
    }
    @Test fun playbackSourcesExcludePromotionAndPreferDirectVideo() {
        val external = com.arflix.tv.data.repository.SportsAddonStream("Watch on provider", "https://example.com/watch", emptyMap(), true)
        val direct = external.copy(name = "Channel HD", url = "https://example.com/live.m3u8", external = false)
        val promo = external.copy(name = "Support the project!", url = "https://example.com/support")
        assertEquals(listOf(direct, external), com.arflix.tv.data.repository.playbackSportsSources(listOf(promo, external, direct, direct)))
    }
    @Test fun penguEmojiLiveStatusAndFinishedDescriptionAreRespected() {
        val pengu = meta.copy(id = "pp-live:provider~123", type = "tv", name = "\uD83D\uDD34 LIVE: North vs South",
            releaseInfo = "2026-09-21 19:00 CEST", released = "2026-09-21T17:00:00Z", description = "Soccer\n\uD83D\uDD34 Live now")
        assertTrue(pengu.toSportsAddonEvent(addon, catalog, now)!!.isLive(now))
        assertTrue(pengu.copy(name = "North vs South").toSportsAddonEvent(addon, catalog, now)!!.isLive(now))
        assertNull(pengu.copy(description = "Finished - a replay may still be available").toSportsAddonEvent(addon, catalog, now))
    }
}
