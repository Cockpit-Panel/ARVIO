package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SportsCatalogueTest {
    @Test fun supplementalFixturesAndLegalClubNamesMatchWithoutUiChanges() {
        val body = """{"version":1,"catalogueEnabled":true,"events":[{"id":"espn:epl:123","source":"ESPN","title":"Manchester United FC vs Club Atletico Madrid","homeTeam":"Manchester United FC","awayTeam":"Club Atletico Madrid","sport":"Soccer","startsAt":$now}]}"""
        val metadata = parseSportsMetadata(body).single()
        assertEquals("espn:epl:123", metadata.fixture!!.id)
        assertEquals("ESPN", metadata.source)
        assertTrue(metadata.isScheduleMetadata)
        val result = buildSportsCatalogue(listOf(epg.copy(title = "Live: Man Utd vs Atl Madrid", competition = null)), listOf(metadata), listOf(channel), now)
        assertEquals(1, result.size)
        assertEquals(1, result.single().channels.size)
    }
    @Test fun scheduledEventsDoNotDisappearWhenTheLiveStatusFeedIsLate() {
        val started = art.copy(startsAt = now - 60_000, fixture = fixture.copy(status = "scheduled"))
        val event = buildSportsCatalogue(emptyList(), listOf(started), listOf(channel), now).single()
        assertFalse(event.isOnAir(now))
        assertTrue(event.isScheduledNow(now))
        assertTrue(sportsGuideRows(listOf(event), now).any { it.id == "scheduled-now" })
        assertFalse(event.isScheduledNow(now + 4 * 3600_000L))
        assertFalse(event.copy(fixture = fixture.copy(status = "finished")).isScheduledNow(now))
    }
    @Test fun fastChannelNormalizationKeepsUnicodeAndEventPrefixSemantics() {
        assertEquals("nl espn 2", sportsChannelKey("NL| ESPN2 FHD"))
        assertEquals("fr equipe", sportsChannelKey("FR| Équipe HD"))
        assertEquals("uk tnt sports 2", sportsChannelKey("UK-NOWTV| TNT SPORT 2 FHD"))
        assertEquals("a vs b", sportsChannelKey("Live: Football: A versus B"))
        assertEquals("us paramount plus", sportsChannelKey("US| Paramount+ HD"))
    }
    @Test fun sportsPrefilterPreservesAllSportTermsAndMixedPriority() {
        for (sport in GuideSport.entries.filter { it != GuideSport.OTHER }) {
            assertNotNull(sport.title, GuideSport.fromText("Provider | ${sport.title} HD"))
        }
        assertEquals(GuideSport.AMERICAN_FOOTBALL, GuideSport.fromText("Football / NFL"))
        assertEquals(GuideSport.AUSTRALIAN_FOOTBALL, GuideSport.fromText("Australian football"))
        assertNull(GuideSport.fromText("NL | General entertainment 4K"))
    }
    @Test fun broadcasterCountriesOutsideTheOldRegionalTableMatchPrecisely() {
        assertTrue(sportsChannelKey("RS| Arena Adrenalin HD") in sportsBroadcasterKeys("Arena Adrenalin RS", "Serbia"))
        assertTrue(sportsChannelKey("HR| Arena Sport 2 FHD") in sportsBroadcasterKeys("Arena Sport 2", "Croatia"))
        assertFalse(sportsChannelKey("RS| Arena Sport 2") in sportsBroadcasterKeys("Arena Sport 2", "Croatia"))
        assertFalse(sportsChannelKey("HR| Arena Sport 3") in sportsBroadcasterKeys("Arena Sport 2", "Croatia"))
    }
    @Test fun liveSportsChannelsAreNotSilentlyTruncatedAtTwelve() {
        val events = (1..40).map { epg.copy(id = "live-channel:$it", channelOnly = true) }
        val rows = sportsGuideRows(events, now)
        assertEquals(40, rows.single { it.id == "live-channels" }.events.size)
    }
    @Test fun fightingFeedUsesBoxingLeagueAndKeepsEventPosterAndBroadcasters() {
        val boxing = art.copy(title = "Ryan Garcia vs Conor Benn", genres = listOf("Fighting"),
            background = "https://r2.thesportsdb.com/images/media/event/thumb/fight.jpg",
            fixture = fixture.copy(league = "Boxing", broadcasters = listOf(
                SportsBroadcaster("DAZN UK", "United Kingdom", art.startsAt!!))))
        val station = channel.copy(name = "UK | DAZN FHD")
        val result = buildSportsCatalogue(emptyList(), listOf(boxing), listOf(station), now).single()
        assertEquals(GuideSport.BOXING, result.sport)
        assertEquals(listOf(station), result.possibleChannels)
        assertTrue(result.hasEventArtwork)
        assertEquals(2, sportsPresentationRows(listOf(result), now, emptySet()).size)
        assertFalse(sportsChannelKey("DE | DAZN") in sportsBroadcasterKeys("DAZN UK", "United Kingdom"))
        assertFalse(sportsChannelKey("US | Paramount HD") in sportsBroadcasterKeys("Paramount+ US", "United States"))
        assertTrue(sportsChannelKey("US | Paramount Plus HD") in sportsBroadcasterKeys("Paramount+ US", "United States"))
    }
    @Test fun largeBroadcastFeedKeepsEveryFixtureAndProviderVariant() {
        val variants = (1..40).map { channel.copy(id = "provider:$it") }
        val repeated = List(200) { fixture.broadcasters.single() }
        val feed = (1..500).map { art.copy(title = "Fixture $it", fixture = fixture.copy(id = "$it", broadcasters = repeated)) }
        val started = System.nanoTime()
        val catalogue = buildSportsCatalogue(emptyList(), feed, variants, now)
        println("Sports catalogue: 500 fixtures, 100000 broadcast listings, 40 channel variants: ${(System.nanoTime() - started) / 1_000_000}ms")
        assertEquals(500, catalogue.size)
        assertTrue(catalogue.all { it.possibleChannels.size == 40 })
        assertEquals(500, sportsGuideRows(catalogue, now).single { it.id == "FOOTBALL" }.events.size)
    }
    @Test fun expandedRegionsAndQualityVariantsPreserveStationIdentity() {
        val keys = sportsBroadcasterKeys("beIN Sports 2", "Turkey")
        for (name in listOf("TR| beINSPORTS2 FHD", "TR| beIN Sport 2 1080p 50FPS BACKUP")) assertTrue(name, sportsChannelKey(name) in keys)
        for (name in listOf("FR| beIN Sports 2", "TR| beIN Sports 3", "TR| beIN Sports 2 +1")) assertFalse(name, sportsChannelKey(name) in keys)
    }
    @Test fun shortAndKnownTeamAliasesMatchWithoutGuessingOtherTeams() {
        val item = art.copy(title = "Manchester United vs PSV", homeTeam = "Manchester United", awayTeam = "PSV", startsAt = now)
        val result = buildSportsCatalogue(listOf(epg.copy(title = "Football: Man Utd - PSV, Premier League")), listOf(item), emptyList(), now)
        assertEquals(listOf(channel), result.single().channels)
        assertTrue(buildSportsCatalogue(listOf(epg.copy(title = "Man City - PSV")), listOf(item), emptyList(), now).single { it.fixture != null }.channels.isEmpty())
    }
    @Test fun previouslyExcludedSportsAreClassified() {
        for ((name, sport) in mapOf("Rugby" to GuideSport.RUGBY, "Golf" to GuideSport.GOLF, "Motorsport" to GuideSport.MOTORSPORT,
            "Formula 1" to GuideSport.F1, "Australian Football" to GuideSport.AUSTRALIAN_FOOTBALL, "Snooker" to GuideSport.SNOOKER))
            assertEquals(sport, GuideSport.fromText(name))
    }
    @Test fun missingArtworkDoesNotRemoveMatchedEvents() {
        val event = buildSportsCatalogue(emptyList(), listOf(art), listOf(channel), now).single()
        assertEquals("FOOTBALL-schedule", sportsPresentationRows(listOf(event), now, emptySet()).single().id)
        assertEquals("FOOTBALL-schedule", sportsPresentationRows(listOf(event.copy(artwork = "https://example.com/event.jpg")), now, setOf(event.id)).single().id)
        assertTrue(sportsPresentationRows(listOf(event.copy(possibleChannels = emptyList())), now, emptySet()).isEmpty())
    }
    @Test fun broadcasterDecorationsPreserveCountryAndChannelNumber() {
        assertEquals(sportsChannelKey("UK TNT Sports 2"), sportsChannelKey("UK-NOWTV| TNT SPORT 2 FHD"))
        assertNotEquals(sportsChannelKey("DE TNT Sports 2"), sportsChannelKey("UK-NOWTV| TNT SPORT 2 FHD"))
        assertNotEquals(sportsChannelKey("UK TNT Sports 1"), sportsChannelKey("UK-NOWTV| TNT SPORT 2 FHD"))
    }
    @Test fun broadcasterCountrySuffixMatchesOnlyThatRegionAndChannel() {
        val keys = sportsBroadcasterKeys("ESPN 3 Netherlands", "Netherlands")
        assertTrue(sportsChannelKey("NL | ESPN 3 UHD 8K") in keys)
        assertFalse(sportsChannelKey("US | ESPN 3 HD") in keys)
        assertFalse(sportsChannelKey("NL | ESPN 2 HD") in keys)
        assertFalse(sportsChannelKey("NL | ESPN 3 HD") in sportsBroadcasterKeys("ESPN 3 France", "Netherlands"))
    }
    @Test fun broadcasterCountryAliasesMatchProviderLabels() {
        val keys = sportsBroadcasterKeys("ESPN 3 Netherlands", "The Netherlands")
        assertTrue(sportsChannelKey("NL | ESPN 3 UHD 8K") in keys)
        assertFalse(sportsChannelKey("BE | ESPN 3 HD") in keys)
    }
    @Test fun decoratedMatchTitlesUseRealCrestsWithoutStockArtwork() {
        val item = art.copy(homeTeam = "North", awayTeam = "South", homeBadge = "https://example.com/north.png", awayBadge = "https://example.com/south.png", startsAt = now)
        val decorated = epg.copy(title = "Football: North - South, Premier League 2026/2027")
        val result = buildSportsCatalogue(listOf(decorated), listOf(item), emptyList(), now).single()
        assertEquals(listOf(channel), result.channels)
        assertTrue(result.hasEventArtwork)
        assertFalse(epg.hasEventArtwork)
        val women = decorated.copy(title = "Football: North - South, Women")
        assertTrue(buildSportsCatalogue(listOf(women), listOf(item), emptyList(), now).single { it.fixture != null }.channels.isEmpty())
    }
    private val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
    private val channel = IptvChannel("p:1", "UK | Sky Sports Main Event FHD", streamUrl = "https://example.invalid/live", group = "Sports")
    private val fixture = SportsFixture("42", "English Premier League", null, null, null, "scheduled", now, null, null,
        listOf(SportsBroadcaster("Sky Sports Main Event HD", "United Kingdom", now + 3600000)))
    private val art = SportsEventArtwork("North vs South", "", listOf("Soccer"), now + 3600000, source = "TheSportsDB", fixture = fixture)
    private val p = IptvProgram("North vs South", startUtcMillis = now - 60000, endUtcMillis = now + 3600000)
    private val epg = SportsGuideEvent("guide", p.title, GuideSport.FOOTBALL, p, listOf(channel), competition = "Premier League")

    @Test fun fixtureWithoutArtworkOrChannelsIsBrowsableButNeverInventsLiveStatus() {
        val event = buildSportsCatalogue(emptyList(), listOf(art), emptyList(), now).single()
        assertEquals("sportsdb:42", event.id)
        assertTrue(event.channels.isEmpty())
        assertFalse(event.isOnAir(now + 7200000))
        assertEquals(listOf("upcoming", "FOOTBALL"), sportsGuideRows(listOf(event), now).map { it.id })
    }
    @Test fun possibleBroadcastsAreSeparateAndHiddenSourcesDoNotLeak() {
        val wrong = channel.copy(id = "wrong", name = "DE | Sky Sports Main Event HD")
        val hint = buildSportsCatalogue(emptyList(), listOf(art), listOf(channel, wrong), now).single()
        assertTrue(hint.channels.isEmpty())
        assertEquals(listOf(channel.id), hint.possibleChannels.map { it.id })
        assertTrue(buildSportsCatalogue(emptyList(), listOf(art), emptyList(), now).single().possibleChannels.isEmpty())
        val actual = buildSportsCatalogue(listOf(epg), listOf(art.copy(startsAt = now)), listOf(channel), now).single()
        assertEquals(listOf(channel), actual.availableChannels(now))
        assertEquals(p.endUtcMillis, actual.schedules[channel.id]!!.endUtcMillis)
    }
    @Test fun qualifiersOtherLeaguesAndOtherTimesRemainSeparate() {
        for (changed in listOf(art.copy(fixture = fixture.copy(qualifier = "women")), art.copy(genres = listOf("Basketball")),
            art.copy(startsAt = now + 10800000), art.copy(fixture = fixture.copy(league = "UEFA Champions League")))) {
            val result = buildSportsCatalogue(listOf(epg), listOf(changed), emptyList(), now)
            assertTrue(result.single { it.fixture != null }.channels.isEmpty())
            assertEquals(1, result.count { it.fixture == null })
        }
    }
    @Test fun liveExpiresAndFinishedEventsSuppressOldGuide() {
        val live = art.copy(startsAt = now - 60000, fixture = fixture.copy(status = "live"))
        val event = buildSportsCatalogue(emptyList(), listOf(live), emptyList(), now).single()
        assertTrue(event.isConfirmedLive(now))
        assertTrue("A normal score refresh gap must not hide a live fixture", event.isOnAir(now + 5 * 60_000L))
        assertFalse(event.isOnAir(now + 15 * 60_000L + 1))
        assertTrue(buildSportsCatalogue(listOf(epg), listOf(art.copy(startsAt = now, fixture = fixture.copy(status = "finished"))), emptyList(), now).isEmpty())
    }
    @Test fun featuredHighlightsRankByProminenceAndSportRowsKeepUpcomingChronological() {
        val event = buildSportsCatalogue(emptyList(), listOf(art.copy(startsAt = now, fixture = fixture.copy(status = "live"))), emptyList(), now).single()
        val minor = event.copy(id = "minor", prominence = 0)
        assertEquals(event.id, sportsGuideRows(listOf(minor, event), now).first().events.first().id)
        val later = event.copy(id = "later", fixture = fixture, programme = p.copy(startUtcMillis = now + 7200000), schedules = emptyMap())
        val earlier = later.copy(id = "earlier", programme = p.copy(startUtcMillis = now + 3600000), prominence = 0)
        val rows = sportsGuideRows(listOf(later, earlier), now)
        assertEquals("later", rows.first().events.first().id)
        assertEquals("earlier", rows.last().events.first().id)
        assertFalse(rows.any { it.id == "more" })
    }
}
