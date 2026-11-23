package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbListResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * TMDB offers two date windows per media type and they answer different questions. The app needs
 * both, so the fix for the decade filter (B34) added the second pair instead of renaming the first.
 *
 * This is the guard for the half that did NOT change: the home screen's anime rows ask which shows
 * had a new episode lately, and nothing about that is a question about when a show started.
 */
class DiscoverDateWindowTest {
    private val api = mockk<TmdbApi>(relaxed = true)
    private val repository = MediaRepository(mockk(relaxed = true), api, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    @Test fun theHomeScreensAnimeRowStillAsksWhenAnEpisodeAired() = runBlocking {
        coEvery {
            api.discoverTv(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any())
        } returns TmdbListResponse(results = emptyList())

        repository.loadHomeCategoryPage("trending_anime", page = 1)

        coVerify(exactly = 1) {
            api.discoverTv(
                any(), watchProviders = any(), watchRegion = any(), sortBy = any(),
                genres = "16", people = any(), originalLanguage = any(), year = any(),
                minVoteCount = any(), minVoteAverage = any(), maxVoteAverage = any(),
                keywords = any(), language = any(), page = any(),
                // The row lives on the episode date — renaming it to first_air_date would have
                // turned "currently airing" into "started in the last 18 months", silently.
                airDateGte = matchNullable<String> { it != null }, airDateLte = any(),
                firstAirDateGte = null, firstAirDateLte = null
            )
        }
    }

    /** And the other half: the window the discover grid sends is the one the card prints. */
    @Test fun theDiscoverWrapperPassesThePremiereWindowOnItsOwnParameters() = runBlocking {
        coEvery {
            api.discoverMovies(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns TmdbListResponse(results = emptyList())

        repository.discoverMovies(
            primaryReleaseDateGte = "1990-01-01",
            primaryReleaseDateLte = "1999-12-31"
        )

        coVerify(exactly = 1) {
            api.discoverMovies(
                any(), genres = any(), crew = any(), sortBy = any(), minVoteCount = any(),
                minVoteAverage = any(), maxVoteAverage = any(), certificationCountry = any(),
                certificationLte = any(), keywords = any(), originalLanguage = any(), year = any(),
                releaseDateGte = null, releaseDateLte = null,
                primaryReleaseDateGte = "1990-01-01", primaryReleaseDateLte = "1999-12-31",
                watchProviders = any(), watchRegion = any(), language = any(), page = any()
            )
        }
    }
}
