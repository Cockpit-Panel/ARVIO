package com.arflix.tv.ui.screens.tv

import com.arflix.tv.data.repository.IptvConfig
import com.arflix.tv.data.repository.StalkerPortalEntry
import com.arflix.tv.ui.screens.settings.syncSignature as settingsSyncSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StalkerImportSignatureTest {
    private val portal = StalkerPortalEntry("review", "Portal", "https://example.com", "00:11:22:33:44:55")
    private val before = IptvConfig(stalkerPortals = listOf(portal))

    @Test fun liveImportChangeTriggersRefresh() = assertChanged(portal.copy(importLiveTv = false))

    @Test fun movieImportChangeTriggersRefresh() = assertChanged(portal.copy(importVod = false))

    @Test fun seriesImportChangeTriggersRefresh() = assertChanged(portal.copy(importSeries = false))

    @Test fun legacyMissingFlagsMatchEnabledDefaults() {
        val legacy = before.copy(stalkerPortals = listOf(portal.copy(importLiveTv = null, importVod = null, importSeries = null)))
        assertEquals(before.syncSignature(), legacy.syncSignature())
        assertEquals(before.settingsSyncSignature(), legacy.settingsSyncSignature())
    }

    private fun assertChanged(changed: StalkerPortalEntry) {
        val after = before.copy(stalkerPortals = listOf(changed))
        assertNotEquals(before.syncSignature(), after.syncSignature())
        assertNotEquals(before.settingsSyncSignature(), after.settingsSyncSignature())
    }
}
