package com.arflix.tv.data.model

data class AutoplayLimits(
    val maximumQuality: String = "Unlimited",
    val maximumSizeGb: Int = 0
) {
    val qualityScore: Int get() = when (normalizeQuality(maximumQuality)) {
        "720p" -> 2
        "1080p" -> 3
        "4K" -> 4
        else -> Int.MAX_VALUE
    }
    val sizeBytes: Long get() = normalizeSizeGb(maximumSizeGb).toLong() * 1024 * 1024 * 1024

    companion object {
        val qualityOptions = listOf("Unlimited", "720p", "1080p", "4K")
        val sizeOptionsGb = listOf(0, 1, 2, 3, 5, 10, 15, 20, 30, 50)

        fun normalizeQuality(value: String?): String = when (value?.trim()?.lowercase()) {
            "720p", "hd" -> "720p"
            "1080p", "fhd", "fullhd" -> "1080p"
            "4k", "2160p", "uhd" -> "4K"
            else -> "Unlimited"
        }

        fun normalizeSizeGb(value: Int): Int = value.coerceIn(0, 1024)
    }
}
