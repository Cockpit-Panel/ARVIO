package com.arflix.tv.ui.screens.tv.live

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.util.IPTV_FALLBACK_LOGOS_ENABLED_KEY
import com.arflix.tv.util.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class ChannelLogoFallbackDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingLoadsRealDirectoryArtworkForMissingAndBrokenProviderLogos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = runBlocking { context.settingsDataStore.data.first()[IPTV_FALLBACK_LOGOS_ENABLED_KEY] }
        fun enabled(value: Boolean) = runBlocking {
            context.settingsDataStore.edit { it[IPTV_FALLBACK_LOGOS_ENABLED_KEY] = value }
        }
        val channel = mutableStateOf(IptvChannel(id = "logo-test", name = "[NL] Sports subscription",
            streamUrl = "https://example.test/live", group = "Sports", tvgName = "ESPN"))
        try {
            enabled(false)
            compose.setContent { ChannelLogo(channel.value.enrichForFastStartup(1), 120.dp,
                Modifier.testTag("logo")) }
            compose.waitUntil(5_000) { !hasRedLogoPixels() }
            enabled(true)
            compose.waitUntil(30_000) { hasRedLogoPixels() }
            enabled(false)
            compose.waitUntil(5_000) { !hasRedLogoPixels() }
            compose.runOnIdle { channel.value = channel.value.copy(logo = "http://127.0.0.1:1/broken.png") }
            enabled(true)
            compose.waitUntil(30_000) { hasRedLogoPixels() }
        } finally {
            runBlocking { context.settingsDataStore.edit {
                if (original == null) it.remove(IPTV_FALLBACK_LOGOS_ENABLED_KEY)
                else it[IPTV_FALLBACK_LOGOS_ENABLED_KEY] = original
            } }
        }
    }

    private fun hasRedLogoPixels(): Boolean {
        val pixels = compose.onNodeWithTag("logo").captureToImage().toPixelMap()
        var red = 0
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            val color = pixels[x, y]
            if (color.red > 0.6f && color.green < 0.35f && color.blue < 0.35f) red++
        }
        return red > 30
    }
}
