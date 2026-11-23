package com.arflix.tv.ui.screens.home

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class NetflixCardTrailerLogoDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realTrailerKeepsTheClearlogoCompactAndStable() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live network playback is opt-in", args.getString("liveTrailer") == "true")
        val logo = requireNotNull(args.getString("trailerLogo"))
        val video = args.getString("trailerVideo") ?: "zSWdZVtXT7E"
        val item = MediaItem(157336, "Interstellar", overview = "A team of explorers travel beyond this galaxy.")
        val focus = HomeFocusState(0, 0).apply { userHasNavigated = true }
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                NetflixTvHomeRowsLayer(
                    listOf(Category("logo_test", "Movies", listOf(item, MediaItem(2, "Next title")))),
                    mapOf("MOVIE_157336" to logo), focus, 24.dp, emptyMap(), true, {}, {},
                    NetflixHomeContent(null, null, null, item, video, 4_000, 0f), { false }, {}
                )
            }
        }
        val node = compose.onNodeWithTag("netflix_logo_MOVIE_157336", useUnmergedTree = true)
        node.assertIsDisplayed()
        compose.waitUntil(8_000) {
            val pixels = node.captureToImage().toPixelMap()
            var white = 0
            for (y in 2 until pixels.height - 2 step 2) for (x in 4 until pixels.width - 2 step 2) {
                val pixel = pixels[x, y]
                if (pixel.red > 0.8f && pixel.green > 0.8f && pixel.blue > 0.8f) white++
            }
            white > 20
        }
        val before = node.fetchSemanticsNode().boundsInRoot
        val density = compose.density
        assertTrue(before.width <= with(density) { 160.dp.toPx() } + 1)
        assertTrue(before.height <= with(density) { 56.dp.toPx() } + 1)
        capture("card-clearlogo-before-trailer.png")
        compose.waitUntil(60_000) {
            compose.mainClock.advanceTimeByFrame()
            var playing = false
            compose.runOnIdle {
                player(compose.activity.window.decorView)?.let { view ->
                    playing = view.player?.let { it.isPlaying && it.videoSize.height > 0 } == true
                }
            }
            playing
        }
        compose.mainClock.advanceTimeBy(800)
        Thread.sleep(700)
        compose.runOnIdle {
            val view = player(compose.activity.window.decorView)!!
            assertFalse("Trailer controls must remain hidden", view.useController)
            assertEquals(0f, view.player!!.volume, 0f)
            val texture = view.videoSurfaceView as android.view.TextureView
            val bitmap = texture.getBitmap(32, 18)!!
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("The playing trailer must render real video pixels", pixels.toSet().size > 4)
            } finally { bitmap.recycle() }
        }
        assertEquals("Playback must not resize or move the clearlogo", before, node.fetchSemanticsNode().boundsInRoot)
        capture("card-clearlogo-during-trailer.png")
    }

    private fun capture(name: String) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(File(compose.activity.getExternalFilesDir(null), name))
    }

    private fun player(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) player(view.getChildAt(i))?.let { return it }
        return null
    }
}
