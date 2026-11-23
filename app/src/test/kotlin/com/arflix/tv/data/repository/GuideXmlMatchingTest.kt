package com.arflix.tv.data.repository

import android.app.Application
import com.arflix.tv.data.model.IptvChannel
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class GuideXmlMatchingTest {
    private fun repository() = IptvRepository(RuntimeEnvironment.getApplication(),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    private fun channel(id: String, name: String, epgId: String? = null) = IptvChannel(
        id = id, name = name, epgId = epgId, group = "Test", streamUrl = "https://example.invalid/$id")

    private fun xml(id: String, display: String = ""): String {
        val format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(ZoneOffset.UTC)
        val start = format.format(Instant.now().minusSeconds(300))
        val end = format.format(Instant.now().plusSeconds(300))
        return """<tv><channel id="$id"><display-name>$display</display-name></channel>
            <programme channel="$id" start="$start" stop="$end"><title>Programme</title></programme></tv>"""
    }

    @Test fun qualifiedXmlIdMatchesNamesWithoutDisplayNameOnSmallAndLargeLists() {
        val selected = listOf(channel("hd", "NL| ESPN 1 HD", "902"),
            channel("uhd", "[NL] ESPN 1 UHD", "903"), channel("us", "US| ESPN 1 HD", "904"))
        listOf(0, 10_000).forEach { padding ->
            val channels = selected + List(padding) { channel("pad$it", "Padding $it", "dummy$it") }
            val result = repository().parseXmlTvNowNext(xml("ESPN1.nl").byteInputStream(), channels)
            assertEquals("padding=$padding", setOf("hd", "uhd"), result.keys)
        }
    }

    @Test fun numericXmlIdUsesCountryQualifiedDisplayName() {
        val channels = listOf(channel("nl", "[NL] ESPN 1 HD"), channel("us", "US| ESPN 1 HD"))
        val result = repository().parseXmlTvNowNext(xml("999", "NL: ESPN 1").byteInputStream(), channels)
        assertEquals(setOf("nl"), result.keys)
    }

    @Test fun regionalTimeshiftIsNotMergedIntoMainChannel() {
        val channels = listOf(channel("main", "UK| ITV1 HD"), channel("shift", "UK| ITV1 +1 HD"))
        val result = repository().parseXmlTvNowNext(xml("ITV1.uk").byteInputStream(), channels)
        assertEquals(setOf("main"), result.keys)
    }

    @Test fun missingRegionalFeedDoesNotBorrowAnotherCountrySchedule() {
        val result = repository().parseXmlTvNowNext(xml("ESPN1.nl").byteInputStream(),
            listOf(channel("us", "US| ESPN 1 HD")))
        assertTrue(result.isEmpty())
    }
}
