package com.arflix.tv.ui.components

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Live integration test using real frame clocks and YouTube playback events. */
class YouTubeTrailerDeviceTest {
    @Test fun playerLoadsAndBackClosesWithoutLeavingHost() {
        val args = InstrumentationRegistry.getArguments()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        val tv = args.getString("trailerDevice") == "tv"
        val state = AtomicReference<PlayerState>()
        val shown = mutableStateOf(true)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var host: ComponentActivity
            scenario.onActivity { activity ->
                host = activity
                activity.setContent {
                    CompositionLocalProvider(LocalDeviceType provides if (tv) DeviceType.TV else DeviceType.PHONE) {
                        if (shown.value) YouTubeTrailerModal(
                            youtubeKey = args.getString("trailerVideo") ?: "M7lc1UVf-VE",
                            onPlaybackStateChanged = {
                                Log.i("TrailerDeviceTest", "Playback state: $it")
                                state.set(it)
                            },
                            onClose = { shown.value = false }
                        )
                    }
                }
            }
            val output = File(host.getExternalFilesDir(null), "trailer-test").apply { mkdirs() }
            val label = if (tv) "tv" else "phone"
            try {
                await("YouTube did not start playing", 60000) { state.get() == PlayerState.PLAYING }
                Thread.sleep(2000)
                device.takeScreenshot(File(output, "$label.png"))
                if (tv) {
                    device.pressDPadCenter()
                    await("Remote OK did not pause playback") { state.get() == PlayerState.PAUSED }
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    Thread.sleep(2000)
                    assertTrue("A deliberate pause must survive returning to the app", state.get() == PlayerState.PAUSED)
                    device.takeScreenshot(File(output, "$label-paused-after-return.png"))
                    device.pressDPadCenter()
                    await("Remote OK did not resume playback") { state.get() == PlayerState.PLAYING }
                    device.pressDPadUp()
                    Thread.sleep(500)
                    scenario.onActivity {
                        assertTrue("YouTube controls must accept remote focus", findWebView(it.window.decorView)?.hasFocus() == true)
                    }
                    device.pressDPadRight()
                    device.takeScreenshot(File(output, "$label-native-controls.png"))

                }
                val holdMs = args.getString("trailerHoldMs")?.toLongOrNull() ?: 0L
                if (holdMs > 0) Thread.sleep(holdMs.coerceAtMost(180000))
                device.pressBack()
                await("Back did not close the trailer") { !shown.value }
                scenario.onActivity { assertFalse("Back must preserve the host screen", it.isFinishing) }
            } finally {
                device.takeScreenshot(File(output, "$label-final.png"))
            }
        }
    }

    private fun await(message: String, timeoutMs: Long = 10000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, predicate())
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
