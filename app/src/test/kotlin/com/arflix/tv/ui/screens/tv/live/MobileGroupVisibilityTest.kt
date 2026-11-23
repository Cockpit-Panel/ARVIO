package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.PlaylistGroupKey
import org.junit.Assert.*
import org.junit.Test

class MobileGroupVisibilityTest {
    private val locked = LiveCategory("locked", "Locked group", 5, CategoryIcon.Grid,
        playlistId = "provider", playlistGroupName = "Locked group")
    private val key = PlaylistGroupKey.build("provider", "Locked group")

    @Test fun lockedGroupRemainsVisibleAndRequiresPinBeforeEntry() {
        assertEquals(listOf(locked), visibleMobileGroups(listOf(locked), emptySet(), emptySet()))
        assertEquals(key, locked.pendingCategoryUnlock(setOf(key), emptySet()))
        assertNull(locked.pendingCategoryUnlock(setOf(key), setOf(key)))
    }
    @Test fun hiddenGroupsStayHiddenRegardlessOfLockState() {
        for (hidden in listOf(key, locked.label)) {
            assertTrue(visibleMobileGroups(listOf(locked), emptySet(), setOf(hidden)).isEmpty())
        }
        assertTrue(visibleMobileGroups(listOf(locked), setOf(locked.id), emptySet()).isEmpty())
    }
    @Test fun emptyAndDuplicateGroupsAreNotOffered() {
        assertEquals(listOf(locked), visibleMobileGroups(listOf(locked, locked, locked.copy(id = "empty", count = 0)), emptySet(), emptySet()))
    }
}
