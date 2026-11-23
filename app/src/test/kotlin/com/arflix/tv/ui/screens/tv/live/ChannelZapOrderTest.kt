package com.arflix.tv.ui.screens.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelZapOrderTest {
    @Test fun forwardThenReverseReturnsToSameChannelInCategoryOrder() {
        val order = ChannelZapOrder()
        order.update(listOf("provider:800", "provider:12", "provider:900"))
        val next = order.next("provider:800", null, 1, true)
        assertEquals("provider:12", next)
        assertEquals("provider:800", order.next(next, null, -1, true))
    }

    @Test fun recentlyWatchedReorderingDoesNotChangeViewingOrder() {
        val order = ChannelZapOrder()
        order.update(listOf("a", "b", "c"))
        assertEquals("b", order.next("a", null, 1, true))
        order.update(listOf("b", "a", "c"))
        assertEquals("a", order.next("b", null, -1, true))
        assertEquals("c", order.next("b", null, 1, true))
    }

    @Test fun unknownCurrentChannelNeverJumpsToBeginning() {
        val order = ChannelZapOrder()
        order.update(listOf("a", "b", "c"))
        assertNull(order.next("outside-category", null, 1, true))
        assertNull(order.next(null, null, -1, true))
    }

    @Test fun exactVariantInFavouritesWinsOverCollapsedRepresentative() {
        val order = ChannelZapOrder()
        order.update(listOf("hd", "uhd", "other"))
        assertEquals("other", order.next("uhd", "hd", 1, true))
        assertEquals("other", order.next("alternate-source", "uhd", 1, true))
    }

    @Test fun partialPageDoesNotWrapToWrongEndAndNewPageExtendsOrder() {
        val order = ChannelZapOrder()
        order.update(listOf("a", "b"))
        assertNull(order.next("b", null, 1, false))
        assertNull(order.next("a", null, -1, false))
        order.update(listOf("a", "b", "c", "d"))
        assertEquals("c", order.next("b", null, 1, true))
        assertEquals("b", order.next("c", null, -1, true))
        assertEquals("a", order.next("d", null, 1, true))
        assertEquals("d", order.next("a", null, -1, true))
    }

    @Test fun removedChannelsCannotBeTunedAndEmptyListIsSafe() {
        val order = ChannelZapOrder()
        order.update(listOf("a", "b", "c"))
        order.update(listOf("a", "c"))
        assertEquals("c", order.next("a", null, 1, true))
        order.update(emptyList())
        assertNull(order.next("a", null, 1, true))
    }

    @Test fun largeListStaysReversibleAcrossRepeatedKeyDirections() {
        val order = ChannelZapOrder()
        order.update((1..55_000).map { "channel-$it" })
        var current = "channel-28000"
        repeat(250) { current = requireNotNull(order.next(current, null, 1, true)) }
        repeat(250) { current = requireNotNull(order.next(current, null, -1, true)) }
        assertEquals("channel-28000", current)
    }
}
