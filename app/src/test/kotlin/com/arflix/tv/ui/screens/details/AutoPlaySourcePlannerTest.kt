package com.arflix.tv.ui.screens.details

import com.arflix.tv.data.model.StreamBehaviorHints
import com.arflix.tv.data.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlaySourcePlannerTest {
    @Test
    fun `best autoplay prefers 4k over small 720p even when sizeBytes hint is wrong`() {
        val small720 = stream(
            source = "Movie 720p WEB-DL",
            quality = "720p",
            size = "780 MB",
            sizeBytes = 100L * 1024L * 1024L * 1024L
        )
        val large4k = stream(
            source = "Movie 2160p 4K WEB-DL",
            quality = "Unknown",
            size = "18.4 GB",
            sizeBytes = 1L
        )

        val selected = bestAutoPlayStream(listOf(small720, large4k), minQualityScore = 0)

        assertEquals(large4k, selected)
    }

    @Test
    fun `best autoplay uses visible size string between same quality sources`() {
        val smaller4k = stream(source = "Movie 2160p", quality = "4K", size = "8 GB")
        val bigger4k = stream(source = "Movie 2160p REMUX", quality = "4K", size = "52 GB")

        val selected = bestAutoPlayStream(listOf(smaller4k, bigger4k), minQualityScore = 0)

        assertEquals(bigger4k, selected)
    }

    @Test
    fun `autoplay does not wait for addon completion when strong source is ready`() {
        val strong = stream(source = "Movie 2160p REMUX", quality = "4K", size = "52 GB")

        assertTrue(
            shouldWaitForAutoPlaySources(
                isLoadingStreams = false,
                selectedStream = strong,
                elapsedMs = AUTOPLAY_STRONG_SOURCE_SETTLE_MS - 1
            )
        )
        assertFalse(
            shouldWaitForAutoPlaySources(
                isLoadingStreams = false,
                selectedStream = strong,
                elapsedMs = AUTOPLAY_STRONG_SOURCE_SETTLE_MS
            )
        )
    }

    @Test
    fun `autoplay gives weak source only a short settle window`() {
        val weak = stream(source = "Movie 720p WEB-DL", quality = "720p", size = "780 MB")

        assertTrue(
            shouldWaitForAutoPlaySources(
                isLoadingStreams = false,
                selectedStream = weak,
                elapsedMs = AUTOPLAY_WEAK_SOURCE_SETTLE_MS - 1
            )
        )
        assertFalse(
            shouldWaitForAutoPlaySources(
                isLoadingStreams = false,
                selectedStream = weak,
                elapsedMs = AUTOPLAY_WEAK_SOURCE_SETTLE_MS
            )
        )
    }

    private fun stream(
        source: String,
        quality: String,
        size: String,
        sizeBytes: Long? = null,
        cached: Boolean = true
    ) = StreamSource(
        source = source,
        addonName = "Torrentio",
        addonId = "torrentio",
        quality = quality,
        size = size,
        sizeBytes = sizeBytes,
        url = "https://example.com/${source.hashCode()}",
        behaviorHints = StreamBehaviorHints(cached = cached)
    )
}
