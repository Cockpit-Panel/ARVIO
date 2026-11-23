package com.arflix.tv.data.api

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class YoutubeGuestSessionTest {
    @Test fun readsAnonymousSessionFromGuardedBootstrap() {
        assertEquals(YoutubeGuestSession("public-key", "anonymous-visitor"),
            parseYoutubeGuestSession(")]}'\n" + bootstrap()))
    }

    @Test fun acceptsPlainJsonAndWindowsLineEndings() {
        assertNotNull(parseYoutubeGuestSession(bootstrap()))
        assertNotNull(parseYoutubeGuestSession("  )]}'\r\n" + bootstrap()))
    }

    @Test fun rejectsMissingOrInvalidSessionDataInsteadOfBreakingFallback() {
        listOf("", "not json", "<html>Unavailable</html>", "null", "{}", "[]", "[[]]",
            bootstrap(visitor = ""), bootstrap(key = ""), bootstrap(visitor = null),
            bootstrap(key = null), bootstrap(visitor = 123)).forEach {
            assertNull("Invalid guest response must not disable previews", parseYoutubeGuestSession(it))
        }
    }

    private fun bootstrap(visitor: Any? = "anonymous-visitor", key: Any? = "public-key"): String {
        val device = MutableList<Any?>(14) { null }.apply { this[13] = visitor }
        return Gson().toJson(listOf(listOf(null, null, listOf(listOf(device), key))))
    }
}
