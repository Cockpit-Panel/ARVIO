package com.arflix.tv.ui.screens.tv.live

import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvProgram
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SportsCatalogueDiskCacheTest {
    @Test fun completeCatalogueSurvivesRecreationWithoutCrossingProfileOrPlaylistBoundaries() {
        val file = File.createTempFile("sports-cache-test", ".json", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            val key = SportsScheduleKey("profile-a", "provider-a", 1234L, setOf("hidden"), false, 1L)
            val channel = IptvChannel("provider-a:1", "Channel", "https://example.invalid/not-played", "Sports")
            val events = (1..1500).map {
                SportsGuideEvent("event:$it", "Event $it", GuideSport.FOOTBALL,
                    IptvProgram("Event $it", description = "Programme details. ".repeat(700), startUtcMillis = 1000, endUtcMillis = 2000), listOf(channel))
            }
            SportsCatalogueDiskCache(file).write(SportsScheduleSnapshot(key, events))
            // The uncompressed catalogue exceeds the old 12 MB read limit.
            assertTrue(file.length() < 12_000_000L)
            val recreated = SportsCatalogueDiskCache(file)
            assertEquals(events, recreated.read(key))
            assertEquals(events, recreated.read(key.copy(guideCoverage = 2, epgBackfill = true, window = 2)))
            assertTrue(recreated.read(key.copy(profileId = "profile-b")).isEmpty())
            assertTrue(recreated.read(key.copy(providerId = "provider-b")).isEmpty())
            assertTrue(recreated.read(key.copy(sourceVersion = 5678)).isEmpty())
            assertTrue(recreated.read(key.copy(excludedGroups = emptySet())).isEmpty())
            assertTrue(recreated.read(key, file.lastModified() + 120001).isEmpty())
        } finally { file.delete() }
    }
}
