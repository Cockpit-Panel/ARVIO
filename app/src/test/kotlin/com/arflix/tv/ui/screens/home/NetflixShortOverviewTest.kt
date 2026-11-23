package com.arflix.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class NetflixShortOverviewTest {
    @Test fun keepsOnlyTheFirstSentence() {
        assertEquals(
            "Caught in a long war, two nations reach the pinnacle of strategic combat.",
            netflixShortOverview("Caught in a long war, two nations reach the pinnacle of strategic combat. Two leaders rise.")
        )
    }

    @Test fun aVeryShortFirstSentenceTakesTheNextOne() {
        assertEquals(
            "Hope is lost. A young farmer leaves home to join the rebellion against the empire.",
            netflixShortOverview("Hope is lost. A young farmer leaves home to join the rebellion against the empire. More follows.")
        )
    }

    @Test fun abbreviationsDoNotEndASentence() {
        assertEquals(
            "Dr. Strange and the U.S. Army must stop a threat that could end the world.",
            netflixShortOverview("Dr. Strange and the U.S. Army must stop a threat that could end the world. Then more.")
        )
    }

    @Test fun hebrewAndSingleSentenceTextsAreKept() {
        assertEquals("סיפור קצר על משפחה אחת שעוברת שנה קשה במיוחד בעיר.",
            netflixShortOverview("סיפור קצר על משפחה אחת שעוברת שנה קשה במיוחד בעיר. ועוד משפט."))
        assertEquals("No sentence end here", netflixShortOverview("No sentence end here"))
    }
}
