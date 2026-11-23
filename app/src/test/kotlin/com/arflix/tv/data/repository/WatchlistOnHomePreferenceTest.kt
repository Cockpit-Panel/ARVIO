package com.arflix.tv.data.repository

import com.arflix.tv.ui.screens.settings.SettingsUiState
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class WatchlistOnHomePreferenceTest {
    private val gson = Gson()

    @Test fun `home watchlist is opt in`() {
        assertFalse(SettingsUiState().watchlistOnHome)
    }

    @Test fun `older cloud payloads preserve the local choice`() {
        assertNull(CloudSyncRepository.CloudProfileSettings().watchlistOnHome)
        assertNull(gson.fromJson("{}", CloudSyncRepository.CloudProfileSettings::class.java).watchlistOnHome)
    }

    @Test fun `both choices survive cloud round trips`() {
        for (enabled in listOf(true, false)) {
            val settings = CloudSyncRepository.CloudProfileSettings(watchlistOnHome = enabled)
            val restored = gson.fromJson(gson.toJson(settings), CloudSyncRepository.CloudProfileSettings::class.java)
            assertEquals(enabled, restored.watchlistOnHome)
        }
    }
}
