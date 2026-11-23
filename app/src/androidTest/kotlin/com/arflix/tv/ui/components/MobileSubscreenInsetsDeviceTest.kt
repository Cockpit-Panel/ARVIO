package com.arflix.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MobileSubscreenInsetsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenAppBarStillReservesGestureAndThreeButtonNavigation() {
        val inset = mutableStateOf(24)
        compose.setContent {
            val bars = WindowInsets(bottom = inset.value)
            Box(Modifier.size(300.dp, 360.dp).testTag("outer")) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(mobileContentInsets(bars, false))) {
                    // A subscreen's own inset must be consumed, not applied twice.
                    Column(Modifier.fillMaxSize().windowInsetsPadding(bars)
                        .verticalScroll(rememberScrollState())) {
                        repeat(20) { Text("Setting $it", Modifier.height(48.dp)) }
                        Text("Last setting", Modifier.height(48.dp).testTag("last"))
                    }
                }
            }
        }
        for (bottom in listOf(24, 48, 0)) {
            compose.runOnIdle { inset.value = bottom }
            compose.onNodeWithTag("last").performScrollTo().assertIsDisplayed()
            val outer = compose.onNodeWithTag("outer").fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithTag("last").fetchSemanticsNode().boundsInRoot
            assertEquals(bottom.toFloat(), outer.bottom - last.bottom, 1f)
        }
    }
}
