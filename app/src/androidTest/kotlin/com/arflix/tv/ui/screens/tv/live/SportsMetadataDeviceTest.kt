package com.arflix.tv.ui.screens.tv.live

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.data.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URL

/** Opt-in real metadata/artwork check. Fixture channel, no provider stream requests. */
class SportsMetadataDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun capturedProviderListMatchesAndDisplaysBoxingPoster() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "sports-audit")
        val channelsFile = File(folder, "sports-provider-channels.json")
        val metadataFile = File(folder, "sports-metadata-current.json")
        assumeTrue("Opt-in captured public metadata and channel labels", channelsFile.exists() && metadataFile.exists())
        val channels = com.google.gson.JsonParser.parseString(channelsFile.readText().removePrefix("\uFEFF")).asJsonArray.map { raw ->
            val item = raw.asJsonObject
            IptvChannel("audit:${item.get("stream_id").asString}", item.get("name").asString,
                "https://example.invalid/not-played", "Sports")
        }
        val metadata = parseSportsMetadata(metadataFile.readText())
        val now = System.currentTimeMillis()
        val legacyQuality = Regex("\\b(uhd|fhd|hd|sd|4k|8k|hevc|h[.]?265|h[.]?264|1080p|720p|2160p|(?:25|30|50|60)fps|raw|backup)\\b", RegexOption.IGNORE_CASE)
        val legacyPackage = Regex("^([a-z]{2,3})\\s+nowtv\\s+")
        val legacyTnt = Regex("\\btnt sport\\b")
        val legacyBein = Regex("\\bbein\\s*sports?\\s*(\\d*)")
        val legacyNumber = Regex("\\b(sports|espn)(\\d+)\\b")
        val legacySpaces = Regex("\\s+")
        val originalKeys = channels.map { sportsArtworkKey(it.name.replace(legacyQuality, "").replace("+", " plus "))
            .replace(legacyPackage, "$1 ").replace(legacyTnt, "tnt sports").replace(legacyBein, "bein sports $1")
            .replace(legacyNumber, "$1 $2").replace(legacySpaces, " ").trim() }
        val keyStart = android.os.SystemClock.elapsedRealtime()
        val optimizedKeys = channels.map { sportsChannelKey(it.name) }
        println("Sports channel-key audit: channels=${channels.size} elapsedMs=${android.os.SystemClock.elapsedRealtime() - keyStart}")
        assertEquals("Every captured provider label must normalize identically", originalKeys, optimizedKeys)
        val started = android.os.SystemClock.elapsedRealtime()
        val result = buildSportsCatalogue(emptyList(), metadata, channels, now)
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        val fight = result.single { it.title == "Ryan Garcia vs Conor Benn" }
        assertEquals(GuideSport.BOXING, fight.sport)
        assertTrue(fight.hasEventArtwork)
        assertTrue(fight.possibleChannels.isNotEmpty())
        assertFalse(fight.possibleChannels.any { it.name == "US| PARAMOUNT HD" })
        println("Sports real-list audit: channels=${channels.size} metadata=${metadata.size} matched=${result.count { it.hasChannels(now) }} matchingMs=$elapsed fightChannels=${fight.possibleChannels.size}")
        compose.setContent { SportsGuidePane(result, now, false, 0, {}, {}, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("sports-guide-list").fetchSemanticsNodes().isNotEmpty() }
        val rows = sportsPresentationRows(result, now, emptySet())
        val boxingRow = rows.indexOfFirst { it.id == "BOXING" }
        assertTrue(boxingRow >= 0)
        compose.onNodeWithTag("sports-guide-list").performScrollToIndex(boxingRow)
        compose.onNodeWithText(fight.title).assertIsDisplayed()
        compose.waitUntil(20000) { compose.onAllNodesWithTag("sports-artwork-loaded", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        capture("sports-real-provider-boxing.png")
    }

    @Test fun liveMetadataRendersInActualSportsPane() {
        val endpoint = InstrumentationRegistry.getArguments().getString("sportsMetadataUrl")
        assumeTrue("Requires a deployed metadata endpoint", endpoint?.startsWith("https://") == true)
        val connection = URL(endpoint).openConnection().apply { connectTimeout = 10_000; readTimeout = 20_000 }
        val metadata = parseSportsMetadata(connection.getInputStream().bufferedReader().use { it.readText() })
        val now = System.currentTimeMillis()
        val relevant = metadata.filter { it.startsAt!! > now - 7_200_000 && it.background.isNotBlank() && GuideSport.fromText(it.genres.joinToString(" ")) != null }.take(24)
        assertTrue("Real feed must have relevant event artwork", relevant.size >= 4)
        val events = relevant.mapIndexed { index, item -> SportsGuideEvent("metadata:$index", item.title,
            GuideSport.fromText(item.genres.joinToString(" "))!!,
            IptvProgram(item.title, startUtcMillis = item.startsAt!!, endUtcMillis = item.startsAt + 7_200_000),
            listOf(IptvChannel("fixture:$index", "Artwork test channel", "https://example.invalid/not-played", "Sports"))) }
        val decorated = buildSportsCatalogue(events, relevant, emptyList(), now)
        compose.setContent { SportsGuidePane(decorated, now, false, 0, {}, {}, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("sports-artwork-loaded", useUnmergedTree = true).fetchSemanticsNodes().size >= 3 }
        compose.onNodeWithText("TheSportsDB").assertDoesNotExist()
        compose.onNodeWithText("Available on my channels").assertDoesNotExist()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "tv-overhaul").apply { mkdirs() }
        File(folder, "sportsdb-live-artwork-fixture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun fixtureCatalogueDoesNotDisplayUnmatchedEvents() {
        val endpoint = InstrumentationRegistry.getArguments().getString("sportsMetadataUrl")
        assumeTrue(endpoint?.startsWith("https://") == true)
        val connection = URL(endpoint).openConnection().apply { connectTimeout = 10_000; readTimeout = 20_000 }
        val metadata = parseSportsMetadata(connection.getInputStream().bufferedReader().use { it.readText() })
        val now = System.currentTimeMillis()
        val events = buildSportsCatalogue(emptyList(), metadata, emptyList(), now)
        assertTrue(events.any { it.fixture != null })
        assertTrue(sportsGuideRows(events, now).isNotEmpty())
        compose.setContent { SportsGuidePane(events, now, false, 0, {}, {}, {}, Modifier.fillMaxSize()) }
        compose.onNodeWithText("No sports events matched to your channels").assertIsDisplayed()
        compose.onAllNodesWithTag("sports-event-card").assertCountEquals(0)
        compose.onNodeWithText("Available on my channels").assertDoesNotExist()
        capture("sports-catalogue-unmatched-hidden.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "tv-overhaul").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
