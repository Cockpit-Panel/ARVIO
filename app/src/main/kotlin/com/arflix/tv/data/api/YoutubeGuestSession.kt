package com.arflix.tv.data.api

import com.google.gson.Gson
import com.google.gson.JsonParseException

internal data class YoutubeGuestSession(val apiKey: String, val visitorData: String)

/** Logged-out bootstrap data; no account cookies, credentials or browser runtime. */
internal fun parseYoutubeGuestSession(payload: String): YoutubeGuestSession? = try {
    val json = payload.trimStart().removePrefix(")]}'").trimStart()
    val root = Gson().fromJson(json, List::class.java)
    // YouTube's sw.js_data response: configuration is [0][2], device info [0][0].
    val envelope = root?.getOrNull(0) as? List<*>
    val config = envelope?.getOrNull(2) as? List<*>
    val devices = config?.getOrNull(0) as? List<*>
    val device = devices?.getOrNull(0) as? List<*>
    val apiKey = (config?.getOrNull(1) as? String)?.takeIf { it.isNotBlank() }
    val visitor = (device?.getOrNull(13) as? String)?.takeIf { it.isNotBlank() }
    if (apiKey != null && visitor != null) YoutubeGuestSession(apiKey, visitor) else null
} catch (_: JsonParseException) {
    null
}
