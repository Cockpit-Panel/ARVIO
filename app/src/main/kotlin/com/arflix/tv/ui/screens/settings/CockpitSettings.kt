package com.arflix.tv.ui.screens.settings

import android.net.Uri
import com.arflix.tv.data.api.ArvioPortal
import com.arflix.tv.data.repository.IptvPlaylistEntry

/**
 * Cockpit build switches and helpers for the Settings screen.
 *
 * Kept in its own file so upstream merges into SettingsScreen.kt only have to
 * carry a few small call sites.
 */
internal object CockpitSettings {
    /** Addons are provisioned by the Cockpit panel: no custom installs, community browser or deletes. */
    const val ADDON_MANAGEMENT_ENABLED = false
}

/** Xtream source written as "host username password". */
internal data class CockpitXtreamParts(
    val host: String,
    val username: String,
    val password: String
)

internal fun splitCockpitXtreamCredentials(value: String): CockpitXtreamParts? {
    val trimmed = value.trim()
    val parts = trimmed.split(Regex("\\s+"))
    if (parts.size >= 3) {
        val host = parts.first().trim()
        val username = parts[1].trim()
        val password = parts.drop(2).joinToString(" ").trim()
        if (host.startsWith("http", ignoreCase = true) && username.isNotBlank() && password.isNotBlank()) {
            return CockpitXtreamParts(host.trimEnd('/'), username, password)
        }
    }
    // Normalized form: http://host:port/get.php?username=..&password=..
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val username = uri.getQueryParameter("username").orEmpty()
    val password = uri.getQueryParameter("password").orEmpty()
    if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank() || username.isBlank() || password.isBlank()) return null
    val base = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
    return CockpitXtreamParts(base, username, password)
}

internal fun ArvioPortal.normalizedCockpitUrl(): String = url.trim().trimEnd('/')

internal fun isCockpitPanelApiUrl(value: String): Boolean =
    value.trim().lowercase().contains("/api/arvio")

/** Playlist subtitle that never exposes the provider URL or credentials. */
internal fun cockpitMaskedIptvSourceLabel(playlist: IptvPlaylistEntry, portals: List<ArvioPortal>): String {
    val host = splitCockpitXtreamCredentials(playlist.m3uUrl)?.host
    val portalName = host?.let { h -> portals.firstOrNull { it.normalizedCockpitUrl().equals(h, ignoreCase = true) }?.name }
    return portalName?.takeIf { it.isNotBlank() }
        ?: playlist.name.ifBlank { "Arvio Portal" }
}
