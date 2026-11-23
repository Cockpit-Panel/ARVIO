package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.repository.IptvConfig

/**
 * When the Favorite TV row may go back to the network for guide data: only while the saved
 * guide has no current programme for one of its channels, and then at most every [retryMs].
 * A saved guide normally covers days ahead, and for an XMLTV source a network refresh means
 * downloading the whole guide.
 */
internal fun favoriteTvGuideNeedsNetwork(
    channelIds: Set<String>,
    saved: Map<String, IptvNowNext>?,
    lastNetworkMs: Long,
    nowMs: Long,
    retryMs: Long
): Boolean {
    if (channelIds.none { saved?.get(it)?.now == null }) return false
    return lastNetworkMs == 0L || nowMs - lastNetworkMs >= retryMs
}

/** Moving along the row re-reads the saved guide at most once per [intervalMs]. */
internal fun favoriteTvFocusRefreshDue(lastRefreshMs: Long, nowMs: Long, intervalMs: Long): Boolean =
    lastRefreshMs == 0L || nowMs - lastRefreshMs >= intervalMs

/**
 * Which IPTV sources the Favorite TV row is built from: the enabled playlists and Stalker
 * portals. A change means the row may show channels of a source that was just switched off.
 */
internal fun iptvFavoriteSourceKey(config: IptvConfig): String = buildList {
    config.playlists.filter { it.enabled }.forEach { add("p:${it.id}|${it.m3uUrl}") }
    config.stalkerPortals.filter { it.enabled }.forEach { add("s:${it.id}|${it.portalUrl}|${it.macAddress}") }
}.joinToString("\n")
