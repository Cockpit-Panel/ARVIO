package com.arflix.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class TraktActivationUrlTest {

    @Test
    fun appendsUserCodeToVerificationUrl() {
        assertEquals(
            "https://trakt.tv/activate/AB12CD34",
            traktActivationUrl("https://trakt.tv/activate", "AB12CD34")
        )
    }

    @Test
    fun doesNotDuplicateSlashWhenVerificationUrlEndsWithOne() {
        assertEquals(
            "https://trakt.tv/activate/AB12CD34",
            traktActivationUrl("https://trakt.tv/activate/", "AB12CD34")
        )
    }

    @Test
    fun opensPlainPageWithoutTrailingSlashWhenCodeIsMissing() {
        assertEquals(
            "https://trakt.tv/activate",
            traktActivationUrl("https://trakt.tv/activate/", "")
        )
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(
            "https://trakt.tv/activate/AB12CD34",
            traktActivationUrl("  https://trakt.tv/activate  ", " AB12CD34 ")
        )
    }

    @Test
    fun returnsEmptyStringWhenVerificationUrlIsMissing() {
        assertEquals("", traktActivationUrl("", "AB12CD34"))
    }
}
