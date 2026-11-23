package com.arflix.tv.ui.screens.tv

import com.arflix.tv.data.model.IptvNowNext

internal fun hasUsefulVisibleGuideData(item: IptvNowNext?, nowMs: Long): Boolean {
    if (item == null) return false
    if (sequenceOf(item.next, item.later).filterNotNull().any { it.endUtcMillis > nowMs } ||
        item.upcoming.any { it.endUtcMillis > nowMs }) return true
    return item.now?.let { it.endUtcMillis - nowMs > 45L * 60_000L } == true
}
