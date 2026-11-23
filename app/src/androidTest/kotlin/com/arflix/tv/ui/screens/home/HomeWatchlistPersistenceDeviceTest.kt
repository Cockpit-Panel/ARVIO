package com.arflix.tv.ui.screens.home

import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.MainActivity
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.ui.screens.settings.SettingsViewModel
import com.arflix.tv.util.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HomeWatchlistPersistenceDeviceTest {
    @Test fun realSettingsAndMembershipChangesReachHomeAndSurviveRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val item = MediaItem(9000101, "Home watchlist verification")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var home: HomeViewModel
            lateinit var settings: SettingsViewModel
            scenario.onActivity {
                home = ViewModelProvider(it)[HomeViewModel::class.java]
                settings = ViewModelProvider(it)[SettingsViewModel::class.java]
            }
            scenario.onActivity { settings.setWatchlistOnHome(true) }
            await { settings.uiState.value.watchlistOnHome }
            scenario.onActivity { home.toggleWatchlist(item) }
            await { home.homeWatchlistItems.value.any { it.id == item.id } }
            assertTrue(runBlocking {
                context.settingsDataStore.data.first()[booleanPreferencesKey("profile_default_watchlist_on_home")] == true
            })
            scenario.recreate()
            scenario.onActivity { home = ViewModelProvider(it)[HomeViewModel::class.java] }
            await { home.homeWatchlistItems.value.any { it.id == item.id } }
            scenario.onActivity { settings = ViewModelProvider(it)[SettingsViewModel::class.java]; settings.setWatchlistOnHome(false) }
            await { home.homeWatchlistItems.value.isEmpty() }
            scenario.onActivity { settings.setWatchlistOnHome(true) }
            await { home.homeWatchlistItems.value.any { it.id == item.id } }
            scenario.onActivity { home.toggleWatchlist(item) }
            await { home.homeWatchlistItems.value.none { it.id == item.id } }
            scenario.onActivity { settings.setWatchlistOnHome(false) }
            await { !settings.uiState.value.watchlistOnHome }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Watchlist state did not update within 15 seconds", condition())
    }
}
