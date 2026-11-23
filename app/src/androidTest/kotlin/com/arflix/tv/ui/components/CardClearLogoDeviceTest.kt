package com.arflix.tv.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Dimension
import coil.size.Precision
import coil.size.Scale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CardClearLogoDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var loader: ImageLoader? = null
    private val files = mutableListOf<File>()
    private val loaded = AtomicReference<SuccessResult>()
    private val requests = AtomicInteger()

    @After fun close() { loader?.shutdown(); files.forEach(File::delete) }

    @Test fun expansionUsesTheFinalDecodeSizeWithoutReloadingOrOversizingTheLogo() {
        val width = mutableStateOf(100.dp)
        val url = fixture(2000, 500)
        val images = imageLoader()
        compose.setContent {
            Box(Modifier.width(width.value).background(Color.Black)) {
                CardClearLogo(url, Modifier.testTag("logo"), images)
            }
        }
        awaitLogo()
        val result = loaded.get()
        val size = runBlocking { result.request.sizeResolver.size() }
        assertEquals(with(compose.density) { 144.dp.roundToPx() }, (size.width as Dimension.Pixels).px)
        assertEquals(with(compose.density) { 40.dp.roundToPx() }, (size.height as Dimension.Pixels).px)
        assertEquals(Scale.FIT, result.request.scale)
        assertEquals(Precision.INEXACT, result.request.precision)
        assertTrue(result.drawable.intrinsicWidth >= (size.width as Dimension.Pixels).px)
        val initialRequests = requests.get()
        (120..500 step 20).forEach { value ->
            compose.runOnIdle { width.value = value.dp }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("logo").assertWidthIsEqualTo(minOf(value, 144).dp)
                .assertHeightIsEqualTo(40.dp)
        }
        assertSame("Morphing must reuse the full-resolution decode", result, loaded.get())
        assertEquals("Morphing must not fetch a logo on every frame", initialRequests, requests.get())
    }

    @Test fun aSmallSourceIsNotUpscaledIntoABlurryLogo() {
        show(fixture(80, 20))
        val bounds = paintedBounds()
        assertTrue("A small bitmap must not stretch to fill the slot", bounds[2] - bounds[0] + 1 <= 82)
        assertTrue(bounds[3] - bounds[1] + 1 <= 22)
    }

    @Test fun tallArtworkFitsWithoutCroppingOrChangingAspectRatio() {
        show(fixture(400, 800))
        val bounds = paintedBounds()
        val width = bounds[2] - bounds[0] + 1f
        val height = bounds[3] - bounds[1] + 1f
        assertEquals(0.5f, width / height, 0.05f)
        assertTrue(height <= with(compose.density) { 40.dp.toPx() } + 2)
    }

    private fun show(url: String) {
        val images = imageLoader()
        compose.setContent {
            Box(Modifier.background(Color.Black)) { CardClearLogo(url, Modifier.testTag("logo"), images) }
        }
        awaitLogo()
    }

    private fun imageLoader(): ImageLoader = ImageLoader.Builder(compose.activity)
        .allowHardware(false)
        .eventListener(object : EventListener {
            override fun onStart(request: ImageRequest) { requests.incrementAndGet() }
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.set(result) }
        }).build().also { loader = it }

    private fun fixture(width: Int, height: Int): String {
        val file = File.createTempFile("card-clearlogo-", ".png", compose.activity.cacheDir).also(files::add)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = android.graphics.Color.WHITE })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    private fun awaitLogo() {
        compose.waitUntil(5_000) { loaded.get() != null }
        compose.waitForIdle()
    }

    private fun paintedBounds(): IntArray {
        val pixels = compose.onNodeWithTag("logo").captureToImage().toPixelMap()
        var left = pixels.width
        var top = pixels.height
        var right = -1
        var bottom = -1
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (pixels[x, y].red > 0.8f) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("Logo pixels were not rendered", right >= left && bottom >= top)
        return intArrayOf(left, top, right, bottom)
    }
}
