package com.arflix.tv.ui.screens.profile

import com.arflix.tv.data.model.Profile
import org.junit.Assert.*
import org.junit.Test

class ProfileSelectionCompletionTest {
    private val ready = ProfileUiState(activeProfile = Profile(id = "selected", name = "Selected"))

    @Test fun matchingCompletedProfileCanEnterHome() {
        assertTrue(ready.canFinishSelection("selected"))
    }
    @Test fun unfinishedSwitchCannotEnterEvenWithMatchingProfile() {
        assertFalse(ready.copy(isSwitchingProfile = true).canFinishSelection("selected"))
    }
    @Test fun oldProfileCannotCompleteNewSelection() {
        assertFalse(ready.canFinishSelection("other"))
    }
    @Test fun missingSelectionCannotEnterHome() {
        assertFalse(ready.canFinishSelection(null))
        assertFalse(ProfileUiState().canFinishSelection("selected"))
    }
    @Test fun managementAndPinDialogBlockNavigation() {
        assertFalse(ready.copy(isManageMode = true).canFinishSelection("selected"))
        assertFalse(ready.copy(showPinDialog = true).canFinishSelection("selected"))
    }
}
