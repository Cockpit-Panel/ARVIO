package com.arflix.tv.ui.screens.home

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.Category
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalTestApi::class)
class HomeArtworkStabilityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val gate = CompletableDeferred<Unit>()
    private var loader: ImageLoader? = null
    @After fun close() { gate.complete(Unit); loader?.shutdown() }

    @Test fun expandingPosterKeepsItsImageUntilTheBackdropIsReady() {
        val requested = AtomicBoolean(false)
        val images = fixtureLoader(requested)
        val expanded = mutableStateOf(false)
        val title = MediaItem(1, "Fixture", image = "fixture:poster", backdrop = "fixture:backdrop")
        compose.setContent {
            Image(rememberNetflixArtwork(title, expanded.value, 120.dp, 90.dp, false, images), null,
                modifier = Modifier.size(120.dp, 90.dp).testTag("stable-artwork"))
        }
        compose.waitUntil(5_000) { center() == Color.Red }
        compose.runOnIdle { expanded.value = true }
        compose.waitUntil(5_000) { requested.get() }
        repeat(5) {
            assertEquals("A pending focus image must not blank the existing artwork", Color.Red, center())
        }
        gate.complete(Unit)
        compose.waitUntil(5_000) { center() == Color.Blue }
    }

    @Test fun readyBackdropBlendsAndFastReversalDoesNotFlashAnEndpoint() {
        val requested = AtomicBoolean(false)
        val requests = AtomicInteger()
        val images = fixtureLoader(requested, requests)
        val expanded = mutableStateOf(false)
        val displayed = AtomicReference<Painter>()
        val title = MediaItem(1, "Fixture", image = "fixture:poster", backdrop = "fixture:backdrop")
        compose.setContent {
            val artwork = rememberNetflixArtwork(title, expanded.value, 120.dp, 90.dp, false, images)
            SideEffect { displayed.set(artwork) }
            Image(artwork, null, modifier = Modifier.size(120.dp, 90.dp).testTag("stable-artwork"))
        }
        compose.waitUntil(5_000) { center() == Color.Red }
        val poster = displayed.get()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { expanded.value = true }
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(5_000) { requested.get() }
            assertEquals(Color.Red, center())
            gate.complete(Unit)
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                displayed.get() !== poster
            }
            assertEquals("The first ready frame must still contain the poster", Color.Red, center())
            compose.mainClock.advanceTimeBy(64)
            val middle = center()
            assertTrue("Artwork must blend rather than snap: $middle", middle.red > 0.05f && middle.blue > 0.05f)
            assertEquals("Animation frames must not fetch more artwork", 2, requests.get())
            compose.runOnIdle { expanded.value = false }
            compose.mainClock.advanceTimeByFrame()
            val reversed = center()
            assertTrue("Reversing focus must preserve the visible blend: $reversed",
                reversed.red > 0.05f && reversed.blue > 0.05f && kotlin.math.abs(reversed.blue - middle.blue) < 0.25f)
            compose.mainClock.advanceTimeBy(48)
            assertTrue(center().blue < reversed.blue)
            compose.mainClock.advanceTimeBy(160)
            assertEquals(Color.Red, center())
            assertTrue("At most one request per target, not per animation frame", requests.get() <= 3)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun remoteFocusMorphsWidthWithoutAHeightJumpOrInstantExpansion() {
        val focus = HomeFocusState(0, 0).apply { userHasNavigated = true }
        val rows = listOf(Category("morph", "Movies", (1..6).map { MediaItem(it, "Title $it") }))
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                HomeInputLayer(
                    categories = rows, cardLogoUrls = emptyMap(), focusState = focus,
                    limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                    contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                    usePosterCards = true, isContextMenuOpen = false, currentProfile = null,
                    onNavigateToDetails = { _, _, _, _ -> }, onNavigateToCollection = {},
                    onNavigateToSearch = {}, onNavigateToWatchlist = {}, onNavigateToTv = { _, _ -> },
                    onNavigateToSettings = {}, onSwitchProfile = {}, onExitApp = {},
                    onOpenContextMenu = { _, _ -> },
                    netflix = NetflixHomeContent(null, null, null, null, null, 0, 0f)
                )
            }
        }
        compose.waitForIdle()
        fun bounds(id: Int) = compose.onNodeWithTag("netflix_morph_MOVIE_$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val wide = bounds(1)
        val narrow = bounds(2)
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.mainClock.advanceTimeBy(80)
            val middle = bounds(2)
            assertTrue("The focused card should widen over several frames", middle.width > narrow.width + 1 && middle.width < wide.width - 1)
            assertEquals("Poster and landscape must share a stable height", wide.height, middle.height, 2f)
            compose.mainClock.advanceTimeBy(500)
            assertEquals(wide.width, bounds(2).width, 2f)
            assertEquals("The expanded title must settle at the same rail anchor", wide.left, bounds(2).left, 2f)
            compose.runOnIdle { assertEquals(1, focus.currentItemIndex) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun rightwardAndLeftwardMorphsHaveNoBacktrackingOrLargePositionSteps() {
        val focus = HomeFocusState(0, 0).apply { userHasNavigated = true }
        val rows = listOf(Category("morph", "Movies", (1..12).map { MediaItem(it, "Title $it") }))
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                HomeInputLayer(
                    categories = rows, cardLogoUrls = emptyMap(), focusState = focus,
                    limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                    contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                    usePosterCards = true, isContextMenuOpen = false, currentProfile = null,
                    onNavigateToDetails = { _, _, _, _ -> }, onNavigateToCollection = {},
                    onNavigateToSearch = {}, onNavigateToWatchlist = {}, onNavigateToTv = { _, _ -> },
                    onNavigateToSettings = {}, onSwitchProfile = {}, onExitApp = {},
                    onOpenContextMenu = { _, _ -> },
                    netflix = NetflixHomeContent(null, null, null, null, null, 0, 0f)
                )
            }
        }
        compose.waitForIdle()
        fun left(id: Int) = compose.onNodeWithTag("netflix_morph_MOVIE_$id", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        val anchor = left(1)
        compose.mainClock.autoAdvance = false
        try {
            fun move(key: Key, id: Int) {
                val start = left(id)
                compose.onRoot().performKeyInput { pressKey(key) }
                val samples = mutableListOf(start)
                repeat(20) {
                    compose.mainClock.advanceTimeByFrame()
                    samples += left(id)
                }
                val distance = kotlin.math.abs(start - anchor)
                android.util.Log.i("NetflixMorphTest", "Card $id positions: $samples")
                samples.zipWithNext().forEach { (before, after) ->
                    val delta = after - before
                    assertTrue("Card $id reversed direction: $samples",
                        if (start > anchor) delta <= 2f else delta >= -2f)
                    assertTrue("Card $id jumped instead of moving smoothly: $samples",
                        kotlin.math.abs(delta) < distance * 0.24f + 4f)
                }
                assertEquals("Card $id must settle exactly on the rail anchor", anchor, samples.last(), 2f)
            }
            (2..6).forEach { move(Key.DirectionRight, it) }
            (5 downTo 1).forEach { move(Key.DirectionLeft, it) }
            // Retarget before either the rail or card width has settled.
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.mainClock.advanceTimeBy(80)
            val before = left(1)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.mainClock.advanceTimeByFrame()
            // One frame can still advance the previous trajectory while Compose applies
            // the new key. It must not snap to the old or new endpoint on that frame.
            assertEquals("A rapid reversal must continue from the visible position", before, left(1),
                kotlin.math.abs(anchor - before) * 0.24f + 4f)
            compose.mainClock.advanceTimeBy(400)
            assertEquals(anchor, left(1), 2f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun fixtureLoader(requested: AtomicBoolean, requests: AtomicInteger = AtomicInteger()): ImageLoader {
        val factory = object : Fetcher.Factory<android.net.Uri> {
            override fun create(data: android.net.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                if (data.scheme != "fixture") return null
                val backdrop = data.toString().endsWith("backdrop")
                return Fetcher {
                    requests.incrementAndGet()
                    if (backdrop) { requested.set(true); gate.await() }
                    val bitmap = Bitmap.createBitmap(if (backdrop) 160 else 80, if (backdrop) 90 else 120,
                        Bitmap.Config.ARGB_8888).apply {
                        eraseColor(if (backdrop) android.graphics.Color.BLUE else android.graphics.Color.RED)
                    }
                    DrawableResult(BitmapDrawable(compose.activity.resources, bitmap), false, DataSource.NETWORK)
                }
            }
        }
        return ImageLoader.Builder(compose.activity).components { add(factory) }.build().also { loader = it }
    }

    private fun center(): Color {
        val pixels = compose.onNodeWithTag("stable-artwork").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
}
