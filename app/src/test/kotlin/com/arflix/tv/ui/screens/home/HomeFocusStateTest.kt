package com.arflix.tv.ui.screens.home

import androidx.compose.runtime.saveable.SaverScope
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class HomeFocusStateTest {
    @Test fun returnToStartClearsEveryRowAndItsTitleAnchor() {
        val rows = listOf(row(1, 2, 3).copy(id = "a"), row(4, 5, 6).copy(id = "b"))
        val state = HomeFocusState().apply {
            currentItemIndex = 2
            recordSelection(rows)
            currentRowIndex = 1
            currentItemIndex = 1
            recordSelection(rows)
            isSidebarFocused = true
        }
        state.returnToStart(rows, 100)
        assertEquals(0, state.currentRowIndex)
        assertEquals(0, state.currentItemIndex)
        assertEquals("a", state.preferredCategoryId)
        assertEquals(mapOf("a" to 0), state.rowItemIndicesByCategoryId)
        assertEquals(mapOf("a" to "MOVIE-1"), state.rowItemKeysByCategoryId)
        assertFalse(state.isSidebarFocused)
        assertFalse(state.isHeroFocused)
        assertTrue(state.userHasNavigated)
        assertEquals(100L, state.lastNavEventTime)
        assertEquals(1, state.scrollResetRequest)
    }

    @Test fun returnToBillboardAndNewNavigationSurviveSavedState() {
        val rows = listOf(row(1, 2, 3))
        val state = HomeFocusState().apply { returnToStart(rows, 100, billboard = true) }
        assertTrue(state.isHeroFocused)
        state.isHeroFocused = false
        state.currentItemIndex = 2
        state.recordSelection(rows)
        val saved = with(HomeFocusState.Saver) { SaverScope { true }.save(state)!! }
        val restored = HomeFocusState.Saver.restore(saved)!!
        restored.reconcile(rows)
        assertEquals(2, restored.currentItemIndex)
        assertEquals(1, restored.scrollResetRequest)
    }

    @Test fun viewAllSurvivesPagingShrinkingAndSavedState() {
        val more = mapOf("row" to true)
        val initial = listOf(row(1, 2, 3))
        val state = HomeFocusState().apply {
            reconcile(initial, more)
            currentItemIndex = 3
            recordSelection(initial)
        }
        val saved = with(HomeFocusState.Saver) { SaverScope { true }.save(state)!! }
        val restored = HomeFocusState.Saver.restore(saved)!!
        restored.reconcile(listOf(row(1)), more)
        assertEquals(1, restored.currentItemIndex)
        restored.reconcile(listOf(row(1, 2, 3, 4, 5)), more)
        assertEquals(5, restored.currentItemIndex)
        assertEquals(2, restored.restoredItemIndex(row(1, 2), 5))
    }

    @Test fun viewAllDisappearingDoesNotEraseAnchorButUserNavigationDoes() {
        val more = mapOf("row" to true)
        val rows = listOf(row(1, 2, 3))
        val state = HomeFocusState().apply {
            reconcile(rows, more)
            currentItemIndex = 3
            recordSelection(rows)
        }
        state.reconcile(rows, emptyMap())
        assertEquals(2, state.currentItemIndex)
        state.reconcile(rows, more)
        assertEquals(3, state.currentItemIndex)
        state.currentItemIndex = 1
        state.recordSelection(rows)
        state.reconcile(listOf(row(3, 2, 1, 4)), more)
        assertEquals(1, state.currentItemIndex)
        assertEquals("MOVIE-2", state.rowItemKeysByCategoryId["row"])
    }

    @Test fun viewAllExcludesSpecialRowsAndUsesRealItemCount() {
        val titles = row(*(1..15).toList().toIntArray())
        assertTrue(homeRowSupportsViewAll(titles, false))
        assertFalse(homeRowSupportsViewAll(row(1, 2), false))
        assertFalse(homeRowSupportsViewAll(titles.copy(id = "continue_watching"), true))
        assertFalse(homeRowSupportsViewAll(titles.copy(id = "collection_row_franchise"), true))
        assertFalse(homeRowSupportsViewAll(row(1).copy(items = listOf(row(1).items[0].copy(status = "iptv:1"))), true))
        val mixed = row(1, 2).let { it.copy(items = it.items + it.items[0].copy(isPlaceholder = true)) }
        assertEquals(2, homeRowViewAllIndex(mixed, true))
    }

    private fun row(vararg ids: Int) = Category("row", "Row", ids.map { MediaItem(it, "Title $it", mediaType = MediaType.MOVIE) })

    @Test fun latestInputWinsOverRefreshAndReorder() {
        val rows = listOf(row(1, 2, 3, 4))
        val state = HomeFocusState().apply { reconcile(rows) }
        state.currentItemIndex = 2
        state.recordSelection(rows)
        state.reconcile(listOf(row(9, 4, 3, 2, 1)))
        assertEquals(2, state.currentItemIndex)
        state.currentItemIndex = 3
        state.recordSelection(listOf(row(9, 4, 3, 2, 1)))
        state.reconcile(listOf(row(1, 2, 3, 4)))
        assertEquals(1, state.currentItemIndex)
    }

    @Test fun temporaryMissingItemsDoNotEraseTheAnchor() {
        val state = HomeFocusState().apply {
            currentItemIndex = 3
            recordSelection(listOf(row(1, 2, 3, 4)))
        }
        state.reconcile(listOf(row()))
        state.reconcile(listOf(row(1)))
        state.reconcile(listOf(row(4, 3, 2, 1)))
        assertEquals(0, state.currentItemIndex)
        assertEquals("MOVIE-4", state.rowItemKeysByCategoryId["row"])
    }

    @Test fun restoredStateKeepsIdentityAndNavigationIntent() {
        val state = HomeFocusState().apply {
            currentItemIndex = 1
            userHasNavigated = true
            isSidebarFocused = true
            recordSelection(listOf(row(1, 2, 3)))
        }
        val saved = with(HomeFocusState.Saver) { SaverScope { true }.save(state)!! }
        val restored = HomeFocusState.Saver.restore(saved)!!
        restored.reconcile(listOf(row(9).copy(id = "new"), row(3, 1, 2)))
        assertEquals(1, restored.currentRowIndex)
        assertEquals(2, restored.currentItemIndex)
        assertTrue(restored.userHasNavigated)
        assertTrue(restored.isSidebarFocused)
    }

    @Test fun navigationAndRenderingIgnoreInterleavedPlaceholdersEqually() {
        val real = row(1, 2).items
        val skeleton = real[0].copy(id = -1, isPlaceholder = true)
        assertEquals(real, navigableHomeItems(listOf(skeleton, real[0], skeleton, real[1])))
        assertEquals(listOf(skeleton), navigableHomeItems(listOf(skeleton)))
    }

    @Test fun returningToAnUpdatedRowUsesItsTitleInsteadOfItsOldIndex() {
        val state = HomeFocusState().apply {
            currentItemIndex = 1
            recordSelection(listOf(row(1, 2, 3)))
        }
        assertEquals(2, state.restoredItemIndex(row(3, 1, 2), 1))
    }
}
