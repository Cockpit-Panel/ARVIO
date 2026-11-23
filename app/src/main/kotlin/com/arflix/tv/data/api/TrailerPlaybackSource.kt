package com.arflix.tv.data.api

data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null,
    val fallbackVideoUrl: String? = null,
    /** Source capability, not a claim about the currently decoded adaptive rendition. */
    val maxVideoHeight: Int = 0
)
