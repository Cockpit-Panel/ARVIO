package com.arflix.tv.ui.screens.tv.live

import android.os.Bundle
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.data.api.StreamApi
import com.arflix.tv.data.api.StremioCatalogResponse
import com.arflix.tv.data.api.StremioMetaPreview
import com.arflix.tv.data.model.*
import com.arflix.tv.data.model.sportsEventCatalogs
import com.arflix.tv.data.repository.SportsAddonRepository
import com.arflix.tv.di.RepositoryAccessEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Opt-in network check; installing an add-on requires an explicit instrumentation argument. */
class SportsAddonLiveDeviceTest {
    @Test fun catalogWorkStaysOffMainAndPublishesOnCallerThread() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val streams = EntryPointAccessors.fromApplication(instrumentation.targetContext,
            RepositoryAccessEntryPoint::class.java).streamRepository()
        val catalog = AddonCatalog("sport", "sports_live", "Live Now")
        val addon = Addon("thread-test", "Sports", "1", "Live sports", true, type = AddonType.COMMUNITY,
            url = "https://example.invalid/manifest.json", manifest = AddonManifest("thread-test", "Sports", "1",
                resources = listOf(AddonResource("catalog"), AddonResource("stream")), catalogs = listOf(catalog)))
        val payload = StremioCatalogResponse(metas = List(1000) {
            StremioMetaPreview(id = "event:$it", type = "sport", name = "North $it vs South $it", releaseInfo = "LIVE")
        })
        val api = java.lang.reflect.Proxy.newProxyInstance(StreamApi::class.java.classLoader, arrayOf(StreamApi::class.java)) { _, method, _ ->
            check(method.name == "getAddonCatalog")
            assertNotEquals("Catalog work must not run on the UI thread", Looper.getMainLooper(), Looper.myLooper())
            payload
        } as StreamApi
        val repository = SportsAddonRepository(streams, api)
        var publications = 0
        val events = withContext(Dispatchers.Main) {
            repository.load(listOf(addon)) {
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
                publications++
            }
        }
        assertEquals(1000, events.size)
        assertTrue(publications > 0)
    }

    @Test fun installedProviderLoadsAndResolvesSportsWithoutPlaylist() = runBlocking {
        val url = InstrumentationRegistry.getArguments().getString("sportsAddonManifest")
        assumeTrue("Provide sportsAddonManifest to opt into this device check", !url.isNullOrBlank())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val streams = EntryPointAccessors.fromApplication(instrumentation.targetContext,
            RepositoryAccessEntryPoint::class.java).streamRepository()
        val addon = withTimeout(30_000) { streams.addCustomAddon(url!!).getOrThrow() }
        assertTrue(sportsEventCatalogs(addon).isNotEmpty())
        val api = Retrofit.Builder().baseUrl("https://example.invalid/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(StreamApi::class.java)
        val repository = SportsAddonRepository(streams, api)
        var firstAt = 0L
        val started = System.currentTimeMillis()
        val sources = withTimeout(120_000) { repository.load(listOf(addon)) { if (it.isNotEmpty() && firstAt == 0L) firstAt = System.currentTimeMillis() } }
        val now = System.currentTimeMillis()
        val events = attachSportsAddonSources(emptyList(), sources, now)
        assertTrue("Provider returned no current/upcoming events", events.isNotEmpty())
        val live = sources.filter { it.isLive(now) }
        val resolved = live.firstOrNull()?.let { repository.resolve(it) }.orEmpty()
        instrumentation.sendStatus(0, Bundle().apply { putString("stream",
            "\nSports add-on: ${addon.name}; events=${events.size}; live=${live.size}; firstResultMs=${firstAt - started}; totalMs=${now - started}; firstLiveStreams=${resolved.size}\n") })
        if (live.isNotEmpty()) assertTrue("First live event returned no playable or external sources", resolved.isNotEmpty())
    }
}
