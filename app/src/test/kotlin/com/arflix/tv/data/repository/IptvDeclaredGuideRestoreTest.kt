package com.arflix.tv.data.repository

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A playlist that names its guide only in its `#EXTM3U url-tvg=...` line got no guide at all
 * once its channels were kept in the SQLite store: startup skips the playlist parse, and the
 * saved guide address was only read back from a cache that already held guide data — which it
 * never could, because the guide needs that address to load in the first place.
 */
class IptvDeclaredGuideRestoreTest {

    @get:Rule val temp = TemporaryFolder()

    private val guideUrl = "http://guide.example/epg.xml.gz"
    private val config = IptvConfig(
        playlists = listOf(
            IptvPlaylistEntry(id = "list_1", name = "List 1", m3uUrl = "http://playlist.example/list.m3u8"),
        ),
    )

    private fun newRepository(filesDir: File): IptvRepository {
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.filesDir } returns filesDir
        val profileManager = mockk<ProfileManager>(relaxed = true)
        every { profileManager.getProfileIdSync() } returns "profile"
        return IptvRepository(context, mockk(relaxed = true), profileManager, mockk(relaxed = true))
    }

    private fun writeGuideCache(filesDir: File, json: String) {
        File(filesDir, "iptv_cache").apply { mkdirs() }
            .resolve("profile_iptv_cache.json")
            .writeText(json)
    }

    @Test fun `saved guide address comes back although no guide was stored yet`() {
        val filesDir = temp.newFolder()
        writeGuideCache(
            filesDir,
            """{"channels":[],"configSignature":"","discoveredEpgUrls":["$guideUrl"],"loadedAtEpochMs":1,"nowNext":{},"sourceSignature":""}""",
        )
        val repository = newRepository(filesDir)

        repository.restoreDiscoveredEpgUrlsFromCache(config)

        assertEquals(listOf(guideUrl), repository.playlistDeclaredEpgUrls())
    }

    @Test fun `a stored guide is skipped, not needed, to read the address`() {
        val filesDir = temp.newFolder()
        writeGuideCache(
            filesDir,
            """{"nowNext":{"ch":{"now":{"title":"A","startUtcMillis":1,"endUtcMillis":2}}},"discoveredEpgUrls":["$guideUrl"],"configSignature":"","sourceSignature":""}""",
        )
        val repository = newRepository(filesDir)

        repository.restoreDiscoveredEpgUrlsFromCache(config)

        assertEquals(listOf(guideUrl), repository.playlistDeclaredEpgUrls())
    }

    @Test fun `an address saved for another playlist source is not used`() {
        val filesDir = temp.newFolder()
        writeGuideCache(
            filesDir,
            """{"discoveredEpgUrls":["$guideUrl"],"configSignature":"","sourceSignature":"another-source"}""",
        )
        val repository = newRepository(filesDir)

        repository.restoreDiscoveredEpgUrlsFromCache(config)

        assertTrue(repository.playlistDeclaredEpgUrls().isEmpty())
    }

    @Test fun `no saved cache leaves the address unknown without failing`() {
        val repository = newRepository(temp.newFolder())

        repository.restoreDiscoveredEpgUrlsFromCache(config)

        assertTrue(repository.playlistDeclaredEpgUrls().isEmpty())
    }
}
