package com.arflix.tv.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Navigation differences of a kids profile. Live TV has no age ratings, so a kids
 * profile does not get the tab at all.
 *
 * Process-wide snapshot state rather than a parameter: the top bar's index helpers
 * ([topBarMaxIndex], [topBarFocusedItem]) are plain functions called from every
 * screen's key handling, and they must all agree on which items exist.
 */
object KidsNavigation {
    /** Set by the navigation host from the active profile. */
    var hideLiveTv by mutableStateOf(false)
}
