package com.arflix.tv.ui.screens.home

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.ui.components.TrailerPlayerEntryPoint
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class NetflixBillboardAutoplayDeviceTest {
    @Test fun billboardAutoplaysWithoutEmbedControlsAndReleasesWhenLeaving() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live network playback is opt-in", args.getString("liveTrailer") == "true")
        val key = args.getString("trailerVideo") ?: "zSWdZVtXT7E"
        val extractor = EntryPointAccessors.fromApplication(instrumentation.targetContext, TrailerPlayerEntryPoint::class.java)
            .inAppYouTubeExtractor()
        assertNotNull(runBlocking { extractor.extractPlaybackSource(key) })
        val focus = HomeFocusState(0, 0).apply { isHeroFocused = true; userHasNavigated = true }
        val item = MediaItem(9001, "Autoplay fixture", overview = "Native billboard playback")
        val rows = listOf(Category("fixture", "Movies", (1..6).map { MediaItem(it, "Title $it") }))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var host: ComponentActivity
            var preview: Player? = null
            scenario.onActivity {
                host = it
                it.setContent {
                    CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                        NetflixTvHomeRowsLayer(rows, emptyMap(), focus, 24.dp, emptyMap(), true,
                            {}, {}, NetflixHomeContent(item, null, key, null, null, 0, 0f), { false }, {})
                    }
                }
            }
            fun playing(): Boolean {
                var ready = false
                scenario.onActivity {
                    val view = find(it.window.decorView) { node -> node is PlayerView } as? PlayerView
                    val texture = view?.videoSurfaceView as? android.view.TextureView
                    ready = view?.player?.let { player -> player.isPlaying && player.videoSize.height > 0 } == true &&
                        texture?.isAvailable == true
                    if (ready) {
                        preview = view?.player
                        assertFalse("Native autoplay controls must stay hidden", view!!.useController)
                        assertEquals(0f, preview!!.volume, 0f)
                        assertNull("The billboard must not mount a YouTube WebView", find(it.window.decorView) { node -> node is WebView })
                    }
                }
                return ready
            }
            await("Billboard did not autoplay", ::playing)
            Thread.sleep(650)
            scenario.onActivity {
                val texture = (find(it.window.decorView) { node -> node is PlayerView } as PlayerView)
                    .videoSurfaceView as android.view.TextureView
                val bitmap = texture.getBitmap(16, 9)!!
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue("Native autoplay did not render video pixels", pixels.toSet().size > 4)
                } finally { bitmap.recycle() }
            }
            UiDevice.getInstance(instrumentation).takeScreenshot(java.io.File(host.getExternalFilesDir(null), "native-billboard-autoplay.png"))
            val first = preview
            scenario.onActivity { focus.isHeroFocused = false }
            await("Leaving the billboard retained its player") {
                var removed = false
                scenario.onActivity { removed = find(it.window.decorView) { node -> node is PlayerView } == null }
                removed
            }
            scenario.onActivity { assertFalse("Offscreen preview kept playing", first!!.isPlaying) }
            scenario.onActivity { focus.isHeroFocused = true }
            await("Returning did not resume native autoplay", ::playing)
            assertNotSame("The released decoder must not be reused", first, preview)
        }
    }

    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000L
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, predicate())
    }

    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), predicate)?.let { return it }
        return null
    }
}
