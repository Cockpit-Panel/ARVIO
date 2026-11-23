package com.arflix.tv.ui.screens.settings

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView

@Composable
internal fun rememberSettingsInputFocusModifier(requester: FocusRequester, enabled: Boolean, blocked: Boolean): Modifier {
    val view = LocalView.current
    var coordinates by remember(requester) { mutableStateOf<LayoutCoordinates?>(null) }
    val latestBlocked by rememberUpdatedState(blocked)
    val latestCoordinates by rememberUpdatedState(coordinates)
    LaunchedEffect(requester, enabled, blocked, coordinates) {
        if (enabled && !blocked && coordinates?.isAttached == true) {
            requester.requestFocus()
        }
    }
    DisposableEffect(view, requester, enabled) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            // Dialog dismissal can restore the Android window after Compose's first frame.
            if (enabled && focused && !latestBlocked && latestCoordinates?.isAttached == true) requester.requestFocus()
        }
        observer.addOnWindowFocusChangeListener(listener)
        onDispose { if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener) }
    }
    return Modifier.onGloballyPositioned { coordinates = it }
}
