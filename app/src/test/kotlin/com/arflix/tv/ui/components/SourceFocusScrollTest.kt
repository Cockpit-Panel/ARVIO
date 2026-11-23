package com.arflix.tv.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SourceFocusScrollTest {
    @Test fun fullyVisibleRowsDoNotScroll() {
        assertThat(sourceFocusScrollDelta(50, 92, 0, 400, 20)).isEqualTo(0)
        assertThat(sourceFocusScrollDelta(20, 360, 0, 400, 20)).isEqualTo(0)
    }

    @Test fun partialBottomRowScrollsOnlyTheMissingPixels() {
        assertThat(sourceFocusScrollDelta(310, 92, 0, 400, 20)).isEqualTo(22)
        assertThat(sourceFocusScrollDelta(310, 142, 0, 400, 20)).isEqualTo(72)
    }

    @Test fun partialTopRowScrollsBackWithoutJumpingAnotherRow() {
        assertThat(sourceFocusScrollDelta(-15, 142, 0, 400, 20)).isEqualTo(-35)
    }

    @Test fun contentPaddingAndViewportOffsetsAreRespected() {
        assertThat(sourceFocusScrollDelta(15, 92, -10, 400, 20)).isEqualTo(0)
        assertThat(sourceFocusScrollDelta(350, 92, -10, 400, 20)).isEqualTo(62)
    }

    @Test fun oversizedReleaseRowAlignsItsStartWithoutOscillating() {
        assertThat(sourceFocusScrollDelta(100, 500, 0, 400, 20)).isEqualTo(80)
        assertThat(sourceFocusScrollDelta(20, 500, 0, 400, 20)).isEqualTo(0)
    }

    @Test fun zeroSizedViewportDoesNotProduceInvalidScroll() {
        assertThat(sourceFocusScrollDelta(0, 92, 0, 0, 20)).isEqualTo(0)
    }
}
