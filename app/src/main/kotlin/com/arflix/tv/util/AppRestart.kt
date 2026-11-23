package com.arflix.tv.util

import android.content.Context
import android.content.Intent

/** Relaunches the app in a fresh process, so no in-memory state survives (used after sign-out). */
fun restartApp(context: Context) {
    val pm = context.packageManager
    val launch = pm.getLaunchIntentForPackage(context.packageName)
        ?: pm.getLeanbackLaunchIntentForPackage(context.packageName)
        ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launch)
    Runtime.getRuntime().exit(0)
}
