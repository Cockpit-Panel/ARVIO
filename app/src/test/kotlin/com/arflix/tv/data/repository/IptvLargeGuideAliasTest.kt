package com.arflix.tv.data.repository

import com.arflix.tv.data.model.IptvChannel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvLargeGuideAliasTest {
    private val repository = IptvRepository(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    private fun channel(id: String, name: String) =
        IptvChannel(id, name, "https://example.invalid/$id", "test", epgId = id)

    @Suppress("UNCHECKED_CAST")
    private fun match(channels: List<IptvChannel>, display: String, xmlId: String = "xml-schedule"): List<IptvChannel> {
        val build = IptvRepository::class.java.getDeclaredMethod("buildLargeChannelKeyLookup", List::class.java).apply { isAccessible = true }
        val lookup = build.invoke(repository, channels) as Map<String, List<IptvChannel>>
        val resolve = IptvRepository::class.java.getDeclaredMethod("resolveXmlTvChannels", String::class.java, Map::class.java, Map::class.java).apply { isAccessible = true }
        return resolve.invoke(repository, xmlId, mapOf(xmlId to setOf(display)), lookup) as List<IptvChannel>
    }

    @Test fun unprefixedXmlNameMatchesNumericApiIdsAndQualityVariants() {
        val channels = listOf(channel("101", "UK| Sky Sports Main Event HD"), channel("102", "UK| Sky Sports Main Event UHD"))
        assertEquals(setOf("101", "102"), match(channels, "Sky Sports Main Event").map { it.id }.toSet())
    }

    @Test fun ambiguousRegionalNamesAreNotGuessed() {
        val channels = listOf(channel("101", "UK| Sports HD"), channel("102", "US| Sports HD"))
        assertTrue(match(channels, "Sports").isEmpty())
    }

    @Test fun explicitIdsStillMatchAndTimeshiftChannelsStaySeparate() {
        val channels = listOf(channel("101", "UK| Sports HD"), channel("102", "UK| Sports +1 HD"))
        assertEquals(listOf("101"), match(channels, "Sports").map { it.id })
        assertEquals(listOf("102"), match(channels, "Sports +1").map { it.id })
        assertEquals(listOf("102"), match(channels, "", "102").map { it.id })
    }

    @Test fun fullXmlImportRetainsNewDisplayNameAliasesOnLargePlaylists() {
        val channels = (1..10_001).map { channel("$it", "Unrelated $it") } +
            channel("wanted", "UK| Sky Sports Main Event HD")
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        val start = now.minusMinutes(5).format(format)
        val end = now.plusMinutes(30).format(format)
        val xml = """<tv><channel id="xml-schedule"><display-name>Sky Sports Main Event</display-name></channel>
            <programme channel="xml-schedule" start="$start" stop="$end"><title>Live coverage</title></programme></tv>"""
        val parse = IptvRepository::class.java.getDeclaredMethod(
            "parseXmlTvNowNextWithSax", java.io.InputStream::class.java, List::class.java, Function0::class.java,
        ).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val result = parse.invoke(repository, xml.byteInputStream(), channels, {}) as Map<String, com.arflix.tv.data.model.IptvNowNext>
        assertEquals("Live coverage", result["wanted"]?.now?.title)
    }
}
