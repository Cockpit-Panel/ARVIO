package com.arflix.tv.util

import android.content.Context

/**
 * Owns the device-local choice for optional diagnostics and usage reporting.
 * This preference is intentionally not profile-scoped or cloud-synced.
 */
data object DiagnosticsManager {
    private const val PREFERENCES_NAME = "arvio_privacy_preferences"
    private const val REPORTING_ENABLED_KEY = "diagnostics_and_usage_enabled"
    private const val USAGE_ENABLED_KEY = "usage_statistics_enabled"

    fun isUsageEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(USAGE_ENABLED_KEY, false)

    fun setUsageEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(USAGE_ENABLED_KEY, enabled).apply()
        UsageAnalytics.configure(context, enabled)
    }

    fun isReportingEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(REPORTING_ENABLED_KEY, true)
    }

    @Synchronized
    fun initialize(context: Context): Boolean {
        val appContext = context.applicationContext
        UsageAnalytics.configure(appContext, isUsageEnabled(appContext))
        if (!isReportingEnabled(appContext)) {
            disableProviders()
            return false
        }

        return CrashlyticsProvider.initialize().also { enabled ->
            LegacyAnrMonitor.configure(appContext, enabled)
        }
    }

    @Synchronized
    fun setReportingEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(REPORTING_ENABLED_KEY, enabled)
            .apply()

        if (enabled) {
            initialize(appContext)
        } else {
            disableProviders()
            LegacyAnrMonitor.configure(appContext, false)
        }
    }

    private fun disableProviders() {
        CrashlyticsProvider.disable()
        AppLogger.init(null)
    }
}
