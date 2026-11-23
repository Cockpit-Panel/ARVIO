package com.arflix.tv.ui.components

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

/** Opt-in live playback check; run separately from deterministic UI tests. */
class NativeTrailerQualityDeviceTest {
    @Test fun smallNativePreviewDecodesHdWhenTheSourceProvidesIt() {
        playAndMeasure(minimumHeight = 720)
    }

    @Test fun previewStillPlaysWhenOnlyLowerQualityIsAvailable() {
        playAndMeasure(minimumHeight = 360)
    }

    private fun playAndMeasure(minimumHeight: Int) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live network playback is opt-in", args.getString("liveTrailer") == "true")
        val key = args.getString("trailerVideo") ?: "zSWdZVtXT7E"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val extractor = EntryPointAccessors.fromApplication(instrumentation.targetContext, TrailerPlayerEntryPoint::class.java)
            .inAppYouTubeExtractor()
        val source = runBlocking { extractor.extractPlaybackSource("https://www.youtube.com/watch?v=$key") }
        assertTrue("YouTube returned no playable source", source != null)
        Log.i("TrailerQualityTest", "Source maximum=${source?.maxVideoHeight}p; separate audio=${source?.audioUrl != null}")
        if (minimumHeight >= 720) {
            val message = "YouTube only exposed ${source?.maxVideoHeight}p for this video"
            if (args.getString("requireHd") == "true") assertTrue(message, (source?.maxVideoHeight ?: 0) >= minimumHeight)
            else assumeTrue(message, (source?.maxVideoHeight ?: 0) >= minimumHeight)
        }
        // Use the real frame clock: polling a Compose-test activity without pumping
        // its virtual clock never starts AnimatedVisibility after the IO result arrives.
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var host: ComponentActivity
            var rendered = false
            scenario.onActivity {
                host = it
                it.setContent { TrailerPlayer(key, Modifier.size(400.dp, 225.dp), volume = 0f,
                    onPlayingChanged = { playing -> rendered = playing }) }
            }
            var width = 0
            var height = 0
            var ready = false
            val deadline = System.currentTimeMillis() + 60_000L
            while (!ready && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    findPlayer(host.window.decorView)?.player?.let {
                        if (it.isPlaying) {
                            width = it.videoSize.width
                            height = it.videoSize.height
                            ready = rendered && height >= minimumHeight
                        }
                    }
                }
                Thread.sleep(200)
            }
            Log.i("TrailerQualityTest", "Actual decoded preview: ${width}x$height")
            scenario.onActivity {
                val view = findPlayer(it.window.decorView)!!
                assertTrue("Autoplay must render a frame before replacing the artwork", rendered)
                assertFalse("Autoplay must not show playback controls", view.useController)
                assertTrue("Muted autoplay must stay muted", view.player!!.volume == 0f)
            }
            Thread.sleep(650)
            val image = java.io.File(host.getExternalFilesDir(null), "native-hd-trailer.png")
            UiDevice.getInstance(instrumentation).takeScreenshot(image)
            assertTrue("Expected at least ${minimumHeight}p, actual decoded resolution ${width}x$height", height >= minimumHeight)
            assertTrue("Previews must not decode 4K on low-memory devices", height <= 1080)
        }
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPlayer(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
