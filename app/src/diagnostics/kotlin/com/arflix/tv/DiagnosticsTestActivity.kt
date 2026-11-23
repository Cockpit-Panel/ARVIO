package com.arflix.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.DiagnosticsManager
import com.arflix.tv.util.UsageAnalytics

/** Explicit test-only entry point in the separate beta package. */
class DiagnosticsTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra("action") ?: "status"
        setContentView(TextView(this).apply { text = "ARVIO diagnostics test: $action" })
        when (action) {
            "enable" -> {
                DiagnosticsManager.setReportingEnabled(this, true)
                DiagnosticsManager.setUsageEnabled(this, true)
                UsageAnalytics.screen("settings")
            }
            "disable" -> {
                DiagnosticsManager.setUsageEnabled(this, false)
                DiagnosticsManager.setReportingEnabled(this, false)
            }
            "nonfatal" -> AppLogger.recordException(IllegalStateException("ARVIO Firebase verification nonfatal"))
            "crash" -> Handler(Looper.getMainLooper()).postDelayed({
                throw IllegalStateException("ARVIO Firebase verification fatal")
            }, 1_000)
        }
    }
}
