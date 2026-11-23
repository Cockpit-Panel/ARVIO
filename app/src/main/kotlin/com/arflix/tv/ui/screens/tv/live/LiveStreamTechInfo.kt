package com.arflix.tv.ui.screens.tv.live

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.util.Locale

internal data class LiveStreamTechInfo(
    val resolution: String = "",
    val fps: String = "",
    val videoCodec: String = "",
    val audioInfo: String = "",
    val bitrate: String = "",
)

/** Unknown input-format values stay hidden; network bandwidth is not stream bitrate. */
internal fun liveStreamTechInfo(video: Format?, audio: Format?): LiveStreamTechInfo {
    val channels = when (audio?.channelCount) {
        1 -> "1.0"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> ""
    }
    val audioCodec = when (audio?.sampleMimeType) {
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "eac3"
        MimeTypes.AUDIO_AC3 -> "ac3"
        MimeTypes.AUDIO_AAC -> "aac"
        MimeTypes.AUDIO_MPEG -> "mp3"
        MimeTypes.AUDIO_OPUS -> "opus"
        else -> ""
    }
    // Only show the declared combined average when every present track reports it.
    val formats = listOfNotNull(video, audio)
    val bitrate = if (formats.isNotEmpty() && formats.all { it.averageBitrate > 0 }) {
        String.format(Locale.US, "~%.1f Mbps", formats.sumOf { it.averageBitrate.toLong() } / 1_000_000.0)
    } else ""
    return LiveStreamTechInfo(
        resolution = video?.height?.takeIf { it > 0 }?.let { "${it}p" }.orEmpty(),
        fps = video?.frameRate?.takeIf { it.isFinite() && it > 0f }?.let {
            String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.') + " fps"
        }.orEmpty(),
        videoCodec = when (video?.sampleMimeType) {
            MimeTypes.VIDEO_H264 -> "h264"
            MimeTypes.VIDEO_H265 -> "hevc"
            MimeTypes.VIDEO_AV1 -> "av1"
            MimeTypes.VIDEO_VP9 -> "vp9"
            else -> ""
        },
        audioInfo = listOf(channels, audioCodec).filter { it.isNotEmpty() }.joinToString(" "),
        bitrate = bitrate,
    )
}
