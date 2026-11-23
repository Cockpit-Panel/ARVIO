package com.arflix.tv.ui.screens.tv.live

import org.junit.Assert.*
import org.junit.Test

class SportsLoadingTest {
    @Test fun initialScanWaitsUntilWorkStarts() {
        assertTrue(shouldShowSportsLoading(false, false, false, false))
    }
    @Test fun failedInitialScanExitsLoadingWithoutASuccessfulScan() {
        assertFalse(shouldShowSportsLoading(false, false, false, true))
    }
    @Test fun retryShowsLoadingWhileItRuns() {
        assertTrue(shouldShowSportsLoading(false, true, false, true))
    }
    @Test fun completedEmptyScanShowsEmptyState() {
        assertFalse(shouldShowSportsLoading(false, false, true, false))
    }
    @Test fun usableEventsStayVisibleDuringBackgroundWork() {
        assertFalse(shouldShowSportsLoading(true, true, false, false))
    }
}
