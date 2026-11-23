package com.arflix.tv.ui.screens.settings

import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonType
import org.junit.Assert.assertEquals
import org.junit.Test

class AddonRowActionsTest {

    // Cockpit: panel-provisioned addons cannot be deleted from the app.
    private val delete = if (CockpitSettings.ADDON_MANAGEMENT_ENABLED) listOf(AddonRowAction.DELETE) else emptyList()

    private fun addon(id: String = "addon", type: AddonType = AddonType.CUSTOM, configureUrl: String? = null) = Addon(
        id = id,
        name = "Addon",
        version = "1.0.0",
        description = "",
        isInstalled = true,
        type = type,
        configureUrl = configureUrl
    )

    @Test
    fun `configurable addon gets the settings button between toggle and delete`() {
        assertEquals(
            listOf(AddonRowAction.TOGGLE, AddonRowAction.CONFIGURE) + delete,
            addonRowActions(addon(configureUrl = "https://addon.example.com/configure"))
        )
    }

    @Test
    fun `addon without settings page keeps toggle and delete`() {
        assertEquals(listOf(AddonRowAction.TOGGLE) + delete, addonRowActions(addon()))
    }

    @Test
    fun `preinstalled subtitles addon only has the toggle`() {
        assertEquals(
            listOf(AddonRowAction.TOGGLE),
            addonRowActions(addon(id = "opensubtitles", type = AddonType.SUBTITLE))
        )
    }
}
