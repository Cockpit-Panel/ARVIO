package com.arflix.tv.data.model

import com.google.gson.JsonParser

data class SportsBroadcaster(val name: String, val country: String, val startsAt: Long)
data class SportsFixture(
    val id: String, val league: String?, val qualifier: String?, val venue: String?, val round: String?,
    val status: String, val observedAt: Long, val homeScore: Int?, val awayScore: Int?,
    val broadcasters: List<SportsBroadcaster>,
)

private val fixtureIdRegex = Regex("(?:\\d+|espn:[a-z]+:\\d+|mlb:\\d+)")

/** Public metadata DTO; the provider API key exists only in the backend. */
fun parseSportsMetadata(body: String): List<SportsEventArtwork> {
    val root = try {
        JsonParser.parseString(body).asJsonObject
    } catch (_: Exception) {
        null
    } ?: return emptyList()

    val version = try { root.get("version")?.asInt } catch (_: Exception) { null }
    if (version != 1) return emptyList()

    val events = try { root.getAsJsonArray("events") } catch (_: Exception) { null } ?: return emptyList()
    val catalogueEnabled = try { root.get("catalogueEnabled")?.asBoolean == true } catch (_: Exception) { false }

    return events.take(6000).mapNotNull { value ->
        try {
            val item = value.asJsonObject
            fun text(key: String) = item.get(key)?.takeUnless { it.isJsonNull }?.asString
            val title = text("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sport = text("sport")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val start = (try { item.get("startsAt")?.asLong } catch (_: Exception) { null })?.takeIf { it > 0 } ?: return@mapNotNull null
            val background = safeSportsImage(text("background"))
            val home = safeSportsImage(text("homeBadge"))
            val away = safeSportsImage(text("awayBadge"))
            val fixture = if (catalogueEnabled && text("id")?.matches(fixtureIdRegex) == true) SportsFixture(
                id = text("id")!!, league = text("league"), qualifier = text("qualifier"), venue = text("venue"), round = text("round"),
                status = text("status") ?: "scheduled", observedAt = try { item.get("observedAt")?.asLong ?: 0L } catch (_: Exception) { 0L },
                homeScore = text("homeScore")?.toIntOrNull(), awayScore = text("awayScore")?.toIntOrNull(),
                broadcasters = try {
                    item.getAsJsonArray("broadcasters")?.take(1500)?.mapNotNull { raw ->
                        try {
                            val b = raw.asJsonObject
                            SportsBroadcaster(b.get("name").asString, b.get("country").asString, b.get("startsAt").asLong)
                        } catch (_: Exception) {
                            null
                        }
                    }.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                },
            ) else null
            if (fixture == null && background == null && (home == null || away == null)) return@mapNotNull null
            SportsEventArtwork(title, background.orEmpty(), listOf(sport), start,
                homeBadge = if (away != null) home else null, awayBadge = if (home != null) away else null,
                homeTeam = text("homeTeam"), awayTeam = text("awayTeam"),
                source = text("source")?.takeIf { it == "ESPN" || it == "MLB" } ?: "TheSportsDB", fixture = fixture)
        } catch (_: Exception) {
            null
        }
    }
}
