package com.arflix.tv.ui.components

/** Keeps an explicit user pause separate from a pause caused by leaving the app. */
internal class TrailerPlaybackLifecycle {
    private var foreground = false
    private var playing = false
    private var resumeOnReturn = false

    fun requestStart(): Boolean {
        if (foreground) return true
        resumeOnReturn = true
        return false
    }

    fun onPlaying() {
        playing = true
    }

    fun onPaused() {
        playing = false
        if (foreground) resumeOnReturn = false
    }

    fun onEnded() {
        playing = false
        resumeOnReturn = false
    }

    fun onBackground() {
        if (foreground) resumeOnReturn = playing
        foreground = false
    }

    fun onForeground(): Boolean {
        foreground = true
        val resume = resumeOnReturn
        resumeOnReturn = false
        return resume
    }
}
