package com.arflix.tv.util

import io.ktor.events.EventDefinition
import io.ktor.events.Events
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsDependencyCompatibilityTest {
    @Test fun `Ktor event subscriptions work with Firebase coroutine dependencies`() {
        val events = Events()
        val event = EventDefinition<String>()
        var observed = ""
        val listener: (String) -> Unit = { observed = it }
        events.subscribe(event, listener)
        events.raise(event, "ready")
        assertEquals("ready", observed)
        events.unsubscribe(event, listener)
    }
}
