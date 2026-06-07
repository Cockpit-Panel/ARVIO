package com.arflix.tv.ui.screens.details

import com.arflix.tv.data.model.StreamSource
import java.util.Locale

internal const val AUTOPLAY_SOURCE_COMPLETION_GRACE_MS = 12_000L
internal const val AUTOPLAY_SOURCE_RECHECK_MS = 350L

private val fourKRegex = Regex("""\b4[kK]\b""")
private val sizeRegex = Regex("""(?i)(\d+(?:[\.,]\d+)?)\s*(TB|GB|MB|KB|B|GiB|MiB|KiB)?""")

/** Score quality from all stream text because addons do not fill the quality field consistently. */
internal fun qualityScoreForAutoPlay(stream: StreamSource): Int {
    val combined = buildString {
        append(stream.quality)
        append(' ')
        append(stream.source)
        append(' ')
        append(stream.addonName)
        stream.behaviorHints?.filename?.let {
            append(' ')
            append(it)
        }
        stream.description?.let {
            append(' ')
            append(it)
        }
    }
    return when {
        combined.contains("2160p", ignoreCase = true) || fourKRegex.containsMatchIn(combined) -> 4
        combined.contains("1080p", ignoreCase = true) -> 3
        combined.contains("720p", ignoreCase = true) -> 2
        combined.contains("480p", ignoreCase = true) -> 1
        else -> 0
    }
}

internal fun bestAutoPlayStream(
    streams: List<StreamSource>,
    minQualityScore: Int
): StreamSource? {
    return streams
        .asSequence()
        .filter { stream ->
            stream.behaviorHints?.notWebReady != true &&
                qualityScoreForAutoPlay(stream) >= minQualityScore
        }
        .sortedWith(
            compareByDescending<StreamSource> { qualityScoreForAutoPlay(it) }
                .thenByDescending { autoPlaySizeBytes(it) }
                .thenByDescending { if (it.behaviorHints?.cached == true) 1 else 0 }
                .thenBy { it.addonName.lowercase() }
                .thenBy { it.source.lowercase() }
        )
        .firstOrNull()
}

/**
 * The source sheet sorts from the visible size string because addon-provided
 * byte hints are inconsistent. Autoplay must do the same or it can choose a
 * tiny 720p source over a visibly larger 4K one.
 */
internal fun autoPlaySizeBytes(stream: StreamSource): Long {
    val raw = stream.size.trim()
    if (raw.isBlank()) return 0L
    val match = sizeRegex.find(raw) ?: return 0L
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
    val unit = match.groupValues.getOrNull(2)?.uppercase(Locale.US).orEmpty()
    val multiplier = when (unit) {
        "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        "GB", "GIB" -> 1024.0 * 1024.0 * 1024.0
        "MB", "MIB" -> 1024.0 * 1024.0
        "KB", "KIB" -> 1024.0
        else -> 1.0
    }
    return (value * multiplier).toLong()
}

internal fun minQualityThreshold(value: String): Int {
    return when (value.trim().lowercase()) {
        "720p", "hd" -> 2
        "1080p", "fullhd", "fhd" -> 3
        "4k", "2160p", "uhd" -> 4
        else -> 0
    }
}

internal fun isAutoPlayableStream(stream: StreamSource): Boolean {
    val url = stream.url?.trim().orEmpty()
    if (!url.startsWith("http", ignoreCase = true)) return false
    return !isPendingDebridStream(stream)
}

internal fun isPendingDebridStream(stream: StreamSource): Boolean {
    val text = listOfNotNull(stream.source, stream.addonName, stream.quality, stream.url, stream.description)
        .joinToString(" ")
        .lowercase()
    return listOf(
        "torrent being downloaded",
        "being downloaded",
        "still downloading",
        "queued",
        "not cached",
        "uncached",
        "cache pending",
        "caching",
        "processing torrent",
        "download in progress"
    ).any { text.contains(it) }
}

internal fun shouldWaitForAutoPlaySources(
    completedAddons: Int,
    totalAddons: Int,
    isLoadingStreams: Boolean,
    hasCandidateStreams: Boolean,
    elapsedMs: Long
): Boolean {
    if (!hasCandidateStreams) return isLoadingStreams
    if (elapsedMs >= AUTOPLAY_SOURCE_COMPLETION_GRACE_MS) return false
    return totalAddons > 0 && completedAddons.coerceAtLeast(0) < totalAddons
}
