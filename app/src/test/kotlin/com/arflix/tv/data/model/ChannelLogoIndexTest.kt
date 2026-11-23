package com.arflix.tv.data.model

import org.junit.Assert.*
import org.junit.Test

class ChannelLogoIndexTest {
    private fun entry(id: String, country: String, vararg names: String) =
        ChannelLogoEntry(id, country, names.toList(), listOf("https://example.invalid/$id.png"))
    private val index = ChannelLogoIndex(listOf(
        entry("ESPN.us", "US", "ESPN"), entry("ESPN.nl", "NL", "ESPN"),
        entry("BBCOne.uk", "UK", "BBC One", "BBC 1"), entry("Channel4.uk", "UK", "Channel 4"),
        entry("Channel4Plus1.uk", "UK", "Channel 4 +1"), entry("Local.us", "US", "NBC Charlotte"),
    ))

    @Test fun exactEpgIdWinsOverProviderName() {
        assertEquals(listOf("https://example.invalid/ESPN.us.png"), index.candidates(" ESPN.US ", "Sports feed"))
    }
    @Test fun providerQualityAndPackagePrefixesDoNotHideKnownLogos() {
        assertEquals(index.candidates(null, "UK | BBC One"), index.candidates(null, "4K| UK-NOWTV| BBC One FHD"))
        assertTrue(index.candidates(null, "FR-NOWTV| BBC One HD").isEmpty())
    }
    @Test fun namesNeedUniqueIdentityAndCountryCanDisambiguate() {
        assertTrue(index.candidates(null, "ESPN").isEmpty())
        assertEquals(listOf("https://example.invalid/ESPN.nl.png"), index.candidates(null, "NL | ESPN FHD"))
        assertEquals(listOf("https://example.invalid/BBCOne.uk.png"), index.candidates(null, "UK: BBC 1 HD"))
    }
    @Test fun bracketedAndSpaceSeparatedCountriesMatchWithoutCrossingRegions() {
        val expected = listOf("https://example.invalid/ESPN.nl.png")
        for (name in listOf("[NL] ESPN HD", "(NL) ESPN UHD", "NL ESPN 4K", "4K| [NL] ESPN FHD")) {
            assertEquals(name, expected, index.candidates(null, name))
        }
        assertTrue(index.candidates(null, "[FR] ESPN HD").isEmpty())
    }
    @Test fun guideNameResolvesProviderLabelsButKeepsCountryAndTimeshift() {
        assertEquals(listOf("https://example.invalid/ESPN.nl.png"),
            index.candidates("12345", "NL| Sports subscription", "ESPN"))
        assertTrue(index.candidates(null, "NL| Sports subscription", "US| ESPN").isEmpty())
        assertTrue(index.candidates(null, "NL| Sports subscription", "ESPN +1").isEmpty())
    }
    @Test fun allIsoCountriesAreRecognized() {
        val local = ChannelLogoIndex(listOf(entry("News.my", "MY", "News"), entry("News.us", "US", "News")))
        assertEquals(listOf("https://example.invalid/News.my.png"), local.candidates(null, "[MY] News HD"))
        assertTrue(local.candidates(null, "News HD").isEmpty())
    }
    @Test fun ambiguousRegionsCanOnlyUseTheirSharedArtwork() {
        val shared = "https://example.invalid/shared.png"
        val local = ChannelLogoIndex(listOf(
            entry("Sports.us", "US", "Sports").copy(urls = listOf(shared, "us-only")),
            entry("Sports.nl", "NL", "Sports").copy(urls = listOf("nl-only", shared)),
        ))
        assertEquals(listOf(shared), local.candidates(null, "4K| Sports UHD"))
        assertEquals(listOf("nl-only", shared), local.candidates(null, "NL| Sports UHD"))
    }
    @Test fun neverDropChannelNumbersTimeShiftsOrRegions() {
        assertEquals(listOf("https://example.invalid/Channel4Plus1.uk.png"), index.candidates(null, "Channel 4 +1 HD"))
        for (name in listOf("Channel 5", "BBC One +1", "NBC", "NBC Boston", "Unknown", "FR | BBC One")) {
            assertTrue(name, index.candidates(null, name).isEmpty())
        }
    }
}
