package com.arflix.tv.util

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsPreferencesTest {
    private lateinit var context: Context

    @Before fun setup() {
        val values = mutableMapOf<String, Boolean>()
        context = mockk()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.getBoolean(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { preferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.apply() } just Runs
        every { editor.commit() } returns true
        mockkObject(UsageAnalytics)
        every { UsageAnalytics.configure(any(), any()) } just Runs
    }

    @After fun cleanup() { unmockkObject(UsageAnalytics) }

    @Test fun `expanded usage is off by default even with existing crash consent`() {
        assertTrue(DiagnosticsManager.isReportingEnabled(context))
        assertFalse(DiagnosticsManager.isUsageEnabled(context))
        context.getSharedPreferences("arvio_privacy_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("diagnostics_and_usage_enabled", true).commit()
        assertFalse(DiagnosticsManager.isUsageEnabled(context))
    }

    @Test fun `usage opt in and opt out persist without changing crash preference`() {
        DiagnosticsManager.setUsageEnabled(context, true)
        assertTrue(DiagnosticsManager.isUsageEnabled(context))
        verify { UsageAnalytics.configure(context, true) }
        DiagnosticsManager.setUsageEnabled(context, false)
        assertFalse(DiagnosticsManager.isUsageEnabled(context))
        assertTrue(DiagnosticsManager.isReportingEnabled(context))
        verify { UsageAnalytics.configure(context, false) }
    }
}
