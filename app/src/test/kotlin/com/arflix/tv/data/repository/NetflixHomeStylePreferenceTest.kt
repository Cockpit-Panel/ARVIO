package com.arflix.tv.data.repository

import com.arflix.tv.data.repository.CloudSyncRepository.CloudProfileSettings
import com.arflix.tv.ui.screens.settings.SettingsUiState
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class NetflixHomeStylePreferenceTest {
    private val gson = Gson()

    @Test
    fun netflixHomeIsTheDefault() {
        assertThat(SettingsUiState().netflixHomeStyle).isTrue()
    }

    @Test
    fun missingCloudFieldDoesNotRequestALocalOverwrite() {
        assertThat(CloudProfileSettings().netflixHomeStyle).isNull()
        assertThat(gson.fromJson("{}", CloudProfileSettings::class.java).netflixHomeStyle).isNull()
    }

    @Test
    fun bothChoicesSurviveCloudRoundTrip() {
        for (enabled in listOf(true, false)) {
            val restored = gson.fromJson(
                gson.toJson(CloudProfileSettings(netflixHomeStyle = enabled)),
                CloudProfileSettings::class.java,
            )
            assertThat(restored.netflixHomeStyle).isEqualTo(enabled)
        }
    }
}
