package com.arflix.tv.ui.screens.home

internal const val HOME_BACK_HOLD_MS = 600L
internal enum class HomeBackAction { NONE, BACK, RETURN_TO_START }

/** Repeated remote key-downs must not turn one hold into several Back actions. */
internal class HomeBackPress {
    private var pressedAt: Long? = null
    private var handledHold = false
    val isPressed: Boolean get() = pressedAt != null

    fun down(now: Long): Boolean {
        if (isPressed) return false
        pressedAt = now
        handledHold = false
        return true
    }

    fun hold(now: Long): Boolean {
        val start = pressedAt ?: return false
        if (handledHold || now - start < HOME_BACK_HOLD_MS) return false
        handledHold = true
        return true
    }

    fun up(now: Long): HomeBackAction {
        val start = pressedAt ?: return HomeBackAction.NONE
        val action = when {
            handledHold -> HomeBackAction.NONE
            now - start >= HOME_BACK_HOLD_MS -> HomeBackAction.RETURN_TO_START
            else -> HomeBackAction.BACK
        }
        cancel()
        return action
    }

    fun cancel() {
        pressedAt = null
        handledHold = false
    }
}
