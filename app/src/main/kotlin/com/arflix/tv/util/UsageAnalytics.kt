package com.arflix.tv.util

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.arflix.tv.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics

/** Device-local opt-in only. Never accept arbitrary titles, URLs, account IDs or search text. */
object UsageAnalytics {
    @Volatile private var analytics: FirebaseAnalytics? = null

    @Synchronized
    fun configure(context: Context, enabled: Boolean) {
        runCatching {
            val sdk = FirebaseAnalytics.getInstance(context.applicationContext)
            val allowed = enabled && BuildConfig.ENABLE_CRASH_REPORTING
            sdk.setConsent(mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (allowed)
                    FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
            ))
            sdk.setAnalyticsCollectionEnabled(allowed)
            sdk.setUserId(null)
            if (!allowed && analytics != null) sdk.resetAnalyticsData()
            analytics = sdk.takeIf { allowed }
        }.onFailure { analytics = null }
    }

    fun screen(route: String?) {
        val screen = AnalyticsPolicy.screen(route) ?: return
        AppLogger.setCrashContext("screen", screen)
        analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screen)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screen)
        })
    }

    private fun playbackEvent(name: String, live: Boolean, duration: Long = 0, code: Int = 0) {
        analytics?.logEvent(name, Bundle().apply {
            putString("media_kind", if (live) "live" else "vod")
            putLong("elapsed_ms", duration.coerceIn(0, 3_600_000))
            if (code != 0) putLong("error_code", code.toLong())
        })
    }

    /** One listener per player, not per recomposition or rendered frame. */
    fun playbackListener(live: Boolean): Player.Listener = object : Player.Listener {
        private val created = SystemClock.elapsedRealtime()
        private var started = false
        private var bufferingSince = 0L
        private var reportedBuffering = false
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && !started) {
                started = true
                playbackEvent("playback_start", live, SystemClock.elapsedRealtime() - created)
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            val now = SystemClock.elapsedRealtime()
            if (playbackState == Player.STATE_BUFFERING && started && bufferingSince == 0L) {
                bufferingSince = now
            } else if (playbackState == Player.STATE_READY && bufferingSince != 0L) {
                if (!reportedBuffering) {
                    playbackEvent("playback_buffering", live, now - bufferingSince)
                    reportedBuffering = true
                }
                bufferingSince = 0L
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            playbackEvent("playback_failed", live, code = error.errorCode)
        }
    }
}
