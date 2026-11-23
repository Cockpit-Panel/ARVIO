package com.arflix.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvGroupOrderTest {
    @Test fun reorderPreservesOtherPlaylists() {
        assertEquals(listOf("other|B", "other|A", "p|B", "p|A"),
            replacePlaylistGroupOrder(listOf("other|B", "other|A", "p|A", "p|B"), listOf("p|B", "p|A"), "p"))
    }

    @Test fun firstOrderAppendsWithoutReplacingOtherPlaylists() {
        assertEquals(listOf("other|B", "other|A", "p|B", "p|A"),
            replacePlaylistGroupOrder(listOf("other|B", "other|A"), listOf("p|B", "p|A"), "p"))
    }

    @Test fun emptyOrderOnlyResetsItsPlaylist() {
        assertEquals(listOf("other|A"), replacePlaylistGroupOrder(listOf("p|A", "other|A"), emptyList(), "p"))
    }
}
