package com.arflix.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeServerIntegrationDeviceTest {
    private val checks = HomeServerIntegrationChecks(ApplicationProvider.getApplicationContext<Context>())

    @Test fun telegramCredentialsAndNativeLibraryAreAvailable() =
        checks.telegramCredentialsAndNativeLibraryAreAvailable()

    @Test fun telegramInitializesToSignInWithoutAnAccount() =
        checks.telegramInitializesToSignInWithoutAnAccount()

    @Test fun allThreeServerLibrariesSurviveEncryptedStorageAndLoadOverHttp() =
        checks.allThreeServerLibrariesSurviveEncryptedStorageAndLoadOverHttp()
}
