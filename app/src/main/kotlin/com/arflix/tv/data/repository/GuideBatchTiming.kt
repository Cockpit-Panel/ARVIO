package com.arflix.tv.data.repository

/** Includes queue time behind the shared two-connection provider budget. */
internal fun shortGuideBatchTimeoutMs(streamCount: Int): Long =
    (streamCount.coerceAtLeast(0).toLong() * 1_000L + 4_000L).coerceIn(12_000L, 180_000L)
