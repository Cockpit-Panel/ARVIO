package com.arflix.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A playlist address pasted on the TV was stored as `.../list.m3u` followed by a NUL character.
 * `trim()` keeps NUL, so every request went to `list.m3u%00` and the server answered HTTP 400.
 */
class IptvInputSanitizingTest {

    @Test fun `a trailing NUL from a paste is removed`() {
        assertEquals(
            "https://example.test/list.m3u",
            stripInvisibleInputChars("https://example.test/list.m3u\u0000"),
        )
    }

    @Test fun `zero-width and direction marks are removed`() {
        assertEquals(
            "https://example.test/list.m3u",
            stripInvisibleInputChars("﻿https://example.test/​list.m3u‎"),
        )
    }

    @Test fun `ordinary text is unchanged`() {
        val url = "http://example.test:8080/get.php?username=u&password=p&type=m3u_plus"
        assertEquals(url, stripInvisibleInputChars(url))
    }

    @Test fun `guide fields keep the separators between several addresses`() {
        assertEquals(
            "https://a.test/epg.xml\nhttps://b.test/epg.xml\thost",
            stripInvisibleInputChars("https://a.test/epg.xml\u0000\nhttps://b.test/epg.xml\thost", keepSeparators = true),
        )
        assertEquals("ab", stripInvisibleInputChars("a\n\tb"))
    }

    @Test fun `a pasted Stalker portal and MAC lose the invisible characters`() {
        val portal = StalkerPortalSupport.normalizeStalkerPortalEntry(
            StalkerPortalEntry("s1", "Portal", "http://portal.test/c/\u0000", "00:1a:79:00:00:01​"),
            index = 0,
        )

        assertEquals("http://portal.test/c", portal?.portalUrl)
        assertEquals("00:1A:79:00:00:01", portal?.macAddress)
    }
}
