package com.arflix.tv.util

/** Only fixed route names leave the device, never route arguments. */
object AnalyticsPolicy {
    fun screen(route: String?): String? {
        val name = route?.substringBefore('/')?.substringBefore('?')?.lowercase() ?: return null
        return when {
            name == "home" -> "home"
            name == "search" -> "search"
            name.contains("watchlist") || name == "library" -> "library"
            name.contains("collection") -> "collections"
            name.contains("calendar") -> "calendar"
            name.contains("detail") -> "details"
            name.contains("player") || name.contains("playback") -> "player"
            name.contains("settings") -> "settings"
            name == "tv" || name.contains("livetv") -> "tv"
            name.contains("profile") -> "profiles"
            name.contains("login") || name.contains("auth") -> "login"
            else -> null
        }
    }
}
