package com.arflix.tv.ui.screens.tv.live

import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/*
 * Many broadcast IPTV feeds (re-encoded DVB channels) never send an IDR frame: every keyframe
 * is a plain I-frame. ExoPlayer only starts video on an IDR by default, so such a channel
 * buffers audio until the buffer is full and then stalls ("stuck buffering"). VLC-based apps
 * start on any I-frame, which is why the same channels play there.
 */

/**
 * IPTV HLS is prepared from its first segment, not from the playlist alone. "Chunkless"
 * preparation builds the track list from the playlist's CODECS attribute, which names only video
 * and audio, so subtitles muxed into the segments (DVB subtitles on re-broadcast channels) never
 * appear. Media3 already falls back to reading a segment when a variant lists two audio codecs,
 * which is why some channels of one provider had subtitles and others did not.
 */
internal const val IPTV_HLS_CHUNKLESS_PREPARATION = false

/**
 * HLS extractors for IPTV that also start video on a non-IDR I-frame, and that keep every DVB
 * subtitle language of a channel ([IptvHlsExtractorFactory]).
 */
internal fun iptvHlsExtractorFactory(): HlsExtractorFactory =
    IptvHlsExtractorFactory(
        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES,
        exposeCea608WhenMissingDeclarations = true,
    )

/** Progressive (raw MPEG-TS) extractors for IPTV that also start video on a non-IDR I-frame. */
internal fun iptvExtractorsFactory(): ExtractorsFactory =
    DefaultExtractorsFactory()
        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
