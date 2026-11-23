package com.arflix.tv.data.repository

import com.arflix.tv.data.api.StreamApi
import com.arflix.tv.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

data class SportsAddonStream(val name: String, val url: String, val headers: Map<String, String>, val external: Boolean, val description: String? = null)

internal fun playbackSportsSources(sources: List<SportsAddonStream>) = sources.filterNot {
    it.external && Regex("\\b(support the project|donate|donation)\\b", RegexOption.IGNORE_CASE)
        .containsMatchIn("${it.name} ${it.description.orEmpty()}")
}.distinctBy { Triple(it.url, it.headers, it.external) }.sortedBy { it.external }

@Singleton
class SportsAddonRepository @Inject constructor(private val streams: StreamRepository, private val api: StreamApi) {
    val installedAddons get() = streams.installedAddons
    private val permits = Semaphore(3)
    private val mutex = Mutex()
    private data class Cached(val at: Long, val events: List<SportsAddonEvent>)
    private val cache = linkedMapOf<String, Cached>()

    suspend fun load(addons: List<Addon>, publish: suspend (List<SportsAddonEvent>) -> Unit): List<SportsAddonEvent> {
        val publishContext = currentCoroutineContext().minusKey(Job)
        return withContext(Dispatchers.Default) {
        val results = linkedMapOf<String, SportsAddonEvent>()
        val publishMutex = Mutex()
        suspend fun publishSnapshot() {
            val snapshot = results.values.toList()
            withContext(publishContext) { publish(snapshot) }
        }
        addons.flatMap { addon -> sportsEventCatalogs(addon).map { addon to it } }.map { (addon, catalog) -> async {
            val installation = sportsAddonInstallation(addon)
            val key = "$installation|${addon.version}|${catalog.type}|${catalog.id}"
            val now = System.currentTimeMillis()
            val cached = mutex.withLock { cache[key]?.takeIf { now - it.at in 0..119_999 } }
            val events = cached?.events ?: permits.withPermit {
                val items = linkedMapOf<String, SportsAddonEvent>()
                val seen = hashSetOf<String>()
                var skip = 0
                var failed = false
                val paginated = catalog.extra.orEmpty().any { it.name == "skip" }
                // Bound broken providers that ignore skip or produce an infinite catalog.
                for (page in 0 until 50) {
                    val url = sportsAddonUrl(addon, "catalog", catalog.type, catalog.id, skip.takeIf { paginated }) ?: break
                    val response = try { withTimeoutOrNull(8_000) { api.getAddonCatalog(url) } }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (response == null) { failed = true; break }
                    val metas = response.metas ?: response.items ?: emptyList()
                    if (metas.isEmpty()) break
                    val newIds = metas.mapNotNull { it.id }.count { seen.add(it) }
                    if (newIds == 0) break
                    metas.mapNotNull { it.toSportsAddonEvent(addon, catalog, now, installation) }.forEach { item ->
                        items[item.key] = items[item.key]?.let { mergeSportsAddonEvent(it, item) } ?: item
                    }
                    publishMutex.withLock {
                        items.values.forEach { item -> results[item.key] = results[item.key]?.let { mergeSportsAddonEvent(it, item) } ?: item }
                        publishSnapshot()
                    }
                    if (!paginated) break
                    skip += metas.size
                }
                items.values.toList().also { loaded -> mutex.withLock {
                    if (!failed) cache[key] = Cached(now, loaded)
                    while (cache.size > 100) cache.remove(cache.keys.first())
                } }
            }
            publishMutex.withLock {
                events.forEach { item -> results[item.key] = results[item.key]?.let { mergeSportsAddonEvent(it, item) } ?: item }
                publishSnapshot()
            }
        } }.awaitAll()
        results.values.toList()
        }
    }

    suspend fun resolve(event: SportsAddonEvent): List<SportsAddonStream> = withContext(Dispatchers.IO) {
        val addon = installedAddons.first().firstOrNull {
            it.isEnabled && it.isInstalled && sportsAddonInstallation(it) == event.installation
        } ?: return@withContext emptyList()
        val url = sportsAddonUrl(addon, "stream", event.type, event.eventId) ?: return@withContext emptyList()
        val response = withTimeout(15_000) { api.getAddonStreams(url) }
        playbackSportsSources(response.streams.orEmpty().mapNotNull { stream ->
            val direct = stream.url?.takeIf { it.isNotBlank() }
            val external = direct == null && (!stream.externalUrl.isNullOrBlank() || !stream.ytId.isNullOrBlank())
            val target = direct ?: stream.externalUrl?.takeIf { it.isNotBlank() } ?: stream.ytId?.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }
            if (target == null || !target.startsWith("https://") && !target.startsWith("http://")) return@mapNotNull null
            SportsAddonStream(stream.name ?: stream.title ?: addon.name, target,
                stream.headers.orEmpty() + stream.behaviorHints?.headers.orEmpty() + stream.behaviorHints?.proxyHeaders?.request.orEmpty(), external,
                stream.title?.takeIf { it.isNotBlank() && it != stream.name })
        })
    }
}
