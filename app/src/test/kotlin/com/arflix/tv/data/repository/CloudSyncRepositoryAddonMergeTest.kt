package com.arflix.tv.data.repository

import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncRepositoryAddonMergeTest {
    @Test
    fun `cloud addons win when ids match`() {
        val local = addon(id = "flix", name = "Local Flix")
        val cloud = addon(id = "flix", name = "Cloud Flix", isEnabled = false)

        val (merged, preserved) = mergeCloudAddonsPreservingLocalDirectAddons(
            cloudAddons = listOf(cloud),
            localAddons = listOf(local)
        )

        assertFalse(preserved)
        assertEquals(listOf("Cloud Flix"), merged.map { it.name })
        assertEquals(false, merged.single().isEnabled)
    }

    @Test
    fun `missing local custom addon is preserved from stale cloud payload`() {
        val cloud = addon(id = "torrentio", name = "Torrentio")
        val localFlix = addon(id = "flix", name = "FlixStreams")

        val (merged, preserved) = mergeCloudAddonsPreservingLocalDirectAddons(
            cloudAddons = listOf(cloud),
            localAddons = listOf(localFlix)
        )

        assertTrue(preserved)
        assertEquals(listOf("torrentio", "flix"), merged.map { it.id })
    }

    @Test
    fun `missing subtitle addon is not preserved`() {
        val localSubtitle = addon(id = "subtitle-only", name = "Subtitle Only", type = AddonType.SUBTITLE)

        val (merged, preserved) = mergeCloudAddonsPreservingLocalDirectAddons(
            cloudAddons = emptyList(),
            localAddons = listOf(localSubtitle)
        )

        assertFalse(preserved)
        assertEquals(emptyList<Addon>(), merged)
    }

    private fun addon(
        id: String,
        name: String,
        type: AddonType = AddonType.CUSTOM,
        isEnabled: Boolean = true
    ) = Addon(
        id = id,
        name = name,
        version = "1.0.0",
        description = "",
        isInstalled = true,
        isEnabled = isEnabled,
        type = type,
        url = "https://example.com/$id/manifest.json"
    )
}
