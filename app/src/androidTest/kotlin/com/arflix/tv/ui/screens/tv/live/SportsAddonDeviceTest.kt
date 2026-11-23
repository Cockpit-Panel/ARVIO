package com.arflix.tv.ui.screens.tv.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arflix.tv.data.model.*
import com.arflix.tv.data.repository.SportsAddonStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SportsAddonDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addonOnlyPickerResolvesOnDemandAndSelectsStream() = exercise(false)
    @Test fun combinedPickerKeepsIptvAndAddonSources() = exercise(true)

    private fun exercise(withIptv: Boolean) {
        val now = System.currentTimeMillis()
        val source = SportsAddonEvent("installed", "test.sports", "Test Sports Add-on", "sport", "event:1", "North vs South",
            listOf("Football"), now - 60_000, true, now, null)
        val channel = IptvChannel("test:1", "IPTV Sports HD", "https://example.invalid/live.m3u8", "Sports")
        val programme = IptvProgram(source.title, startUtcMillis = now - 60_000, endUtcMillis = now + 3_600_000)
        val events = attachSportsAddonSources(if (withIptv) listOf(SportsGuideEvent("test", source.title, GuideSport.FOOTBALL, programme, listOf(channel))) else emptyList(), listOf(source), now)
        var resolves = 0
        var played: SportsAddonStream? = null
        compose.setContent {
            SportsGuidePane(events, now, false, 0, {}, {}, {}, Modifier.fillMaxSize(), resolveAddon = {
                resolves++
                listOf(SportsAddonStream("Add-on stream HD", "https://example.invalid/test.m3u8", mapOf("Referer" to "https://example.invalid"), false))
            }, onPlayAddon = { _, stream -> played = stream })
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("sports-event-card").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(0, resolves) }
        compose.onAllNodesWithTag("sports-event-card")[0].performClick()
        compose.onNodeWithText("Test Sports Add-on").assertIsDisplayed()
        if (withIptv) compose.onNodeWithText("IPTV Sports HD").assertIsDisplayed()
        compose.onNodeWithText("Test Sports Add-on").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add-on stream HD").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add-on stream HD").performClick()
        compose.runOnIdle {
            assertEquals(1, resolves)
            assertEquals("https://example.invalid", played?.headers?.get("Referer"))
        }
    }
}
