package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.R
import com.arflix.tv.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starter rows for a kids profile, added once to that profile's own catalogs.
 *
 * The regular rows (trending, popular, addon catalogs) are mostly adult titles and
 * shrink to a handful of cards once filtered, so a new kids profile gets a few rows that
 * are kids content to begin with. They are plain TMDB genre catalogs: the parent can
 * hide, move, rename or remove them in Settings like any other row, and the profile's
 * [com.arflix.tv.data.model.Profile.kidsStarterRowsAdded] flag keeps a removed one from
 * coming back. Their titles still pass through [KidsContentFilter] like every row.
 */
@Singleton
class KidsStarterCatalogs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val catalogRepository: CatalogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts watching the active profile; call once from app startup. */
    fun start() {
        scope.launch {
            profileRepository.activeProfile
                .filterNotNull()
                .distinctUntilChanged()
                .collect { profile ->
                    if (profile.kidsLevel == null || profile.kidsStarterRowsAdded) return@collect
                    runCatching {
                        catalogRepository.appendTmdbCatalogs(profile.id, starters())
                        profileRepository.updateProfile(profile.copy(kidsStarterRowsAdded = true))
                    }.onFailure { AppLogger.w(TAG, "Kids starter rows failed: ${it.message}") }
                }
        }
    }

    private fun starters(): List<Triple<String, String, String>> = listOf(
        Triple(ROW_MOVIES, context.getString(R.string.kids_row_movies), "$TMDB/genre/10751-family/movie"),
        Triple(ROW_SHOWS, context.getString(R.string.kids_row_shows), "$TMDB/genre/10762-kids/tv"),
        Triple(ROW_ANIMATED, context.getString(R.string.kids_row_animated), "$TMDB/genre/16-animation/movie"),
        Triple(ROW_FAMILY_SHOWS, context.getString(R.string.kids_row_family_shows), "$TMDB/genre/10751-family/tv")
    )

    companion object {
        private const val TAG = "KidsStarterCatalogs"
        private const val TMDB = "https://www.themoviedb.org"
        const val ROW_MOVIES = "kids_movies"
        const val ROW_SHOWS = "kids_shows"
        const val ROW_ANIMATED = "kids_animated"
        const val ROW_FAMILY_SHOWS = "kids_family_shows"
    }
}
