package com.arflix.tv.data.repository

import org.junit.Assert.*
import org.junit.Test

class HomeServerLoginFailureTest {
    @Test fun `Silo profile and PIN failures have actionable categories`() {
        for (message in listOf("username must include a profile suffix like username#profile", "profile not found: Family", "profile name is ambiguous")) {
            assertEquals(HomeServerLoginFailure.PROFILE, HomeServerLoginFailure.detect(401, """{"Error":"InvalidUsernameOrPassword","Message":"$message"}"""))
        }
        for (message in listOf("profile is PIN protected", "invalid profile PIN", "use username#profile and password#pin format")) {
            assertEquals(HomeServerLoginFailure.PIN, HomeServerLoginFailure.detect(401, """{"Message":"$message"}"""))
        }
    }
    @Test fun `wrong endpoint is distinct from bad credentials`() {
        assertEquals(HomeServerLoginFailure.ENDPOINT, HomeServerLoginFailure.detect(200, "<!doctype html><html>Silo</html>"))
        assertEquals(HomeServerLoginFailure.ENDPOINT, HomeServerLoginFailure.detect(404, "{}"))
        assertNull(HomeServerLoginFailure.detect(401, """{"Message":"Invalid username or password"}"""))
        assertNull(HomeServerLoginFailure.detect(500, """{"Message":"profile not found"}"""))
        assertNull(HomeServerLoginFailure.detect(401, "not json"))
    }
}
