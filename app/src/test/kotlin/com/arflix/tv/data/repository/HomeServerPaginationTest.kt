package com.arflix.tv.data.repository

import android.app.Application
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.arflix.tv.testing.IsolatedSettingsStoreRule
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeServerPaginationTest {
    @get:Rule val settingsStore = IsolatedSettingsStoreRule()

    @Test fun allProvidersContinueWhenTheServerOmitsItsTotalCount() = runBlocking {
        val profile = "paging-${UUID.randomUUID()}"
        val profiles = mockk<ProfileManager> {
            every { activeProfileId } returns MutableStateFlow(profile)
            coEvery { getProfileId() } returns profile
            every { profileStringKeyFor(any(), any()) } answers { stringPreferencesKey("${firstArg<String>()}_${secondArg<String>()}") }
        }
        val client = mockk<OkHttpClient>()
        var reportedTotal: Int? = null
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val plex = request.url.encodedPath.startsWith("/library")
            val offset = request.url.queryParameter(if(plex) "X-Plex-Container-Start" else "StartIndex")!!.toInt()
            val entries = (offset until (offset + 2).coerceAtMost(3)).joinToString(",") {
                if(plex) """{"ratingKey":"$it","title":"Movie $it","type":"movie"}"""
                else """{"Id":"$it","Name":"Movie $it","Type":"Movie"}"""
            }
            val totalField = reportedTotal?.let { "\"${if (plex) "totalSize" else "TotalRecordCount"}\":$it," }.orEmpty()
            val json = if(plex) """{"MediaContainer":{${totalField}"size":${if(offset == 0) 2 else 1},"Metadata":[$entries]}}"""
                else """{${totalField}"Items":[$entries]}"""
            mockk<okhttp3.Call> { every { execute() } returns Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(json.toResponseBody("application/json".toMediaType())).build() }
        }
        val repository = HomeServerRepository(RuntimeEnvironment.getApplication(), client, profiles)
        val connections = listOf(HomeServerKind.JELLYFIN, HomeServerKind.EMBY, HomeServerKind.PLEX).map { kind ->
            HomeServerConnection(connectionId = kind.name, displayName = kind.name, serverKind = kind,
                serverUrl = "https://example.invalid", userId = "test", accessToken = "test-only",
                collections = listOf(HomeServerCollection("movies", "Movies", "movies")))
        }
        repository.importCloudConnectionsJsonForProfile(profile, Gson().toJson(connections))
        withTimeout(5000) { repository.connections.first { it.size == 3 } }
        connections.forEach { connection ->
            reportedTotal = null
            val source = HomeServerRepository.buildCatalogSourceRef(connection, connection.collections.single())
            val first = repository.loadCatalogItems(source, 0, 2, propagateErrors = true)
            assertEquals(2, first.items.size)
            assertNull(first.totalCount)
            assertTrue("${connection.serverKind} stopped at its first page", first.hasMore)
            assertEquals(2, first.nextOffset)
            val last = repository.loadCatalogItems(source, first.nextOffset!!, 2, propagateErrors = true)
            assertEquals(1, last.items.size)
            assertFalse(last.hasMore)
            assertEquals(3, last.nextOffset)
            reportedTotal = 14439
            val counted = repository.loadCatalogItems(source, 0, 2, propagateErrors = true)
            assertEquals(14439, counted.totalCount)
            assertEquals(2, counted.items.size)
            assertTrue(counted.hasMore)
            reportedTotal = 0
            val empty = repository.loadCatalogItems(source, 3, 2, propagateErrors = true)
            assertEquals(0, empty.totalCount)
            assertTrue(empty.items.isEmpty())
            assertFalse(empty.hasMore)
        }
    }
}
