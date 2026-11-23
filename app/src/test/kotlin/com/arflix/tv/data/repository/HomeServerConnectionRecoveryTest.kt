package com.arflix.tv.data.repository

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.util.settingsDataStore
import com.google.gson.Gson
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeServerConnectionRecoveryTest {
    @Before fun isolateSettings() {
        // Android disk/encryption behavior is covered by HomeServerIntegrationDeviceTest.
        // Keep these recovery/race tests independent of Windows file replacement semantics.
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            private val mutex = Mutex()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                mutex.withLock { transform(data.value).toPreferences().also { data.value = it } }
        }
        mockkStatic("com.arflix.tv.util.DataStoresKt")
        every { any<Context>().settingsDataStore } returns store
    }

    @After fun restoreSettings() { unmockkStatic("com.arflix.tv.util.DataStoresKt") }
    private val profiles = mockk<ProfileManager> {
        every { activeProfileId } returns MutableStateFlow("recovery")
        coEvery { getProfileId() } returns "recovery"
        every { profileStringKeyFor(any(), any()) } answers {
            stringPreferencesKey("${firstArg<String>()}_${secondArg<String>()}")
        }
    }
    private val client = mockk<OkHttpClient>()
    private val repository = HomeServerRepository(RuntimeEnvironment.getApplication(), client, profiles)

    private fun connection(kind: HomeServerKind) = HomeServerConnection(
        connectionId = kind.name, serverKind = kind, serverUrl = "https://example.invalid",
        userId = "member", accessToken = "test-token",
        collections = listOf(HomeServerCollection("films", "Films", "movies"))
    )

    @Test fun `one invalid saved record must not hide all other servers`() = runBlocking {
        val valid = listOf(HomeServerKind.PLEX, HomeServerKind.JELLYFIN, HomeServerKind.EMBY)
            .map { Gson().toJson(connection(it)) }
        val invalid = """{"serverUrl":"https://invalid.example","serverKind":null,"collections":[null]}"""
        repository.importCloudConnectionsJsonForProfile("recovery",
            """{"connections":[${valid[0]},null,$invalid,${valid[1]},${valid[2]}]}""")
        assertEquals(setOf(HomeServerKind.PLEX, HomeServerKind.JELLYFIN, HomeServerKind.EMBY),
            repository.currentConnections().filter { it.isUsable }.map { it.serverKind }.toSet())
        assertEquals(3, repository.getSavedCatalogCandidates(repository.currentConnections()).size)
    }

    @Test fun `legacy web connections are not discarded when imported`() = runBlocking {
        repository.importCloudConnectionsJsonForProfile("recovery", """[
            {"id":"legacy-plex","type":"plex","url":"https://plex.example","token":"test-token",
             "name":"My Plex","collections":[{"id":"1","name":"Movies","type":"movie"}]},
            {"id":"legacy-jellyfin","type":"jellyfin","url":"https://jellyfin.example","token":"test-token",
             "userId":"member","collections":[{"id":"2","name":"Shows","type":"tvshows"}]}
        ]""")
        val saved = repository.currentConnections()
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.isUsable })
        assertEquals(listOf(HomeServerKind.PLEX, HomeServerKind.JELLYFIN), saved.map { it.serverKind })
    }

    @Test fun `missing libraries recover for all providers without administrator endpoints`() = runBlocking {
        val servers = listOf(HomeServerKind.PLEX, HomeServerKind.JELLYFIN, HomeServerKind.EMBY)
            .map { connection(it).copy(collections = emptyList()) }
        repository.importCloudConnectionsJsonForProfile("recovery", Gson().toJson(servers))
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val json = when (request.url.encodedPath) {
                "/library/sections" -> """{"MediaContainer":{"Directory":[{"key":"1","title":"Films","type":"movie"}]}}"""
                "/Users/member/Views" -> """{"Items":[{"Id":"1","Name":"Films","CollectionType":"movies"}]}"""
                else -> error("Unexpected discovery endpoint: ${request.url.encodedPath}")
            }
            mockk<okhttp3.Call> { every { execute() } returns Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build() }
        }
        repository.refreshMissingLibraries()
        assertEquals(3, repository.getSavedCatalogCandidates(repository.currentConnections()).size)
        repository.refreshMissingLibraries()
        verify(exactly = 3) { client.newCall(any()) }
    }

    @Test fun `disabled and already configured libraries do not trigger recovery requests`() = runBlocking {
        val configured = connection(HomeServerKind.JELLYFIN)
        val disabled = connection(HomeServerKind.EMBY).copy(enabled = false, collections = emptyList())
        repository.importCloudConnectionsJsonForProfile("recovery", Gson().toJson(listOf(configured, disabled)))
        repository.refreshMissingLibraries()
        verify { client wasNot Called }
        assertEquals(2, repository.currentConnections().size)
    }

    @Test fun `recovery does not restore a server deleted during the request`() = runBlocking {
        val started = CountDownLatch(1)
        val complete = CountDownLatch(1)
        repository.importCloudConnectionsJsonForProfile("recovery",
            Gson().toJson(listOf(connection(HomeServerKind.JELLYFIN).copy(collections = emptyList()))))
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            mockk<okhttp3.Call> { every { execute() } answers {
                started.countDown()
                check(complete.await(5, TimeUnit.SECONDS))
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"Items":[{"Id":"1","Name":"Films","CollectionType":"movies"}]}"""
                        .toResponseBody("application/json".toMediaType())).build()
            } }
        }
        val recovery = async(Dispatchers.Default) { repository.refreshMissingLibraries() }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repository.importCloudConnectionsJsonForProfile("recovery", null)
        } finally {
            complete.countDown()
        }
        recovery.await()
        assertTrue(repository.currentConnections().isEmpty())
    }
}
