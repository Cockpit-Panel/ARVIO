package com.arflix.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ImportedCollectionCardDeviceTest(private val device: DeviceType) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun devices() = listOf(DeviceType.TV, DeviceType.PHONE, DeviceType.TABLET)
    }

    @Test fun missingArtworkStillHasVisibleTitleAndCanBeOpened() {
        var opened = false
        val title = "Independent films and international favourites"
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides device) {
                Box(Modifier.padding(24.dp)) {
                    MediaCard(item = MediaItem(id = 7, title = title, mediaType = MediaType.MOVIE,
                        status = "collection:custom_test", image = ""), width = 260.dp,
                        isLandscape = true, showTitle = true, showSubtitle = false,
                        onClick = { opened = true }, modifier = Modifier.testTag("collection"))
                }
            }
        }
        compose.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("collection").performClick()
        compose.runOnIdle { assertTrue(opened) }
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "collection-${device.name}.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
