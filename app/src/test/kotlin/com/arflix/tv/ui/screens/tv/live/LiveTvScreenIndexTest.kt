package com.arflix.tv.ui.screens.tv.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTvScreenIndexTest {

    @Test
    fun `global channel index is translated into guide window index`() {
        val index = resolveGuideAnchorIndex(
            selectedChannelId = "channel-729",
            guideChannelIndexById = emptyMap(),
            filteredChannelIndexById = mapOf("channel-729" to 729),
            guideWindowStart = 700,
            guideChannelCount = 48,
        )

        assertEquals(29, index)
    }

    @Test
    fun `channel outside guide window falls back safely`() {
        val index = resolveGuideAnchorIndex(
            selectedChannelId = "channel-729",
            guideChannelIndexById = emptyMap(),
            filteredChannelIndexById = mapOf("channel-729" to 729),
            guideWindowStart = 0,
            guideChannelCount = 48,
        )

        assertEquals(0, index)
    }

    @Test
    fun `local guide index takes precedence over global index`() {
        val index = resolveGuideAnchorIndex(
            selectedChannelId = "channel-729",
            guideChannelIndexById = mapOf("channel-729" to 12),
            filteredChannelIndexById = mapOf("channel-729" to 729),
            guideWindowStart = 700,
            guideChannelCount = 48,
        )

        assertEquals(12, index)
    }
}
