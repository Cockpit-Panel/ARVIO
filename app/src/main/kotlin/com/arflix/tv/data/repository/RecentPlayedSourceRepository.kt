package com.arflix.tv.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.RecentPlayedSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-profile store of [RecentPlayedSource]s: one record per movie and one per episode, so each
 * episode remembers its own source. The most recently played [MAX_RECORDS] are kept.
 */
@Singleton
class RecentPlayedSourceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
) {
    private val gson = Gson()
    private val recordsType = object : TypeToken<Map<String, RecentPlayedSource>>() {}.type
    // Saves outlive the player screen: leaving right after the threshold must not drop the write.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The active profile's record for this movie or episode ([season]/[episode] are TMDB numbers). */
    suspend fun get(mediaType: MediaType, mediaId: Int, season: Int?, episode: Int?): RecentPlayedSource? = withContext(Dispatchers.IO) {
        try {
            val raw = context.streamDataStore.data.first()[profileManager.profileStringKey(RECORDS_KEY)].orEmpty()
            val records = decode(raw)
            // Older versions kept one record per show under the title key; it still counts for
            // the episode it was saved for.
            (records[recordKey(mediaType, mediaId, season, episode)] ?: records[recordKey(mediaType, mediaId, null, null)])
                ?.takeIf { it.appliesTo(mediaType, mediaId, season, episode) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "read failed: ${e.message}")
            null
        }
    }

    /** Replaces the record for the record's movie or episode. [profileId] is the profile that watched it. */
    fun save(profileId: String, record: RecentPlayedSource) {
        scope.launch {
            try {
                val key = profileManager.profileStringKeyFor(profileId, RECORDS_KEY)
                context.streamDataStore.edit { prefs ->
                    val records = decode(prefs[key].orEmpty()) +
                        (recordKey(record.mediaType, record.mediaId, record.season, record.episode) to record)
                    val kept = records.entries
                        .sortedByDescending { it.value.playedAtMs }
                        .take(MAX_RECORDS)
                        .associate { it.key to it.value }
                    prefs[key] = gson.toJson(kept)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "save failed: ${e.message}")
            }
        }
    }

    private fun decode(raw: String): Map<String, RecentPlayedSource> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, RecentPlayedSource>>(raw, recordsType)
        }.getOrNull().orEmpty()
    }

    private fun recordKey(mediaType: MediaType, mediaId: Int, season: Int?, episode: Int?) =
        if (season == null || episode == null) "${mediaType.name}:$mediaId"
        else "${mediaType.name}:$mediaId:$season:$episode"

    private companion object {
        const val TAG = "RecentPlayedSource"
        const val RECORDS_KEY = "recent_played_sources_v1"
        const val MAX_RECORDS = 500
    }
}
