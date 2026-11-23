package com.arflix.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsFocusGeometryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun variableHeightRowsRevealOnlyTheirOverflow() {
        val tracker = SettingsFocusTracker()
        lateinit var scroll: ScrollState
        lateinit var scope: CoroutineScope
        compose.setContent {
            scroll = rememberScrollState()
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV, LocalSettingsFocusTracker provides tracker) {
                Column(Modifier.size(300.dp, 180.dp).onGloballyPositioned { tracker.viewport = it }.verticalScroll(scroll)) {
                    repeat(8) { index ->
                        Box(Modifier.fillMaxWidth().height(if (index % 2 == 0) 50.dp else 90.dp).settingsFocusSlot(index))
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(0f, tracker.revealDelta(0)!!, 1f)
            assertTrue(tracker.revealDelta(3)!! > 0f)
            scope.launch { scroll.scrollTo(tracker.revealDelta(3)!!.toInt()) }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0f, tracker.revealDelta(3)!!, 1f)
            assertTrue(tracker.revealDelta(0)!! < 0f)
        }
    }
}
