package com.arflix.tv.ui.screens.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTvProviderRegressionTest {
    @Test fun providerLogoAlwaysWins() {
        assertEquals("provider", selectChannelLogo("provider", listOf("fallback"), true, emptySet()) { true })
    }

    @Test fun disabledFallbackNeverReplacesMissingOrFailedProvider() {
        assertNull(selectChannelLogo(null, listOf("fallback"), false, emptySet()) { false })
        assertNull(selectChannelLogo("provider", listOf("fallback"), false, setOf("provider")) { false })
    }

    @Test fun enabledFallbackOnlyAfterProviderFailure() {
        assertEquals("fallback", selectChannelLogo("provider", listOf("bad", "fallback"), true, setOf("provider")) { it == "bad" })
    }

    @Test fun seekableCatchupKeepsPreciseSeeking() {
        assertEquals(35_000L, catchupSeekTarget(25_000L, 10_000L, 600_000L, true, 60_000L))
    }

    @Test fun unseekableCatchupAdvancesToNextSupportedTimestamp() {
        assertEquals(60_000L, catchupSeekTarget(25_000L, 10_000L, 600_000L, false, 60_000L))
        assertEquals(120_000L, catchupSeekTarget(65_000L, 10_000L, 600_000L, false, 60_000L))
        assertEquals(35_000L, catchupSeekTarget(25_000L, 10_000L, 600_000L, false, 1_000L))
    }

    @Test fun unseekableCatchupRewindsAndDoesNotWrapAtBoundaries() {
        assertEquals(0L, catchupSeekTarget(65_000L, -10_000L, 600_000L, false, 60_000L))
        assertEquals(0L, catchupSeekTarget(5_000L, -10_000L, 600_000L, false, 60_000L))
        assertEquals(590_000L, catchupSeekTarget(590_000L, 10_000L, 600_000L, false, 60_000L))
    }
}
