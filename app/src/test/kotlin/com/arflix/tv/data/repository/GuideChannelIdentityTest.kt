package com.arflix.tv.data.repository

import org.junit.Assert.*
import org.junit.Test

class GuideChannelIdentityTest {
    @Test fun xmlIdsMatchProviderQualityVariants() {
        val expected = GuideChannelIdentity.key("ESPN1.nl")
        listOf("NL| ESPN 1 HD", "[NL] ESPN 1 FHD", "(NL) ESPN 1 UHD", "NL ESPN 1 8K", "NL: ESPN 1")
            .forEach { assertEquals(it, expected, GuideChannelIdentity.key(it)) }
        assertEquals("guide-region:nl:espn1", expected)
    }
    @Test fun equivalentCountryCodesMatch() {
        assertEquals(GuideChannelIdentity.key("BBC One.uk"), GuideChannelIdentity.key("GB| BBC One HD"))
        assertEquals(GuideChannelIdentity.key("ESPN.us"), GuideChannelIdentity.key("USA: ESPN FHD"))
    }
    @Test fun differentRegionsNeverShareAnIdentity() {
        assertNotEquals(GuideChannelIdentity.key("ESPN.us"), GuideChannelIdentity.key("ESPN.nl"))
    }
    @Test fun timeshiftAndNumberedChannelsStaySeparate() {
        assertNotEquals(GuideChannelIdentity.key("ITV1.uk"), GuideChannelIdentity.key("UK| ITV1 +1 HD"))
        assertNotEquals(GuideChannelIdentity.key("ESPN1.nl"), GuideChannelIdentity.key("ESPN2.nl"))
    }
    @Test fun unknownOrAbsentCountryIsNotGuessed() {
        listOf("ESPN HD", "12345", "Sky Sports", "Channel.example", "VIP| ESPN", "NL| HD")
            .forEach { assertNull(it, GuideChannelIdentity.key(it)) }
    }
    @Test fun unicodeNamesArePreserved() {
        assertEquals(GuideChannelIdentity.key("FR| Équipe HD"), GuideChannelIdentity.key("Équipe.fr"))
        assertNotEquals(GuideChannelIdentity.key("FR| Équipe"), GuideChannelIdentity.key("FR| quipe"))
    }
}
