package com.arflix.tv

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.ui.components.TrailerPlayerEntryPoint
import com.arflix.tv.ui.screens.home.HomeFocusState
import com.arflix.tv.ui.screens.home.NetflixHomeContent
import com.arflix.tv.ui.screens.home.NetflixTvHomeRowsLayer
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Native playback acceptance on hardware, isolated from the user's installed account. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TrailerQualityDiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra("video") ?: "zSWdZVtXT7E"
        if (!Regex("^[a-zA-Z0-9_-]{11}$").matches(key)) { finish(); return }
        val minimumHeight = intent.getIntExtra("height", 1080).coerceIn(720, 1080)
        val duration = intent.getLongExtra("durationMs", 60_000L).coerceIn(5_000L, 70_000L)
        val billboard = intent.getStringExtra("mode") == "billboard"
        val started = SystemClock.elapsedRealtime()
        lifecycleScope.launch {
            val extractor = EntryPointAccessors.fromApplication(applicationContext, TrailerPlayerEntryPoint::class.java)
                .inAppYouTubeExtractor()
            val source = extractor.extractPlaybackSource(key)
            val resolveMs = SystemClock.elapsedRealtime() - started
            if (source == null) {
                Log.i(TAG, "RESULT " + JSONObject(mapOf("status" to "FAIL", "video" to key, "reason" to "no source")))
                return@launch
            }
            Log.i(TAG, "SOURCE " + JSONObject(mapOf("video" to key, "resolveMs" to resolveMs,
                "maximumHeight" to source.maxVideoHeight, "separateAudio" to (source.audioUrl != null))))
            val title = when (key) {
                "zSWdZVtXT7E" -> "Interstellar"
                "Way9Dexny3w" -> "Dune: Part Two"
                "M7lc1UVf-VE" -> "YouTube Player Demo"
                else -> "Trailer"
            }
            val item = MediaItem(9001, title)
            val focus = HomeFocusState(0, 0).apply { isHeroFocused = billboard; userHasNavigated = true }
            setContent {
                CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                    NetflixTvHomeRowsLayer(listOf(Category("hd-acceptance", "Movies", listOf(item))),
                        emptyMap(), focus, 24.dp, emptyMap(), true, {}, {},
                        NetflixHomeContent(item, null, key.takeIf { billboard }, item,
                            key.takeUnless { billboard }, 0L, 0f), { false }, {})
                }
            }
            var firstFrameAt = 0L
            var cpuAtFirstFrame = 0L
            var sampledPixels = false
            var errors = 0
            var lastError: Any? = null
            var player: ExoPlayer? = null
            var view: PlayerView? = null
            while (SystemClock.elapsedRealtime() - started < 95_000L) {
                view = find(window.decorView) { it is PlayerView } as? PlayerView
                player = view?.player as? ExoPlayer
                val counters = player?.videoDecoderCounters
                counters?.ensureUpdated()
                if (player?.playerError != null && player.playerError !== lastError) {
                    lastError = player.playerError
                    errors++
                }
                val now = SystemClock.elapsedRealtime()
                if (player?.isPlaying == true && (counters?.renderedOutputBufferCount ?: 0) > 0 && firstFrameAt == 0L) {
                    firstFrameAt = now
                    cpuAtFirstFrame = Process.getElapsedCpuTime()
                    Log.i(TAG, "FIRST_FRAME " + JSONObject(mapOf("video" to key,
                        "width" to player.videoSize.width, "height" to player.videoSize.height,
                        "startupMs" to (now - started), "muted" to (player.volume == 0f))))
                }
                if (!sampledPixels && firstFrameAt > 0L && now - firstFrameAt > 1000L) {
                    val bitmap = (view?.videoSurfaceView as? TextureView)?.getBitmap(16, 9)
                    if (bitmap != null) {
                        try {
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            sampledPixels = pixels.toSet().size > 4
                        } finally { bitmap.recycle() }
                    }
                }
                if (firstFrameAt > 0L && now - firstFrameAt >= duration) break
                delay(200L)
            }
            val counters = player?.videoDecoderCounters
            counters?.ensureUpdated()
            val elapsed = (SystemClock.elapsedRealtime() - firstFrameAt).coerceAtLeast(1)
            val height = player?.videoSize?.height ?: 0
            val passed = firstFrameAt > 0 && height >= minimumHeight && height <= 1080 &&
                player?.isPlaying == true && (player?.currentPosition ?: 0) >= duration * 3 / 4 &&
                player?.volume == 0f && view?.useController == false && sampledPixels && errors == 0 &&
                find(window.decorView) { it is WebView } == null
            Log.i(TAG, "RESULT " + JSONObject(mapOf("status" to if (passed) "PASS" else "FAIL",
                "video" to key, "mode" to if (billboard) "billboard" else "card", "resolveMs" to resolveMs,
                "startupMs" to if (firstFrameAt > 0) firstFrameAt - started else -1,
                "width" to (player?.videoSize?.width ?: 0), "height" to height,
                "positionMs" to (player?.currentPosition ?: 0), "observedMs" to elapsed,
                "renderedFrames" to (counters?.renderedOutputBufferCount ?: 0),
                "droppedFrames" to (counters?.droppedBufferCount ?: 0), "errors" to errors,
                "processCpuPercent" to if (firstFrameAt > 0) (Process.getElapsedCpuTime() - cpuAtFirstFrame) * 100.0 / elapsed else -1,
                "nonblankVideo" to sampledPixels)))
        }
    }

    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            find(view.getChildAt(index), predicate)?.let { return it }
        }
        return null
    }

    private companion object { const val TAG = "TrailerHdTest" }
}
