package com.arflix.tv.data.repository

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AccountLocalDataTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun file(dir: File, path: String) = File(dir, path).apply { parentFile?.mkdirs(); writeText("x") }

    @Test fun signOutRemovesTheAccountsFilesAndKeepsTheDevicesOwn() {
        val files = context.filesDir
        val cache = context.cacheDir
        val account = listOf(
            file(files, "addon_native_items.json"),
            file(files, "iptv_cache/channels.bin"),
            file(files, "xtream_vod_disk_cache/page.json"),
            file(files, "profile_avatars/p1.png"),
            file(files, "home_continue_watching_v2_p1_he-IL.json"),
            file(cache, "home_categories_cache_p1_he-IL.json")
        )
        val device = listOf(
            file(files, "audiosync/asr/model.bin"),
            file(cache, "media3_playback_cache/segment"),
            file(cache, "release-calendar-v1/week.json")
        )
        context.getSharedPreferences("arvio_iptv_prefs", Context.MODE_PRIVATE).edit().putString("k", "v").commit()
        context.getSharedPreferences("discord_rpc_prefs", Context.MODE_PRIVATE).edit()
            .putString("access_token", "token").putString("refresh_token", "refresh").commit()
        context.getSharedPreferences("simkl_lists_v2", Context.MODE_PRIVATE).edit()
            .putString("grant_p1", "encrypted").putString("grant_p2", "encrypted").commit()
        context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().putString("locale_tag", "he").commit()
        context.openOrCreateDatabase("arvio_iptv_channels.db", Context.MODE_PRIVATE, null).close()

        deleteAccountLocalFiles(context)

        account.forEach { assertFalse("${it.name} should be gone", it.exists()) }
        device.forEach { assertTrue("${it.name} should stay", it.exists()) }
        assertNull(context.getSharedPreferences("arvio_iptv_prefs", Context.MODE_PRIVATE).getString("k", null))
        assertTrue("the Discord login must not survive sign-out",
            context.getSharedPreferences("discord_rpc_prefs", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue("no profile's SIMKL V2 grant may survive sign-out",
            context.getSharedPreferences("simkl_lists_v2", Context.MODE_PRIVATE).all.isEmpty())
        assertEquals("he", context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).getString("locale_tag", null))
        assertFalse(context.getDatabasePath("arvio_iptv_channels.db").exists())
    }
}
