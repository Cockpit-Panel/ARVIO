package com.arflix.tv.data.repository

internal fun replacePlaylistGroupOrder(saved: List<String>, updated: List<String>, playlistId: String): List<String> {
    val prefix = "$playlistId|"
    val next = mutableListOf<String>()
    var inserted = false
    for (key in saved) {
        if (key.startsWith(prefix)) {
            if (!inserted) {
                next.addAll(updated)
                inserted = true
            }
        } else {
            next.add(key)
        }
    }
    if (!inserted) next.addAll(updated)
    return next.distinct()
}
