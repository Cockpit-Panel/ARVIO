package com.arflix.tv.data.repository

import com.google.gson.JsonParser

/** Interpret Silo's Jellyfin-compatible login failures without displaying server-supplied secrets. */
internal enum class HomeServerLoginFailure {
    PROFILE, PIN, ENDPOINT;

    companion object {
        fun detect(status: Int, body: String): HomeServerLoginFailure? {
            if (status == 404 || status == 405 || body.trimStart().startsWith("<")) return ENDPOINT
            if (status != 401) return null
            val message = runCatching {
                JsonParser.parseString(body).asJsonObject.get("Message")?.asString
                    ?: JsonParser.parseString(body).asJsonObject.get("message")?.asString
            }.getOrNull()?.lowercase().orEmpty()
            return when {
                "profile pin" in message || "profile is pin protected" in message || "password#pin" in message -> PIN
                "username#profile" in message || "profile not found" in message || "profile name is ambiguous" in message -> PROFILE
                else -> null
            }
        }
    }
}
