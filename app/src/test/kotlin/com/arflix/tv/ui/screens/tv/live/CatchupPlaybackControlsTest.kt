package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.IptvProgram
import org.junit.Assert.*
import org.junit.Test

class CatchupPlaybackControlsTest {
    private val programme = IptvProgram(title = "Archive", startUtcMillis = 1_000_000L, endUtcMillis = 4_600_000L)

    @Test fun archiveControlsUsePlaybackPositionNotAgeOfProgramme() {
        assertEquals(120_000L, programmePlaybackPosition(true, 120_000L, programme, 90_000_000L))
        assertEquals(110_000L, catchupSeekTarget(120_000L, -10_000L, 3_600_000L, true, 60_000L))
        assertEquals(130_000L, catchupSeekTarget(120_000L, 10_000L, 3_600_000L, true, 60_000L))
    }

    @Test fun liveControlsUseElapsedProgrammeTime() {
        assertEquals(600_000L, programmePlaybackPosition(false, 20_000L, programme, 1_600_000L))
        assertEquals(600_000L, catchupAvailableDuration(programme, 1_600_000L))
        assertEquals(3_600_000L, catchupAvailableDuration(programme, 90_000_000L))
        assertEquals(0L, catchupAvailableDuration(programme, 500_000L))
    }

    @Test fun nonSeekableArchiveReopensAtProviderMinuteInBothDirections() {
        assertEquals(120_000L, catchupSeekTarget(150_000L, -10_000L, 3_600_000L, false, 60_000L))
        assertEquals(180_000L, catchupSeekTarget(150_000L, 10_000L, 3_600_000L, false, 60_000L))
        assertFalse(canSeekWithinCatchupStream(false, 120_000L, 120_000L))
    }

    @Test fun nativeSeekingCanCrossMinutesWithoutReopeningButNotBeforeUrlStart() {
        assertTrue(canSeekWithinCatchupStream(true, 600_000L, 120_000L))
        assertTrue(canSeekWithinCatchupStream(true, 120_000L, 120_000L))
        assertFalse(canSeekWithinCatchupStream(true, 0L, 120_000L))
    }

    @Test fun seekCannotGoBeyondAiredPartOfLiveProgramme() {
        val available = catchupAvailableDuration(programme, 1_300_000L)
        assertEquals(299_000L, catchupSeekTarget(280_000L, 120_000L, available, true, 60_000L))
        assertEquals(0L, catchupSeekTarget(280_000L, -280_000L, available, false, 60_000L))
    }
}
