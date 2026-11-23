package com.arflix.tv.ui.screens.tv

import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import org.junit.Assert.*
import org.junit.Test

class VisibleGuideDataTest {
    private val now = 10_000_000L
    private val expired = IptvProgram("Old", startUtcMillis = now - 120_000, endUtcMillis = now - 60_000)
    private val future = IptvProgram("Next", startUtcMillis = now + 60_000, endUtcMillis = now + 120_000)

    @Test fun expiredNextLaterAndUpcomingDoNotSuppressRefresh() {
        listOf(IptvNowNext(next = expired), IptvNowNext(later = expired),
            IptvNowNext(upcoming = listOf(expired))).forEach {
            assertFalse(hasUsefulVisibleGuideData(it, now))
        }
    }
    @Test fun actualFutureProgrammeAvoidsUnnecessaryRequests() {
        assertTrue(hasUsefulVisibleGuideData(IptvNowNext(next = future), now))
        assertTrue(hasUsefulVisibleGuideData(IptvNowNext(upcoming = listOf(expired, future)), now))
    }
    @Test fun historyAndMissingGuideStillNeedRefresh() {
        assertFalse(hasUsefulVisibleGuideData(null, now))
        assertFalse(hasUsefulVisibleGuideData(IptvNowNext(recent = listOf(expired)), now))
    }
    @Test fun currentProgrammeOnlySatisfiesExistingWarmWindow() {
        assertTrue(hasUsefulVisibleGuideData(IptvNowNext(now = expired.copy(endUtcMillis = now + 3_600_000)), now))
        assertFalse(hasUsefulVisibleGuideData(IptvNowNext(now = expired.copy(endUtcMillis = now + 60_000)), now))
    }
}
