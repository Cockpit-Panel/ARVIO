package com.arflix.tv.data.repository

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import android.content.Context
import android.content.ContextWrapper
import com.arflix.tv.data.telegram.TelegramAuthState
import com.arflix.tv.data.telegram.TelegramClient
import com.arflix.tv.data.telegram.TelegramConfig
import com.arflix.tv.network.OkHttpProvider
import com.arflix.tv.di.RepositoryAccessEntryPoint
import com.arflix.tv.util.profilesDataStore
import com.google.gson.Gson
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors

internal class HomeServerIntegrationChecks(private val context: Context) {

    fun telegramCredentialsAndNativeLibraryAreAvailable() {
        check(TelegramConfig.isConfigured) { "Telegram credentials missing from APK" }
        check(TelegramClient(context).isAvailable) { "Telegram native library missing from APK" }
    }

    fun telegramInitializesToSignInWithoutAnAccount() = runBlocking {
        val files = File(context.cacheDir, "telegram-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir() = files
        }
        val client = TelegramClient(isolatedContext)
        try {
            client.initialize()
            val state = withTimeout(30_000) {
                client.authState.first { it is TelegramAuthState.WaitPhone || it is TelegramAuthState.Error }
            }
            check(state == TelegramAuthState.WaitPhone) { "Telegram could not initialize: $state" }
        } finally {
            client.reset()
        }
    }

    fun allThreeServerLibrariesSurviveEncryptedStorageAndLoadOverHttp() = runBlocking {
        LibraryServer().use { server ->
            val profile = "server-test-${UUID.randomUUID()}"
            val activeProfileKey = stringPreferencesKey("active_profile_id")
            val previousProfile = context.profilesDataStore.data.first()[activeProfileKey]
            val profiles = EntryPointAccessors.fromApplication(context,
                RepositoryAccessEntryPoint::class.java).profileManager()
            val repository = HomeServerRepository(context, OkHttpProvider.client, profiles)
            try {
                context.profilesDataStore.edit { it[activeProfileKey] = profile }
                val servers = listOf(HomeServerKind.PLEX, HomeServerKind.JELLYFIN, HomeServerKind.EMBY).map { kind ->
                    HomeServerConnection(connectionId = kind.name, serverKind = kind,
                        serverUrl = server.url, accessToken = "fixture-token", userId = "member")
                }
                repository.importCloudConnectionsJsonForProfile(profile, Gson().toJson(servers))
                repository.refreshMissingLibraries()
                // Recreate the repository to exercise reading encrypted settings, not an in-memory result.
                val reopened = HomeServerRepository(context, OkHttpProvider.client, profiles)
                val saved = reopened.currentConnections()
                check(saved.size == 3) { "Saved connections were lost" }
                check(saved.all { it.isUsable && it.accessToken == "fixture-token" })
                val libraries = reopened.getSavedCatalogCandidates(saved)
                check(libraries.size == 3) { "Missing recovered libraries" }
                libraries.forEach { library ->
                    val page = reopened.loadCatalogItems(library.sourceRef, 0, 60, propagateErrors = true)
                    check(page.items.size == 1) { "${library.serverKind} returned no library items" }
                    check(page.items.single().title == "Test Film")
                    check(page.totalCount == 1)
                }
            } finally {
                repository.importCloudConnectionsJsonForProfile(profile, null)
                context.profilesDataStore.edit {
                    if (previousProfile == null) it.remove(activeProfileKey) else it[activeProfileKey] = previousProfile
                }
            }
        }
    }

    private class LibraryServer : AutoCloseable {
        private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${socket.localPort}"
        init {
            executor.submit {
                while (!socket.isClosed) {
                    val client = try { socket.accept() } catch (_: java.io.IOException) { break }
                    client.use {
                        val reader = it.getInputStream().bufferedReader()
                        val path = reader.readLine().split(' ')[1].substringBefore('?')
                        val headers = generateSequence { reader.readLine()?.takeIf(String::isNotEmpty) }.toList()
                        val authenticated = headers.any { line ->
                            (line.startsWith("X-Plex-Token:", true) || line.startsWith("X-Emby-Token:", true)) &&
                                line.substringAfter(':').trim() == "fixture-token"
                        }
                        val body = when (path) {
                            "/library/sections" -> """{"MediaContainer":{"Directory":[{"key":"1","title":"Movies","type":"movie"}]}}"""
                            "/Users/member/Views" -> """{"Items":[{"Id":"1","Name":"Movies","CollectionType":"movies"}]}"""
                            "/library/sections/1/all" -> """{"MediaContainer":{"totalSize":1,"Metadata":[{"ratingKey":"film","title":"Test Film","type":"movie"}]}}"""
                            "/Users/member/Items" -> """{"TotalRecordCount":1,"Items":[{"Id":"film","Name":"Test Film","Type":"Movie"}]}"""
                            else -> "{}"
                        }.toByteArray()
                        val status = if (authenticated) "200 OK" else "401 Unauthorized"
                        it.getOutputStream().apply {
                            write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }
        override fun close() {
            socket.close()
            executor.shutdownNow()
        }
    }
}
