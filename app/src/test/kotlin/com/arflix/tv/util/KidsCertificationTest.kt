package com.arflix.tv.util

import com.arflix.tv.data.api.TmdbContentRatingResult
import com.arflix.tv.data.api.TmdbContentRatingsResponse
import com.arflix.tv.data.api.TmdbReleaseDate
import com.arflix.tv.data.api.TmdbReleaseDatesResponse
import com.arflix.tv.data.api.TmdbReleaseDatesResult
import com.arflix.tv.data.model.KidsAgeLevel
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.Profile
import com.arflix.tv.data.repository.KidsContentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsCertificationTest {

    @Test
    fun `reads named and numeric certifications`() {
        assertEquals(0, KidsCertification.minimumAge("G"))
        assertEquals(7, KidsCertification.minimumAge("PG"))
        assertEquals(13, KidsCertification.minimumAge("PG-13"))
        assertEquals(7, KidsCertification.minimumAge("TV-Y7"))
        assertEquals(17, KidsCertification.minimumAge("TV-MA"))
        assertEquals(12, KidsCertification.minimumAge("12A"))
        assertEquals(16, KidsCertification.minimumAge("16"))
        assertEquals(15, KidsCertification.minimumAge("MA15+"))
        assertEquals(12, KidsCertification.minimumAge("PG12"))
        assertEquals(0, KidsCertification.minimumAge(" l "))
    }

    @Test
    fun `unreadable certifications are unknown`() {
        assertNull(KidsCertification.minimumAge(null))
        assertNull(KidsCertification.minimumAge(""))
        assertNull(KidsCertification.minimumAge("NR"))
        assertNull(KidsCertification.minimumAge("E"))
    }

    @Test
    fun `movie uses US when the own country is missing or unreadable`() {
        val response = TmdbReleaseDatesResponse(
            listOf(
                TmdbReleaseDatesResult("FR", listOf(TmdbReleaseDate("NR", 3))),
                TmdbReleaseDatesResult("US", listOf(TmdbReleaseDate("PG", 3)))
            )
        )
        assertEquals(7, KidsCertification.forMovie(response, "FR"))
        assertEquals(7, KidsCertification.forMovie(response, "DE"))
        assertNull(KidsCertification.forMovie(TmdbReleaseDatesResponse(), "US"))
    }

    @Test
    fun `the stricter of own country and US counts`() {
        val response = TmdbContentRatingsResponse(
            listOf(
                TmdbContentRatingResult("IL", "All"),
                TmdbContentRatingResult("DE", "16"),
                TmdbContentRatingResult("US", "TV-PG")
            )
        )
        assertEquals(10, KidsCertification.forTv(response, "IL"))
        assertEquals(16, KidsCertification.forTv(response, "DE"))
        assertEquals(10, KidsCertification.forTv(response, "US"))
    }

    @Test
    fun `filter state allows only known titles under the cap`() {
        val pg = MediaItem(id = 1, title = "pg", mediaType = MediaType.MOVIE)
        val pg13 = MediaItem(id = 2, title = "pg13", mediaType = MediaType.MOVIE)
        val unrated = MediaItem(id = 3, title = "nr", mediaType = MediaType.TV)
        val unknown = MediaItem(id = 4, title = "?", mediaType = MediaType.TV)
        val noTmdb = MediaItem(id = -5, title = "native", mediaType = MediaType.MOVIE)
        val ages = mapOf(
            KidsContentFilter.key("US", MediaType.MOVIE, 1) to 10,
            KidsContentFilter.key("US", MediaType.MOVIE, 2) to 13,
            KidsContentFilter.key("US", MediaType.TV, 3) to KidsContentFilter.UNRATED
        )
        val older = KidsContentFilter.State(KidsAgeLevel.OLDER_KIDS, "US", ages)
        assertTrue(older.allows(pg))
        assertFalse(older.allows(pg13))
        assertFalse(older.allows(unrated))
        assertFalse(older.allows(unknown))
        assertFalse(older.allows(noTmdb))
        assertTrue(older.isUnknown(unknown))
        assertTrue(KidsContentFilter.State(KidsAgeLevel.TEENS, "US", ages).allows(pg13))

        val regular = KidsContentFilter.State(null, "US", emptyMap())
        assertTrue(regular.allows(unknown))
        assertTrue(regular.allows(noTmdb))
    }

    @Test
    fun `profiles saved before the age level read as the default`() {
        val legacy = Profile(name = "kid", isKidsProfile = true, kidsAgeLevel = null)
        assertEquals(KidsAgeLevel.DEFAULT, legacy.kidsLevel)
        assertNull(Profile(name = "adult").kidsLevel)
        assertEquals(
            KidsAgeLevel.TEENS,
            Profile(name = "teen", isKidsProfile = true, kidsAgeLevel = "TEENS").kidsLevel
        )
    }
}
