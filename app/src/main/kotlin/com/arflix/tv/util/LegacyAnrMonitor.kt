package com.arflix.tv.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Crashlytics reports ANRs on API 30+. Older Fire TVs get a bounded non-fatal freeze sample. */
object LegacyAnrMonitor {
    private val foreground = AtomicInteger()
    private val pending = AtomicBoolean()
    private val reported = AtomicBoolean()
    private var registered = false
    private var executor: ScheduledExecutorService? = null
    private val main = Handler(Looper.getMainLooper())

    @Synchronized
    fun configure(context: Context, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) return
        if (!enabled) {
            executor?.shutdownNow()
            executor = null
            pending.set(false)
            return
        }
        val app = context.applicationContext as? Application ?: return
        if (!registered) {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) { foreground.incrementAndGet() }
                override fun onActivityPaused(activity: Activity) { foreground.updateAndGet { (it - 1).coerceAtLeast(0) } }
                override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            })
            registered = true
        }
        if (executor != null) return
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "arvio-freeze-monitor").apply { isDaemon = true }
        }.also { worker ->
            worker.scheduleWithFixedDelay({
                if (foreground.get() == 0 || Debug.isDebuggerConnected() || reported.get()) {
                    pending.set(false)
                } else if (pending.getAndSet(true)) {
                    if (reported.compareAndSet(false, true)) {
                        val error = LegacyUiFreezeException().apply { stackTrace = Looper.getMainLooper().thread.stackTrace }
                        AppLogger.recordException(error, mapOf("error_area" to "legacy_ui_freeze"))
                    }
                } else {
                    main.post { pending.set(false) }
                }
            }, 6, 6, TimeUnit.SECONDS)
        }
    }
}

class LegacyUiFreezeException : RuntimeException("Foreground main thread did not answer a six-second heartbeat")
