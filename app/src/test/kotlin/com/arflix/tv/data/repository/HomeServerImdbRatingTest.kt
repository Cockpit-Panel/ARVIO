package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HomeServerImdbRatingTest {
    private val api = mockk<TmdbApi>()
    private val repository = spyk(MediaRepository(mockk(relaxed = true), api, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)))

    @Test fun positiveServerIdentityLoadsRealImdbInsteadOfCommunityScore() = runTest {
        coEvery { repository.getImdbRating(MediaType.MOVIE, 42, null) } returns "8.2"
        val item = MediaItem(42, "Server movie", isHomeServer = true, tmdbRating = "7.4")
        assertEquals("8.2", repository.getImdbRating(item))
    }

    @Test fun nativeServerIdentityCanUseItsDirectImdbId() = runTest {
        coEvery { repository.getImdbRating(MediaType.TV, -42, "tt12345") } returns "8.1"
        val item = MediaItem(-42, "Server series", mediaType = MediaType.TV,
            isHomeServer = true, homeServerImdbId = " tt12345 ")
        assertEquals("8.1", repository.getImdbRating(item))
    }

    @Test fun existingValidImdbAvoidsAnotherLookup() = runTest {
        assertEquals("7.1", repository.getImdbRating(MediaItem(42, "Movie", imdbRating = "7.1")))
        coVerify(exactly = 0) { repository.getImdbRating(any(), any(), any()) }
    }

    @Test fun unknownNativeIdentityDoesNotInventARating() = runTest {
        assertNull(repository.getImdbRating(MediaItem(-42, "")))
        assertNull(repository.getImdbRating(MediaItem(-42, "", isHomeServer = true, tmdbRating = "9.0")))
        coVerify(exactly = 0) { repository.getImdbRating(any(), any(), any()) }
    }
}
