package com.arflix.tv.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class HomeHeroPreviewFormatTest {
    @Test fun addressesThatAlreadyMeantHlsStillDo() {
        assertTrue(homeHeroUsesHls("https://example.test/live/ch_12.m3u8", serverSaysHls = false))
        assertTrue(homeHeroUsesHls("https://example.test/hls/stream", serverSaysHls = false))
        assertTrue(homeHeroUsesHls("https://example.test/get.php?format=hls", serverSaysHls = false))
    }

    @Test fun anAddressWithoutAFileTypeFollowsTheServer() {
        val url = "https://example.test/api/play/channel_11"
        assertTrue(homeHeroUsesHls(url, serverSaysHls = true))
        assertFalse(homeHeroUsesHls(url, serverSaysHls = false))
    }

    @Test fun directStreamsAreNotTurnedIntoHls() {
        assertFalse(homeHeroUsesHls("https://example.test/live/user/pass/12345", serverSaysHls = false))
        assertFalse(homeHeroUsesHls("https://example.test/trailer.mp4", serverSaysHls = false))
    }
}
