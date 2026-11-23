package com.arflix.tv.ui.screens.player

import com.arflix.tv.data.model.AutoplayLimits
import com.arflix.tv.data.model.StreamBehaviorHints
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.repository.CloudSyncRepository.CloudProfileSettings
import com.arflix.tv.ui.screens.details.bestAutoPlayStream
import com.arflix.tv.ui.screens.settings.SettingsUiState
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class AutoplayLimitsTest {
    @Test fun sequentialSearchContinuesUntilProviderMeetsAutoplayLimits() {
        val candidates = SequentialStreamCandidates(3, AutoplayLimits(maximumSizeGb = 2))
        assertFalse(candidates.accept(emptyList()))
        assertFalse(candidates.accept(listOf(stream("720p"))))
        assertFalse(candidates.accept(listOf(stream("1080p", "10 GB"))))
        val playable = stream("1080p", "1 GB")
        assertTrue(candidates.accept(listOf(playable)))
        assertEquals(listOf(playable), candidates.streams)
    }

    @Test fun sequentialSearchRetainsRejectedSourcesForManualSelection() {
        val candidates = SequentialStreamCandidates(3, AutoplayLimits())
        val low = stream("720p")
        assertFalse(candidates.accept(listOf(low)))
        assertFalse(candidates.accept(emptyList()))
        assertEquals(listOf(low), candidates.streams)
    }

    private fun stream(quality: String = "1080p", size: String = "2 GB") = StreamSource(
        source = "Example", addonName = "Example", addonId = "example", quality = quality,
        size = size, url = "https://example.com/video"
    )

    @Test fun defaultsKeepExistingAutoplayBehaviour() {
        val source = stream("Unknown", "")
        assertEquals(listOf(source), eligiblePlayerAutoplayStreams(listOf(source), 0))
        assertEquals("Unlimited", SettingsUiState().autoPlayMaxQuality)
        assertEquals(0, SettingsUiState().autoPlayMaxSizeGb)
    }

    @Test fun fullHdCapExcludes4k8kAndUnknownButAllowsLowerQuality() {
        val sources = listOf("720p", "1080p", "4K", "8K", "Unknown").map { stream(it) }
        assertEquals(sources.take(2), eligiblePlayerAutoplayStreams(sources, 0, AutoplayLimits("1080p")))
    }

    @Test fun minimumAndMaximumBothApply() {
        val sources = listOf("720p", "1080p", "4K").map { stream(it) }
        assertEquals(listOf(sources[1]), eligiblePlayerAutoplayStreams(sources, 3, AutoplayLimits("1080p")))
        assertTrue(eligiblePlayerAutoplayStreams(sources, 4, AutoplayLimits("1080p")).isEmpty())
    }

    @Test fun sizeBoundaryIsInclusiveAndUnknownOrMalformedSizesAreRejected() {
        val sources = listOf("1 GB", "2 GB", "2.01 GB", "", "Unknown", "-1 GB", "2 bananas").map { stream(size = it) }
        assertEquals(sources.take(2), eligiblePlayerAutoplayStreams(sources, 0, AutoplayLimits(maximumSizeGb = 2)))
    }

    @Test fun supportsDecimalCommaMebibytesAndByteHints() {
        val sources = listOf(stream(size = "1,5 GiB"), stream(size = "2048 MiB"),
            stream(size = "").copy(behaviorHints = StreamBehaviorHints(videoSize = 2L * 1024 * 1024 * 1024)))
        assertEquals(sources, eligiblePlayerAutoplayStreams(sources, 0, AutoplayLimits(maximumSizeGb = 2)))
    }

    @Test fun roundedSizeCannotHideLargerByteHint() {
        val source = stream().copy(behaviorHints = StreamBehaviorHints(videoSize = 2L * 1024 * 1024 * 1024 + 1))
        assertTrue(eligiblePlayerAutoplayStreams(listOf(source), 0, AutoplayLimits(maximumSizeGb = 2)).isEmpty())
    }

    @Test fun qualityCanComeFromFilename() {
        val source = stream("Unknown").copy(behaviorHints = StreamBehaviorHints(filename = "Movie.2160p.mkv"))
        assertTrue(eligiblePlayerAutoplayStreams(listOf(source), 0, AutoplayLimits("1080p")).isEmpty())
        assertEquals(listOf(source), eligiblePlayerAutoplayStreams(listOf(source), 0, AutoplayLimits("4K")))
    }

    @Test fun limitedPlannerChoosesBestEligibleNotLargestSource() {
        val hd = stream("720p", "1 GB")
        val fhd = stream("1080p", "2 GB")
        val tooLarge = stream("1080p", "10 GB")
        val uhd = stream("4K", "2 GB")
        assertEquals(fhd, bestAutoPlayStream(listOf(uhd, tooLarge, hd, fhd), 0, AutoplayLimits("1080p", 2)))
    }

    @Test fun noMatchWaitsForLateSourcesThenRequiresManualChoice() {
        val sources = listOf(stream("4K"))
        val limits = AutoplayLimits("1080p", 2)
        assertEquals(PlayerAutoplayAvailability.SEARCHING, playerAutoplayAvailability(sources, 0, true, false, limits))
        assertEquals(PlayerAutoplayAvailability.NO_MATCH, playerAutoplayAvailability(sources, 0, false, false, limits))
        assertEquals(PlayerAutoplayAvailability.READY, playerAutoplayAvailability(sources + stream(), 0, false, false, limits))
        assertEquals(PlayerAutoplayAvailability.SELECTED, playerAutoplayAvailability(sources, 0, false, true, limits))
    }

    @Test fun profileLimitsSurviveCloudRoundTripIndependently() {
        val original = mapOf(
            "family" to CloudProfileSettings(autoPlayMaxQuality = "1080p", autoPlayMaxSizeGb = 3),
            "owner" to CloudProfileSettings(autoPlayMaxQuality = "Unlimited", autoPlayMaxSizeGb = 0)
        )
        val gson = Gson()
        val json = gson.toJsonTree(original).asJsonObject
        val family = gson.fromJson(json["family"], CloudProfileSettings::class.java)
        val owner = gson.fromJson(json["owner"], CloudProfileSettings::class.java)
        assertEquals("1080p", family.autoPlayMaxQuality)
        assertEquals(3, family.autoPlayMaxSizeGb)
        assertEquals("Unlimited", owner.autoPlayMaxQuality)
        assertEquals(0, owner.autoPlayMaxSizeGb)
    }

    @Test fun legacyCloudDoesNotSupplyAnImplicitReset() {
        val restored = Gson().fromJson("{}", CloudProfileSettings::class.java)
        assertNull(restored.autoPlayMaxQuality)
        assertNull(restored.autoPlayMaxSizeGb)
    }

    @Test fun normalizationIsBoundedAndSizeMultiplicationDoesNotOverflow() {
        assertEquals("1080p", AutoplayLimits.normalizeQuality(" FHD "))
        assertEquals("Unlimited", AutoplayLimits.normalizeQuality(null))
        assertEquals(0L, AutoplayLimits(maximumSizeGb = -1).sizeBytes)
        assertEquals(1024L * 1024 * 1024 * 1024, AutoplayLimits(maximumSizeGb = Int.MAX_VALUE).sizeBytes)
    }
}
