package com.arflix.tv.ui.screens.tv.live

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveTvResponsiveLayoutTest {

    @Test
    fun landscapePhoneUsesShortMiniPlayerSoGuideRemainsVisible() {
        val layout = liveTvMiniPlayerLayout(
            isTouchDevice = true,
            availableWidthDp = 892,
            availableHeightDp = 360,
        )

        assertThat(layout).isEqualTo(LiveTvMiniPlayerLayout.LANDSCAPE_COMPACT)
    }

    @Test
    fun portraitPhoneKeepsTheExistingFullWidthStackedPlayer() {
        val layout = liveTvMiniPlayerLayout(
            isTouchDevice = true,
            availableWidthDp = 411,
            availableHeightDp = 892,
        )

        assertThat(layout).isEqualTo(LiveTvMiniPlayerLayout.PORTRAIT_STACKED)
    }

    @Test
    fun landscapeTabletUsesStandardSideBySidePlayer() {
        val layout = liveTvMiniPlayerLayout(
            isTouchDevice = true,
            availableWidthDp = 1280,
            availableHeightDp = 800,
        )

        assertThat(layout).isEqualTo(LiveTvMiniPlayerLayout.STANDARD)
    }

    @Test
    fun televisionKeepsTheExistingStandardPlayer() {
        val layout = liveTvMiniPlayerLayout(
            isTouchDevice = false,
            availableWidthDp = 1280,
            availableHeightDp = 720,
        )

        assertThat(layout).isEqualTo(LiveTvMiniPlayerLayout.STANDARD)
    }

    @Test
    fun landscapePhoneMiniPlayerLeavesRoomForCategoryRailAndGuide() {
        val spec = landscapePhoneMiniPlayerSpec()

        assertThat(spec.videoWidthDp).isAtMost(180)
        assertThat(spec.videoHeightDp).isAtMost(102)
        assertThat(spec.totalHeightDp).isAtMost(116)
        assertThat(spec.showDescription).isFalse()
        assertThat(spec.showNextProgramme).isFalse()
    }

    @Test
    fun landscapeGeometryWinsIfAPathAlsoRequestsStackedCompactSizing() {
        assertThat(miniPlayerVideoSizeMode(compact = true, landscapeCompact = true))
            .isEqualTo(MiniPlayerVideoSizeMode.LANDSCAPE_COMPACT)
    }

    @Test
    fun largeLandscapePhoneStillUsesCompactPlayer() {
        assertThat(
            liveTvMiniPlayerLayout(
                isTouchDevice = true,
                availableWidthDp = 915,
                availableHeightDp = 411,
            )
        ).isEqualTo(LiveTvMiniPlayerLayout.LANDSCAPE_COMPACT)
    }

    @Test
    fun foldedAndUnfoldedFoldablesFollowAvailableGeometry() {
        assertThat(liveTvMiniPlayerLayout(true, 360, 800))
            .isEqualTo(LiveTvMiniPlayerLayout.PORTRAIT_STACKED)
        assertThat(liveTvMiniPlayerLayout(true, 800, 360))
            .isEqualTo(LiveTvMiniPlayerLayout.LANDSCAPE_COMPACT)
        assertThat(liveTvMiniPlayerLayout(true, 841, 673))
            .isEqualTo(LiveTvMiniPlayerLayout.STANDARD)
    }

    @Test
    fun portraitTabletUsesStandardPlayer() {
        assertThat(liveTvMiniPlayerLayout(true, 800, 1280))
            .isEqualTo(LiveTvMiniPlayerLayout.STANDARD)
    }

    @Test
    fun narrowLandscapeStillLeavesVisibleEpgRows() {
        val guideHeight = landscapeCompactGuideHeightDp(availableHeightDp = 300)

        assertThat(guideHeight).isAtLeast(96)
    }

    @Test
    fun rotatingDuringNavigationReevaluatesTheLayout() {
        val portrait = liveTvMiniPlayerLayout(true, 411, 892)
        val landscape = liveTvMiniPlayerLayout(true, 892, 411)
        val portraitAgain = liveTvMiniPlayerLayout(true, 411, 892)

        assertThat(portrait).isEqualTo(LiveTvMiniPlayerLayout.PORTRAIT_STACKED)
        assertThat(landscape).isEqualTo(LiveTvMiniPlayerLayout.LANDSCAPE_COMPACT)
        assertThat(portraitAgain).isEqualTo(portrait)
    }
}
