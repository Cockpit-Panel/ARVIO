package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsPolicyTest {
    @Test fun `screen names never contain route arguments`() {
        assertThat(AnalyticsPolicy.screen("details/movie/private-title?token=secret")).isEqualTo("details")
        assertThat(AnalyticsPolicy.screen("player?url=https://private.example")).isEqualTo("player")
        assertThat(AnalyticsPolicy.screen("settings/subscriptions")).isEqualTo("settings")
        assertThat(AnalyticsPolicy.screen("home")).isEqualTo("home")
    }
    @Test fun `unknown routes and missing routes are not sent`() {
        assertThat(AnalyticsPolicy.screen(null)).isNull()
        assertThat(AnalyticsPolicy.screen("https://provider.example")).isNull()
        assertThat(AnalyticsPolicy.screen("private-search-text")).isNull()
    }
}
