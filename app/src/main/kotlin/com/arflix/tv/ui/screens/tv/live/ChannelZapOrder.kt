package com.arflix.tv.ui.screens.tv.live

/** A viewing session keeps its order even when tuning updates Recently Watched. */
internal class ChannelZapOrder {
    private var lastAvailable: List<String>? = null
    private var ids: List<String> = emptyList()
    private var positions: Map<String, Int> = emptyMap()

    fun update(available: List<String>) {
        if (available === lastAvailable) return
        lastAvailable = available
        val allowed = available.toHashSet()
        val retained = ids.filter { it in allowed }
        val retainedSet = retained.toHashSet()
        ids = retained + available.filter { it !in retainedSet }.distinct()
        positions = ids.withIndex().associate { it.value to it.index }
    }

    fun next(currentId: String?, displayId: String?, delta: Int, complete: Boolean): String? {
        if (ids.size < 2 || delta == 0) return null
        // An explicitly selected source wins over its collapsed display representative.
        val current = positions[currentId] ?: positions[displayId] ?: return null
        val target = current + delta
        if (!complete && target !in ids.indices) return null
        return ids[Math.floorMod(target, ids.size)]
    }
}
