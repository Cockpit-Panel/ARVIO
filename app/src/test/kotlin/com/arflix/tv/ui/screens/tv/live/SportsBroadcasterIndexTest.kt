package com.arflix.tv.ui.screens.tv.live

import org.junit.Assert.*
import org.junit.Test

class SportsBroadcasterIndexTest {
    @Test fun repeatedLookupsKeepAllVariantsAndOriginalProviderOrder() {
        val labels = (0 until 55_000).map { i ->
            Triple("provider:$i", if (i % 2 == 0) "ESPN HD" else "TNT Sports 1 UHD", if (i % 7 == 0) "Hidden" else "Sports")
        }
        var scans = 0
        val index = SportsBroadcasterIndex.build(SportsBroadcasterIndexKey("profile", "all", 1L, setOf("Hidden"))) { visitor ->
            scans++
            labels.forEach { (id, name, group) -> visitor(id, name, group) }
        }
        val names = linkedSetOf(sportsChannelKey("TNT Sports 1"), sportsChannelKey("ESPN"))
        val expected = labels.filter { it.third != "Hidden" && sportsChannelKey(it.second) in names }.map { it.first }
        assertEquals(expected, index.matchingIds(names))
        assertEquals(expected, index.matchingIds(names.toList().asReversed().toSet()))
        assertEquals(1, scans)
        assertTrue(expected.size > 45_000)
        assertTrue(index.matchingIds(setOf("unknown")).isEmpty())
    }
}
