package com.arflix.tv.ui.screens.search

/**
 * Why the search screen must not start in typing mode, and what decides it.
 *
 * The screen is opened with a select press. On a TV that same press is still travelling when
 * the new screen has already taken the focus, so it lands a second time — on the search bar,
 * where select means "start typing". Typing mode then stands up without a keyboard behind it:
 * the text field was not attached yet when the keyboard was asked for. What the user sees is a
 * ring in the search bar, no keyboard, and every direction key swallowed by an input that isn't
 * there; only BACK gets out. Reported twice from a device, reproduced by the user.
 *
 * The window itself was already opened on entry ([SEARCH_SELECT_SUPPRESS_MS] after the screen
 * comes up) but never asked about — this function is the question that was missing. Every door
 * into typing mode goes through it.
 */
internal fun startsSearchEditing(nowMs: Long, suppressSelectUntilMs: Long, repeatCount: Int = 0): Boolean =
    repeatCount == 0 && nowMs >= suppressSelectUntilMs

/**
 * How long after entering the screen a select press is taken for the one that opened it.
 *
 * Kept at the value the entry guard was written with: long enough for a press that is handed
 * on across the screen change, short enough that a user who deliberately presses select right
 * away does not lose it.
 */
internal const val SEARCH_SELECT_SUPPRESS_MS = 150L

/**
 * Whether typing mode should stay on, given what the keyboard is doing.
 *
 * The screen cannot see the BACK press that closes the keyboard: the keyboard swallows that
 * press to dismiss itself. Typing mode then outlives the keyboard, and because every direction
 * key belongs to the keyboard while typing, the user is left pressing against an input that is
 * no longer on screen — only a second BACK gets out. Reported from the TCL and from a phone in
 * the TV preview, 16.09.2026.
 *
 * Only a keyboard that HAS been reported visible counts ([keyboardWasSeen]). The moment between
 * the select press and the keyboard sliding in must not be read as "the keyboard is gone", and
 * on a device that never reports its keyboard at all nothing fires: typing mode then behaves
 * exactly as it did before, rather than ending under the user's fingers.
 */
internal fun searchEditingSurvivesKeyboard(imeVisible: Boolean, keyboardWasSeen: Boolean): Boolean =
    imeVisible || !keyboardWasSeen
