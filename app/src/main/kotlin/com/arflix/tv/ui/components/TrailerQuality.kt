package com.arflix.tv.ui.components

import androidx.media3.common.TrackSelectionParameters

/** Preview size must not limit decoding to a small card's dimensions. Keep ABR fallback. */
internal fun TrackSelectionParameters.forFullHdTrailer(): TrackSelectionParameters = buildUpon()
    .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, true)
    .setMaxVideoSize(1920, 1080)
    .build()
