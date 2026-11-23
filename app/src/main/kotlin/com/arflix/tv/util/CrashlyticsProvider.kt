package com.arflix.tv.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Crashlytics implementation of [AppLogger.CrashContextProvider].
 *
 * Provides crash reporting with:
 * - Sanitized custom keys and playback state
 * - Non-fatal exception recording
 * - Breadcrumb logging
 *
 * Collection is configured by DiagnosticsManager, not Firebase's automatic defaults.
 */
data object CrashlyticsProvider : AppLogger.CrashContextProvider {

    private const val TAG = "Crashlytics"
    private var isInitialized = false

    /**
     * Initialize Crashlytics integration.
     * Call from Application.onCreate() after Firebase is initialized.
     *
     * Respects BuildConfig.ENABLE_CRASH_REPORTING flag:
     * - Release: enabled
     * - Debug: disabled
     * - Staging: enabled
     *
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(): Boolean {
        // Check if crash reporting is enabled for this build variant
        if (!com.arflix.tv.BuildConfig.ENABLE_CRASH_REPORTING) {
            isInitialized = false
            AppLogger.init(null)
            return false
        }

        return try {
            // Check if Firebase/Crashlytics is available
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("distribution", if (com.arflix.tv.BuildConfig.SELF_UPDATE_ENABLED) "sideload" else "play")
            crashlytics.setCustomKey("build_type", com.arflix.tv.BuildConfig.BUILD_TYPE)
            crashlytics.setCrashlyticsCollectionEnabled(true)
            isInitialized = true
            AppLogger.init(this)
            true
        } catch (e: Exception) {
            // Firebase not configured - that's OK, just disable crash reporting
            isInitialized = false
            AppLogger.init(null)
            false
        }
    }

    fun disable() {
        isInitialized = false
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(false)
                deleteUnsentReports()
            }
        }
    }

    override fun setCustomKey(key: String, value: String) {
        if (!isInitialized) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Exception) {
            // Ignore - Crashlytics not available
        }
    }

    override fun setCustomKey(key: String, value: Int) {
        if (!isInitialized) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Exception) {
            // Ignore - Crashlytics not available
        }
    }

    override fun setCustomKey(key: String, value: Boolean) {
        if (!isInitialized) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Exception) {
            // Ignore - Crashlytics not available
        }
    }

    override fun log(message: String) {
        if (!isInitialized) return
        try {
            FirebaseCrashlytics.getInstance().log(message.take(500))
        } catch (e: Exception) {
            // Ignore - Crashlytics not available
        }
    }

    override fun recordException(throwable: Throwable) {
        if (!isInitialized) return
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            // Ignore - Crashlytics not available
        }
    }

    override fun setUserId(userId: String?) {
        if (!isInitialized) return
        try {
            FirebaseCrashlytics.getInstance().setUserId(userId ?: "")
        } catch (e: Exception) {
            // Ignore - Crashlytics not available
        }
    }

    // ============================================
    // Convenience methods for common crash contexts
    // ============================================

    /**
     * Set playback context for crash reports.
     */
    fun setPlaybackContext(
        contentId: String?,
        contentType: String?,
        streamUrl: String?,
        position: Long?,
        duration: Long?
    ) {
        setCustomKey("content_type", contentType ?: "unknown")
        position?.let { setCustomKey("playback_position", it.toInt()) }
        duration?.let { setCustomKey("playback_duration", it.toInt()) }
    }

    /**
     * Set navigation context for crash reports.
     */
    fun setScreenContext(screenName: String) {
        setCustomKey("screen", screenName)
    }

    /**
     * Set network context for crash reports.
     */
    fun setNetworkContext(networkType: String, isConnected: Boolean) {
        setCustomKey("network_type", networkType)
        setCustomKey("network_connected", isConnected)
    }

}
