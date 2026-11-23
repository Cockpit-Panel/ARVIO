package com.arflix.tv.ui.screens.tv.live

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.arflix.tv.data.model.PlaylistGroupKey
import com.arflix.tv.ui.screens.profile.PinEntryDialog
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Rule
import org.junit.Test

class MobileLockedGroupDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingLockedGroupFromMobileHomeOpensPinPrompt() {
        val group = LiveCategory("locked", "Protected channels", 5, CategoryIcon.Grid,
            playlistId = "source", playlistGroupName = "Protected channels")
        val hidden = group.copy(id = "hidden", label = "Hidden channels", playlistGroupName = "Hidden channels")
        val lockedKey = PlaylistGroupKey.build("source", "Protected channels")
        val pending = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                val context = LocalContext.current
                val player = remember { ExoPlayer.Builder(context).build() }
                DisposableEffect(player) { onDispose { player.release() } }
                LiveTvGroupHome(exoPlayer = player, currentChannel = null, nowNext = null,
                    clockTickMillis = 0L, allChannelsCount = 10, sportsCount = 0,
                    favoritesCount = 0, recentsCount = 0, favoriteSet = emptySet(),
                    onToggleFavorite = {}, onOpenFullscreen = {},
                    groups = visibleMobileGroups(listOf(group, hidden), emptySet(), setOf("Hidden channels")),
                    onOpenAllChannels = {}, onOpenSports = {}, onOpenFavorites = {}, onOpenRecents = {},
                    onSelectGroup = { pending.value = it.pendingCategoryUnlock(setOf(lockedKey), emptySet()) != null },
                    playerActive = false)
                if (pending.value) PinEntryDialog(title = "Unlock protected channels",
                    onPinConfirmed = { pending.value = false }, onDismiss = { pending.value = false })
            }
        }
        compose.onNodeWithText("Protected channels").performScrollTo().performClick()
        compose.onNodeWithText("Unlock protected channels").assertIsDisplayed()
        compose.onNodeWithText("Hidden channels").assertDoesNotExist()
    }
}
