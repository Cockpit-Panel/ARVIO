package com.arflix.tv.data.repository

import com.arflix.tv.data.api.StalkerApi
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvProgram
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Stalker EPG merge helpers on [IptvRepository] (marked `internal`
 * so tests can call them directly, same convention as [IptvEpgIndex]/[StalkerPortalSupport]).
 * Isolation comes from the channel id prefix (see [StalkerPortalSupport]), not a
 * separate per-portal EPG-index source key.
 */
class IptvRepositoryStalkerEpgTest {

    private fun newRepository(): IptvRepository {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val okHttpClient = io.mockk.mockk<okhttp3.OkHttpClient>(relaxed = true)
        val profileManager = io.mockk.mockk<ProfileManager>(relaxed = true)
        val invalidationBus = io.mockk.mockk<CloudSyncInvalidationBus>(relaxed = true)
        return IptvRepository(context, okHttpClient, profileManager, invalidationBus)
    }

    private fun stubStalkerApi(
        portal: String = "http://portal.example.com",
        mac: String = "00:1A:79:AA:BB:CC",
        respond: (String) -> String?
    ): StalkerApi =
        object : StalkerApi(portal, mac) {
            override fun doGet(url: String): String = respond(url) ?: error("Unexpected url: $url")
            override fun doGetReader(url: String): java.io.Reader =
                (respond(url) ?: error("Unexpected url: $url")).reader()
        }

    private fun program(startMs: Long, endMs: Long, title: String) =
        IptvProgram(title = title, startUtcMillis = startMs, endUtcMillis = endMs)

    @Test
    fun `stalkerNowNextFromPrograms returns null for empty list`() {
        val repository = newRepository()
        assertNull(repository.stalkerNowNextFromPrograms(emptyList(), nowMs = 10_000L))
    }

    @Test
    fun `stalkerNowNextFromPrograms compacts sorted programs into now next later upcoming`() {
        val repository = newRepository()
        val nowMs = 10_000L
        val programs = listOf(
            program(0L, 5_000L, "Past"), // already over, not "now"
            program(5_000L, 15_000L, "Now"), // live at nowMs
            program(15_000L, 20_000L, "Next"),
            program(20_000L, 25_000L, "Later"),
            program(25_000L, 30_000L, "Upcoming1"),
            program(30_000L, 35_000L, "Upcoming2")
        )

        val result = repository.stalkerNowNextFromPrograms(programs.shuffled(), nowMs)

        requireNotNull(result)
        assertEquals("Now", result.now?.title)
        assertEquals("Next", result.next?.title)
        assertEquals("Later", result.later?.title)
        assertEquals(listOf("Upcoming1", "Upcoming2"), result.upcoming.map { it.title })
        assertTrue("Stalker EPG keeps no recent/catchup history (K10 out of scope)", result.recent.isEmpty())
    }

    @Test
    fun `fetchStalkerEpgForActivePortals keys results by full channel id and isolates portals`() = runTest {
        val repository = newRepository()
        // fetchStalkerEpgForActivePortals compacts against the real wall-clock "now"
        // (System.currentTimeMillis()), so the stubbed program must actually be live now -
        // a fixed epoch like 0/10 would always be in the past and get dropped, not merged.
        val nowSec = System.currentTimeMillis() / 1000
        val startSec = nowSec - 60
        val stopSec = nowSec + 60
        val portal1Api = stubStalkerApi { url ->
            if (url.contains("action=get_simple_data_table")) {
                """{ "js": [
                    { "ch_id": "100", "name": "Portal1 News", "start_timestamp": "$startSec", "stop_timestamp": "$stopSec" }
                ] }"""
            } else null
        }
        val portal2Api = stubStalkerApi { url ->
            if (url.contains("action=get_simple_data_table")) {
                // Same ch_id as portal 1 on purpose: without isolation this would collide (C1/C6).
                """{ "js": [
                    { "ch_id": "100", "name": "Portal2 Movie", "start_timestamp": "$startSec", "stop_timestamp": "$stopSec" }
                ] }"""
            } else null
        }
        val channels = listOf(
            IptvChannel(id = "stalker:stalker1:100", name = "Ch A", logo = null, group = "g", streamUrl = "cmd"),
            IptvChannel(id = "stalker:stalker2:100", name = "Ch B", logo = null, group = "g", streamUrl = "cmd")
        )

        val result = repository.fetchStalkerEpgForActivePortals(
            mapOf("stalker1" to portal1Api, "stalker2" to portal2Api),
            channels
        )

        assertEquals(setOf("stalker:stalker1:100", "stalker:stalker2:100"), result.keys)
        assertEquals("Portal1 News", result.getValue("stalker:stalker1:100").now?.title)
        assertEquals("Portal2 Movie", result.getValue("stalker:stalker2:100").now?.title)
    }

    @Test
    fun `fetchStalkerEpgForActivePortals skips programs with unknown channel id or malformed timestamps`() = runTest {
        val repository = newRepository()
        val api = stubStalkerApi { url ->
            if (url.contains("action=get_simple_data_table")) {
                """{ "js": [
                    { "ch_id": "999", "name": "Unknown channel", "start_timestamp": "0", "stop_timestamp": "10" },
                    { "ch_id": "100", "name": "Bad timestamps", "start_timestamp": "not-a-number", "stop_timestamp": "10" },
                    { "ch_id": "100", "name": "End before start", "start_timestamp": "10", "stop_timestamp": "5" }
                ] }"""
            } else null
        }
        val channels = listOf(
            IptvChannel(id = "stalker:stalker1:100", name = "Ch A", logo = null, group = "g", streamUrl = "cmd")
        )

        val result = repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)

        assertTrue("No valid programs in the response, result must stay empty", result.isEmpty())
    }

    @Test
    fun `fetchStalkerEpgForActivePortals returns empty map without network calls when no portals or channels`() = runTest {
        val repository = newRepository()
        assertTrue(repository.fetchStalkerEpgForActivePortals(emptyMap(), emptyList()).isEmpty())
    }

    @Test
    fun `fetchStalkerEpgForActivePortals falls back to get_short_epg per channel when both bulk actions are empty`() = runTest {
        // Confirmed live on-device (EPG-Test.md, Portal 1): get_simple_data_table and
        // get_epg_info both return nothing on some portal builds, only get_short_epg works.
        val repository = newRepository()
        val nowSec = System.currentTimeMillis() / 1000
        val startSec = nowSec - 60
        val stopSec = nowSec + 60
        val api = stubStalkerApi { url ->
            when {
                url.contains("action=get_simple_data_table") -> """{ "js": [] }"""
                url.contains("action=get_epg_info") -> """{ "js": { "data": [] } }"""
                url.contains("action=get_short_epg") && url.contains("ch_id=100") ->
                    """{ "js": [
                        { "ch_id": "100", "name": "Fallback Show", "start_timestamp": "$startSec", "stop_timestamp": "$stopSec" }
                    ] }"""
                url.contains("action=get_short_epg") && url.contains("ch_id=200") ->
                    """{ "js": [] }"""
                else -> null
            }
        }
        val channels = listOf(
            IptvChannel(id = "stalker:stalker1:100", name = "Ch A", logo = null, group = "g", streamUrl = "cmd"),
            IptvChannel(id = "stalker:stalker1:200", name = "Ch B (no programs)", logo = null, group = "g", streamUrl = "cmd")
        )

        val result = repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)

        assertEquals(setOf("stalker:stalker1:100"), result.keys)
        assertEquals("Fallback Show", result.getValue("stalker:stalker1:100").now?.title)
    }

    @Test
    fun `fetchStalkerEpgForActivePortals reuses the cached bulk result within the TTL instead of re-fetching`() = runTest {
        // Confirmed live: a large portal's get_epg_info response can be ~25 MB, taking
        // seconds per fetch - refetching it on every small on-demand batch call caused
        // real, user-visible lag. A short per-portal cache must avoid that repeat fetch.
        val repository = newRepository()
        val nowSec = System.currentTimeMillis() / 1000
        val startSec = nowSec - 60
        val stopSec = nowSec + 60
        var bulkRequestCount = 0
        val api = stubStalkerApi { url ->
            when {
                url.contains("action=get_simple_data_table") -> {
                    bulkRequestCount++
                    """{ "js": [
                        { "ch_id": "100", "name": "Cached Show", "start_timestamp": "$startSec", "stop_timestamp": "$stopSec" }
                    ] }"""
                }
                else -> null
            }
        }
        val channels = listOf(
            IptvChannel(id = "stalker:stalker1:100", name = "Ch A", logo = null, group = "g", streamUrl = "cmd")
        )

        val first = repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)
        val second = repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)

        assertEquals(1, bulkRequestCount)
        assertEquals("Cached Show", first.getValue("stalker:stalker1:100").now?.title)
        assertEquals("Cached Show", second.getValue("stalker:stalker1:100").now?.title)
    }

    @Test
    fun `bulk cache is isolated when the same portal id points to different credentials`() = runTest {
        val repository = newRepository()
        val nowSec = System.currentTimeMillis() / 1000
        val channels = listOf(
            IptvChannel(id = "stalker:stalker1:100", name = "Ch A", logo = null, group = "g", streamUrl = "cmd")
        )
        fun response(title: String) = """{ "js": [
            { "ch_id": "100", "name": "$title", "start_timestamp": "${nowSec - 60}", "stop_timestamp": "${nowSec + 60}" }
        ] }"""
        val firstApi = stubStalkerApi(portal = "http://first.example.com") { url ->
            if (url.contains("action=get_simple_data_table")) response("First portal") else null
        }
        val secondApi = stubStalkerApi(portal = "http://second.example.com") { url ->
            if (url.contains("action=get_simple_data_table")) response("Second portal") else null
        }

        val first = repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to firstApi), channels)
        val second = repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to secondApi), channels)

        assertEquals("First portal", first.getValue("stalker:stalker1:100").now?.title)
        assertEquals("Second portal", second.getValue("stalker:stalker1:100").now?.title)
    }

    @Test
    fun `invalidateCache clears the Stalker bulk cache`() = runTest {
        val repository = newRepository()
        val nowSec = System.currentTimeMillis() / 1000
        var bulkRequestCount = 0
        val api = stubStalkerApi { url ->
            if (url.contains("action=get_simple_data_table")) {
                bulkRequestCount++
                """{ "js": [
                    { "ch_id": "100", "name": "News", "start_timestamp": "${nowSec - 60}", "stop_timestamp": "${nowSec + 60}" }
                ] }"""
            } else null
        }
        val channels = listOf(
            IptvChannel(id = "stalker:stalker1:100", name = "Ch A", logo = null, group = "g", streamUrl = "cmd")
        )

        repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)
        repository.invalidateCache()
        repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)

        assertEquals(2, bulkRequestCount)
    }

    @Test
    fun `empty short EPG results are cached within the TTL`() = runTest {
        val repository = newRepository()
        var simpleRequestCount = 0
        var fallbackRequestCount = 0
        var shortRequestCount = 0
        val api = stubStalkerApi { url ->
            when {
                url.contains("action=get_simple_data_table") -> {
                    simpleRequestCount++
                    """{ "js": [] }"""
                }
                url.contains("action=get_epg_info") -> {
                    fallbackRequestCount++
                    """{ "js": { "data": [] } }"""
                }
                url.contains("action=get_short_epg") -> {
                    shortRequestCount++
                    """{ "js": [] }"""
                }
                else -> null
            }
        }
        val channels = listOf(
            IptvChannel(id = "stalker:stalker1:100", name = "Ch A", logo = null, group = "g", streamUrl = "cmd"),
            IptvChannel(id = "stalker:stalker1:200", name = "Ch B", logo = null, group = "g", streamUrl = "cmd")
        )

        repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)
        repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)

        assertEquals(1, simpleRequestCount)
        assertEquals(1, fallbackRequestCount)
        assertEquals(2, shortRequestCount)
    }

    @Test
    fun `fetchStalkerEpgForActivePortals skips the get_short_epg fallback for large batches`() = runTest {
        // A full-catalog backfill can be thousands of channels - falling back to one
        // get_short_epg request per channel at that scale would hammer the portal, so
        // the fallback is capped and simply yields no EPG for an oversized batch instead.
        val repository = newRepository()
        val shortEpgRequested = mutableListOf<String>()
        val api = stubStalkerApi { url ->
            when {
                url.contains("action=get_simple_data_table") -> """{ "js": [] }"""
                url.contains("action=get_epg_info") -> """{ "js": { "data": [] } }"""
                url.contains("action=get_short_epg") -> {
                    shortEpgRequested += url
                    """{ "js": [] }"""
                }
                else -> null
            }
        }
        val channels = (1..150).map {
            IptvChannel(id = "stalker:stalker1:$it", name = "Ch $it", logo = null, group = "g", streamUrl = "cmd")
        }

        val result = repository.fetchStalkerEpgForActivePortals(mapOf("stalker1" to api), channels)

        assertTrue("Oversized batch must not trigger per-channel fallback requests", shortEpgRequested.isEmpty())
        assertTrue(result.isEmpty())
    }
}
