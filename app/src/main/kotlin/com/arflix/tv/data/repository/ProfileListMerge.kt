package com.arflix.tv.data.repository

import org.json.JSONArray
import org.json.JSONObject

/**
 * Merges this device's profile list into the account's cloud copy instead of replacing it.
 *
 * Every device saves the whole account payload. Replacing the cloud's `profiles` with the
 * local list meant a device that had not synced since another device added a profile (an
 * older build, a TV that was off) deleted that profile from the account on its next push,
 * and every other device then restored the shortened list.
 *
 * Now a profile the device does not know stays, together with its per-profile data. Only an
 * explicit delete removes one: deleted ids are kept in the payload under [DELETED_KEY], so a
 * device still holding a deleted profile cannot bring it back either.
 *
 * For a profile both sides have, the copy edited last wins (`updatedAt`, stamped on edits
 * only), so a stale copy cannot revert newer settings such as a PIN or the kids age limit. A
 * copy without the stamp predates it and counts as oldest; on a tie the device's copy wins.
 */
internal object ProfileListMerge {
    const val DELETED_KEY = "deletedProfileIds"

    /** Most recent deletions kept; older ids are long gone from every device. */
    private const val DELETED_LIMIT = 100

    fun deletedIds(root: JSONObject): Set<String> {
        val array = root.optJSONArray(DELETED_KEY) ?: return emptySet()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
    }

    /**
     * Applies the merge to [local], the payload this device is about to save, using [remote],
     * the account's current copy. [localDeleted] are the profiles deleted on this device.
     */
    fun merge(local: JSONObject, remote: JSONObject, localDeleted: Set<String>): JSONObject {
        val deleted = LinkedHashSet<String>().apply {
            addAll(deletedIds(remote))
            addAll(deletedIds(local))
            addAll(localDeleted)
        }
        val localProfiles = profiles(local)
        val remoteProfiles = profiles(remote)
        val localIds = localProfiles.map { it.optString("id") }.toSet()
        val remoteOnlyIds = mutableSetOf<String>()

        val merged = JSONArray()
        val seen = mutableSetOf<String>()
        fun add(profile: JSONObject) {
            val id = profile.optString("id")
            if (id.isBlank() || id in deleted || !seen.add(id)) return
            merged.put(profile)
        }
        for (profile in remoteProfiles) {
            val id = profile.optString("id")
            val own = localProfiles.firstOrNull { it.optString("id") == id }
            if (own == null && id.isNotBlank() && id !in deleted) remoteOnlyIds += id
            add(if (own == null) profile else newer(own, profile))
        }
        // A fresh install's untouched "Profile 1" is not a profile the user made; adding it to
        // an account that already has profiles would leave a stray one behind.
        val keepPlaceholder = remoteProfiles.isEmpty()
        for (profile in localProfiles) {
            if (!keepPlaceholder && isPlaceholder(profile)) continue
            add(profile)
        }

        local.put("profiles", merged)
        if (deleted.isNotEmpty()) {
            local.put(DELETED_KEY, JSONArray(deleted.toList().takeLast(DELETED_LIMIT)))
        }
        val active = local.optString("activeProfileId")
        if (active.isBlank() || active !in seen) {
            local.put("activeProfileId", merged.optJSONObject(0)?.optString("id") ?: JSONObject.NULL)
        }
        if (remoteOnlyIds.isNotEmpty()) keepPerProfileData(local, remote, remoteOnlyIds - localIds)
        return local
    }

    /**
     * Per-profile maps (settings, tokens, avatars, catalogs...) are keyed by profile id and
     * built from this device's profiles only; the entries of profiles kept from the cloud are
     * copied over so they are not stripped from the account either.
     */
    private fun keepPerProfileData(local: JSONObject, remote: JSONObject, ids: Set<String>) {
        val keys = remote.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val remoteMap = remote.optJSONObject(key) ?: continue
            if (ids.none(remoteMap::has)) continue
            val localMap = local.optJSONObject(key) ?: if (local.has(key)) continue else JSONObject().also {
                local.put(key, it)
            }
            for (id in ids) {
                if (remoteMap.has(id) && !localMap.has(id)) localMap.put(id, remoteMap.get(id))
            }
        }
    }

    /** The copy edited last; the most recent `lastUsedAt` is kept either way. */
    private fun newer(local: JSONObject, remote: JSONObject): JSONObject {
        val winner = if (remote.optLong("updatedAt", 0L) > local.optLong("updatedAt", 0L)) remote else local
        val lastUsed = maxOf(local.optLong("lastUsedAt", 0L), remote.optLong("lastUsedAt", 0L))
        if (lastUsed > 0L) winner.put("lastUsedAt", lastUsed)
        return winner
    }

    private fun profiles(root: JSONObject): List<JSONObject> {
        val array = root.optJSONArray("profiles") ?: return emptyList()
        return (0 until array.length()).mapNotNull(array::optJSONObject)
    }

    private fun isPlaceholder(profile: JSONObject): Boolean =
        profile.optString("name").equals("Profile 1", ignoreCase = true) &&
            profile.optInt("avatarId", 0) == 0 &&
            profile.optLong("avatarImageVersion", 0L) <= 0L &&
            !profile.optBoolean("isKidsProfile", false) &&
            !profile.optBoolean("isLocked", false) &&
            profile.optString("pin").let { it.isBlank() || it == "null" }
}
