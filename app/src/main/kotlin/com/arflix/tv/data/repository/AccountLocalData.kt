package com.arflix.tv.data.repository

import android.content.Context
import java.io.File

/**
 * Files and caches the signed-in account leaves on this device, removed at sign-out. Kept apart
 * from device-level state on purpose: downloads, the app language, the audio-sync model and the
 * image caches stay, while anything built from the account's addons, playlists or history goes.
 */
internal object AccountLocalData {
    /** In filesDir: native addon items, IPTV caches and custom profile avatars. */
    val accountFiles = listOf("addon_native_items.json", "iptv_cache", "xtream_vod_disk_cache", "profile_avatars")

    /** Per-profile snapshots: Continue Watching (filesDir) and the Home rows (cacheDir). */
    const val CONTINUE_WATCHING_PREFIX = "home_continue_watching_"
    const val HOME_ROWS_PREFIX = "home_categories_cache_"

    /**
     * IPTV resolver and guide caches built from the account's playlists, the Discord login (it
     * reconnects by itself after the restart) and the SIMKL V2 grants of every profile.
     */
    val accountSharedPrefs = listOf(
        "arvio_iptv_prefs",
        "iptv_movie_source_cache_v1",
        "iptv_series_resolver_cache_v1",
        "arvio_epg_http_cache",
        "discord_rpc_prefs",
        "simkl_lists_v2"
    )

    /** The account's IPTV channel list and guide index. */
    val accountDatabases = listOf("arvio_iptv_channels.db", "arvio_iptv_epg_index.db")

    fun isAccountFile(name: String): Boolean =
        name in accountFiles || name.startsWith(CONTINUE_WATCHING_PREFIX)

    fun isAccountCache(name: String): Boolean = name.startsWith(HOME_ROWS_PREFIX)
}

internal fun deleteAccountLocalFiles(context: Context) {
    fun deleteMatching(dir: File, matches: (String) -> Boolean) {
        dir.listFiles()?.filter { matches(it.name) }?.forEach { it.deleteRecursively() }
    }
    deleteMatching(context.filesDir, AccountLocalData::isAccountFile)
    deleteMatching(context.cacheDir, AccountLocalData::isAccountCache)
    AccountLocalData.accountSharedPrefs.forEach { name ->
        runCatching { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }
    AccountLocalData.accountDatabases.forEach { name -> runCatching { context.deleteDatabase(name) } }
}
