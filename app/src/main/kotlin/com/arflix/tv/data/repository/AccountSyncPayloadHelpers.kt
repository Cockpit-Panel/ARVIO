package com.arflix.tv.data.repository
import com.google.gson.JsonParser
import org.json.JSONException
import org.json.JSONObject

// Cockpit: pure account-sync payload helpers kept verbatim from upstream
// AuthRepository.kt (the Cockpit AuthRepository replaces the rest of that file).

private fun parseJsonObject(payload: String): com.google.gson.JsonObject? {
    return try { JsonParser().parse(payload).asJsonObject } catch (e: com.google.gson.JsonSyntaxException) { null } catch (e: IllegalStateException) { null }
}

internal fun accountSyncPayloadProfileCount(payload: String): Int? {
    val root = parseJsonObject(payload) ?: return null
    if (!root.has("profiles")) return null
    return root.get("profiles")
        ?.takeIf { !it.isJsonNull && it.isJsonArray }
        ?.asJsonArray
        ?.size()
        ?: 0
}

internal fun accountSyncPayloadScopedCoverage(payload: String): Int {
    val root = parseJsonObject(payload) ?: return 0
    val profileIds = root.get("profiles")
        ?.takeIf { !it.isJsonNull && it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { profile ->
            profile
                ?.takeIf { !it.isJsonNull && it.isJsonObject }
                ?.asJsonObject
                ?.stringValue("id")
                ?.takeIf { it.isNotBlank() }
        }
        ?.toSet()
        .orEmpty()
    if (profileIds.isEmpty()) return 0

    val scopedKeys = listOf(
        "profileSettingsById",
        "addonsByProfile",
        "catalogsByProfile",
        "hiddenPreinstalledByProfile",
        "hiddenAddonByProfile",
        "hiddenHomeServerByProfile",
        "iptvByProfile",
        "watchlistByProfile"
    )

    return scopedKeys.sumOf { key ->
        val obj = root.getObject(key) ?: return@sumOf 0
        profileIds.count { profileId -> obj.has(profileId) }
    }
}

internal fun accountSyncPayloadRestoreRank(payload: String): Int {
    val root = parseJsonObject(payload) ?: return 0
    val profiles = root.get("profiles")
        ?.takeIf { !it.isJsonNull && it.isJsonArray }
        ?.asJsonArray
    val profileCount = if (root.has("profiles")) profiles?.size() ?: 0 else null
    val hasUsefulProfiles = when {
        profileCount == null -> false
        profileCount > 1 -> true
        profileCount == 1 -> profiles?.get(0)
            ?.takeIf { !it.isJsonNull && it.isJsonObject }
            ?.asJsonObject
            ?.let { !it.isPlaceholderCloudProfile() }
            ?: false
        else -> false
    }
    val hasConfiguredState = root.hasConfiguredAccountState()
    val hasFullSnapshotShape = root.hasFullAccountSnapshotShape()

    return when {
        profileCount != null && profileCount <= 0 -> 0
        profileCount != null && profileCount > 1 && hasFullSnapshotShape -> 80
        profileCount != null && profileCount > 1 -> 70
        (hasUsefulProfiles || hasConfiguredState) && hasFullSnapshotShape -> 50
        hasUsefulProfiles || hasConfiguredState -> 40
        profileCount == null && hasFullSnapshotShape -> 30
        profileCount == null -> 20
        else -> 10
    }
}

internal fun accountSyncPayloadSaveSucceeded(
    accountSyncSaved: Boolean,
    userSettingsSaved: Boolean,
    profileAddonsSaved: Boolean
): Boolean {
    return accountSyncSaved || userSettingsSaved || profileAddonsSaved
}

private fun accountSyncPayloadsMatch(expected: String, actual: String?): Boolean {
    if (actual.isNullOrBlank()) return false
    if (expected == actual) return true
    return try {
        val expectedJson = JSONObject(expected)
        val actualJson = JSONObject(actual)
        expectedJson.remove("updatedAt")
        actualJson.remove("updatedAt")
        expectedJson.toString() == actualJson.toString()
    } catch (_: JSONException) {
        false
    }
}

private fun com.google.gson.JsonObject.isPlaceholderCloudProfile(): Boolean {
    val name = stringValue("name").trim()
    val avatarId = intValue("avatarId")
    val avatarImageVersion = longValue("avatarImageVersion")
    val isKidsProfile = booleanValue("isKidsProfile")
    val isLocked = booleanValue("isLocked")
    val pin = stringValue("pin").trim()

    return name.equals("Profile 1", ignoreCase = true) &&
        avatarId == 0 &&
        avatarImageVersion <= 0L &&
        !isKidsProfile &&
        !isLocked &&
        pin.isBlank()
}

private fun com.google.gson.JsonObject.hasFullAccountSnapshotShape(): Boolean {
    return has("profileSettingsById") ||
        has("addonsByProfile") ||
        has("catalogsByProfile") ||
        has("iptvByProfile") ||
        has("watchlistByProfile")
}

private fun com.google.gson.JsonObject.hasConfiguredAccountState(): Boolean {
    if (arraySize("addons") > 0) return true
    if (stringValue("iptvM3uUrl").isNotBlank()) return true

    return objectHasNonEmptyArray("addonsByProfile") ||
        objectHasNonEmptyArray("watchlistByProfile") ||
        objectHasConfiguredIptvProfile("iptvByProfile")
}

private fun com.google.gson.JsonObject.objectHasNonEmptyArray(key: String): Boolean {
    val obj = getObject(key) ?: return false
    obj.entrySet().forEach { (_, value) ->
        if (value != null && !value.isJsonNull && value.isJsonArray && value.asJsonArray.size() > 0) {
            return true
        }
    }
    return false
}

private fun com.google.gson.JsonObject.objectHasConfiguredIptvProfile(key: String): Boolean {
    val obj = getObject(key) ?: return false
    obj.entrySet().forEach { (_, rawValue) ->
        val value = rawValue
            ?.takeIf { !it.isJsonNull && it.isJsonObject }
            ?.asJsonObject
            ?: return@forEach
        if (value.stringValue("m3uUrl").isNotBlank()) return true
        if (value.stringValue("epgUrl").isNotBlank()) return true
        if (value.arraySize("playlists") > 0) return true
        if (value.arraySize("favoriteChannels") > 0) return true
        if (value.arraySize("favoriteGroups") > 0) return true
    }
    return false
}

private fun com.google.gson.JsonObject.getObject(key: String): com.google.gson.JsonObject? {
    return get(key)
        ?.takeIf { !it.isJsonNull && it.isJsonObject }
        ?.asJsonObject
}

private fun com.google.gson.JsonObject.arraySize(key: String): Int {
    return get(key)
        ?.takeIf { !it.isJsonNull && it.isJsonArray }
        ?.asJsonArray
        ?.size()
        ?: 0
}

private fun com.google.gson.JsonObject.stringValue(key: String): String {
    return try {
        get(key)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
            .orEmpty()
    } catch (_: Exception) {
        ""
    }
}

private fun com.google.gson.JsonObject.intValue(key: String): Int {
    return try {
        get(key)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asInt
            ?: 0
    } catch (_: Exception) {
        0
    }
}

private fun com.google.gson.JsonObject.longValue(key: String): Long {
    return try {
        get(key)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asLong
            ?: 0L
    } catch (_: Exception) {
        0L
    }
}

private fun com.google.gson.JsonObject.booleanValue(key: String): Boolean {
    return try {
        get(key)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asBoolean
            ?: false
    } catch (_: Exception) {
        false
    }
}

/**
 * Authentication state
 */
