package com.arflix.tv.data.repository

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ProfileListMergeTest {

    private fun profile(id: String, name: String = id) = JSONObject().put("id", id).put("name", name)

    private fun payload(vararg profiles: JSONObject, active: String? = profiles.firstOrNull()?.optString("id")) =
        JSONObject()
            .put("profiles", JSONArray(profiles.toList()))
            .put("activeProfileId", active ?: JSONObject.NULL)

    private fun ids(root: JSONObject): List<String> {
        val array = root.getJSONArray("profiles")
        return (0 until array.length()).map { array.getJSONObject(it).getString("id") }
    }

    @Test
    fun `a device that missed a new profile does not delete it`() {
        val kids = profile("kids").put("isKidsProfile", true).put("kidsAgeLevel", "LITTLE_KIDS")
        val remote = payload(profile("main"), kids)
            .put("profileSettingsById", JSONObject().put("main", "m").put("kids", "k"))
            .put("catalogsByProfile", JSONObject().put("kids", JSONArray().put("row")))
        val stale = payload(profile("main", "Main renamed"))
            .put("profileSettingsById", JSONObject().put("main", "m2"))

        val merged = ProfileListMerge.merge(stale, remote, localDeleted = emptySet())

        assertEquals(listOf("main", "kids"), ids(merged))
        assertEquals("Main renamed", merged.getJSONArray("profiles").getJSONObject(0).getString("name"))
        // The kept profile keeps the fields this device's build does not know.
        assertEquals("LITTLE_KIDS", merged.getJSONArray("profiles").getJSONObject(1).getString("kidsAgeLevel"))
        // And its per-profile data, while this device's own entries still win.
        assertEquals("k", merged.getJSONObject("profileSettingsById").getString("kids"))
        assertEquals("m2", merged.getJSONObject("profileSettingsById").getString("main"))
        assertEquals("row", merged.getJSONObject("catalogsByProfile").getJSONArray("kids").getString(0))
    }

    @Test
    fun `a profile deleted here is removed and stays removed`() {
        val remote = payload(profile("main"), profile("old"))
        val afterDelete = ProfileListMerge.merge(payload(profile("main")), remote, localDeleted = setOf("old"))
        assertEquals(listOf("main"), ids(afterDelete))
        assertEquals(setOf("old"), ProfileListMerge.deletedIds(afterDelete))

        // Another device still holding the deleted profile pushes afterwards.
        val holdout = payload(profile("main"), profile("old"))
        val again = ProfileListMerge.merge(holdout, afterDelete, localDeleted = emptySet())
        assertEquals(listOf("main"), ids(again))
    }

    @Test
    fun `new profiles are added and a fresh install placeholder is not`() {
        val remote = payload(profile("main"))
        val added = ProfileListMerge.merge(payload(profile("main"), profile("new")), remote, emptySet())
        assertEquals(listOf("main", "new"), ids(added))

        val freshInstall = payload(profile("p1", "Profile 1"))
        assertEquals(listOf("main"), ids(ProfileListMerge.merge(freshInstall, remote, emptySet())))
        // On an empty account the placeholder is the account's only profile, so it stays.
        assertEquals(listOf("p1"), ids(ProfileListMerge.merge(payload(profile("p1", "Profile 1")), payload(), emptySet())))
    }

    @Test
    fun `active profile falls back to a profile that still exists`() {
        val merged = ProfileListMerge.merge(
            payload(profile("main"), active = "gone"), payload(profile("main")), localDeleted = setOf("gone")
        )
        assertEquals("main", merged.getString("activeProfileId"))
        assertFalse(ids(merged).contains("gone"))
        assertTrue(ProfileListMerge.deletedIds(merged).contains("gone"))
    }

    @Test
    fun `a stale copy does not overwrite newer kids settings or PIN protection`() {
        // Edited on another device: age limit raised to teens, parent PIN and lock set.
        val newer = profile("kids").put("isKidsProfile", true).put("kidsAgeLevel", "TEENS")
            .put("parentPin", "hash-new").put("isLocked", true).put("pin", "pin-new")
            .put("updatedAt", 2_000L).put("lastUsedAt", 1_000L)
        // This device's copy predates that edit, but it selected the profile more recently.
        val stale = profile("kids").put("isKidsProfile", true).put("kidsAgeLevel", "LITTLE_KIDS")
            .put("updatedAt", 1_000L).put("lastUsedAt", 5_000L)

        val merged = ProfileListMerge.merge(payload(stale), payload(newer), emptySet())
        val kept = merged.getJSONArray("profiles").getJSONObject(0)
        assertEquals("TEENS", kept.getString("kidsAgeLevel"))
        assertEquals("hash-new", kept.getString("parentPin"))
        assertTrue(kept.getBoolean("isLocked"))
        assertEquals(5_000L, kept.getLong("lastUsedAt"))

        // A copy saved before updatedAt existed counts as oldest.
        val legacy = profile("kids").put("isKidsProfile", true).put("kidsAgeLevel", "LITTLE_KIDS")
        val fromLegacy = ProfileListMerge.merge(payload(legacy), payload(newer), emptySet())
        assertEquals("TEENS", fromLegacy.getJSONArray("profiles").getJSONObject(0).getString("kidsAgeLevel"))

        // And this device's own newer edit still wins.
        val edited = profile("kids").put("isKidsProfile", false).put("updatedAt", 3_000L)
        val fromEdit = ProfileListMerge.merge(payload(edited), payload(newer), emptySet())
        assertFalse(fromEdit.getJSONArray("profiles").getJSONObject(0).getBoolean("isKidsProfile"))
    }
}
