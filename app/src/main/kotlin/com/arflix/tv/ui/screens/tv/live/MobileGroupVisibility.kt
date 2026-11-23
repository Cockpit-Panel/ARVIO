package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.PlaylistGroupKey

// PIN protection gates selection, not discovery. Only hidden/empty groups leave the picker.
internal fun visibleMobileGroups(
    groups: List<LiveCategory>, hiddenCategoryIds: Set<String>, hiddenGroups: Set<String>,
): List<LiveCategory> = groups.filterNot { category ->
    val name = category.playlistGroupName ?: category.label
    val key = category.playlistId?.takeIf { it.isNotBlank() }?.let { PlaylistGroupKey.build(it, name) }
    category.count <= 0 || category.id in hiddenCategoryIds ||
        key in hiddenGroups || name in hiddenGroups || category.label in hiddenGroups
}.distinctBy { it.id }

internal fun LiveCategory.pendingCategoryUnlock(lockedGroups: Collection<String>, unlockedGroups: Set<String>): String? {
    val key = playlistId?.let { id -> playlistGroupName?.let { PlaylistGroupKey.build(id, it) } } ?: return null
    return key.takeIf { it in lockedGroups && it !in unlockedGroups }
}
