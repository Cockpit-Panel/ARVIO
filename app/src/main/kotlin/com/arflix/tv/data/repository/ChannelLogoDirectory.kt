package com.arflix.tv.data.repository

import android.content.Context
import android.util.JsonReader
import com.arflix.tv.data.model.ChannelLogoEntry
import com.arflix.tv.data.model.ChannelLogoIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.IPTV_FALLBACK_LOGOS_ENABLED_KEY

object ChannelLogoDirectory {
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fallbackPreference: StateFlow<Boolean>? = null
    @Synchronized fun fallbackEnabled(context: Context): StateFlow<Boolean> =
        fallbackPreference ?: context.applicationContext.settingsDataStore.data
            .map { it[IPTV_FALLBACK_LOGOS_ENABLED_KEY] ?: false }
            .stateIn(preferenceScope, SharingStarted.Eagerly, false)
            .also { fallbackPreference = it }

    private val mutex = Mutex()
    @Volatile private var index: ChannelLogoIndex? = null

    suspend fun candidates(context: Context, epgId: String?, name: String, guideName: String? = null): List<String> = withContext(Dispatchers.IO) {
        val directory = index ?: mutex.withLock {
            index ?: run {
                val entries = mutableListOf<ChannelLogoEntry>()
                context.applicationContext.assets.open("channel-logos.json").bufferedReader().use { input ->
                    JsonReader(input).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() != "entries") { reader.skipValue(); continue }
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginArray()
                                val id = reader.nextString()
                                val country = reader.nextString()
                                val names = reader.strings()
                                val urls = reader.strings()
                                reader.endArray()
                                entries += ChannelLogoEntry(id, country, names, urls)
                            }
                            reader.endArray()
                        }
                        reader.endObject()
                    }
                }
                ChannelLogoIndex(entries).also { index = it }
            }
        }
        directory.candidates(epgId, name, guideName)
    }

    private fun JsonReader.strings(): List<String> {
        beginArray()
        val values = mutableListOf<String>()
        while (hasNext()) values += nextString()
        endArray()
        return values
    }
}
