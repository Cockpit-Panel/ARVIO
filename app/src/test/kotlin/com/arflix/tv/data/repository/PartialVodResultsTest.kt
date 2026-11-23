package com.arflix.tv.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PartialVodResultsTest {
    @Test fun slowPortalDoesNotEraseCompletedProviders() = runTest {
        val results = withPartialVodResults<String>(100) { publish ->
            publish(listOf("xtream"))
            delay(10)
            publish(listOf("xtream", "first portal"))
            delay(1000)
            error("unreachable")
        }
        assertEquals(listOf("xtream", "first portal"), results)
    }

    @Test fun successfulLookupReturnsAllResults() = runTest {
        assertEquals(listOf("xtream", "stalker"), withPartialVodResults<String>(100) { publish ->
            publish(listOf("xtream"))
            listOf("xtream", "stalker")
        })
    }

    @Test fun providerExceptionPreservesCompletedResults() = runTest {
        assertEquals(listOf("xtream"), withPartialVodResults<String>(100) { publish ->
            publish(listOf("xtream"))
            throw IllegalStateException("portal unavailable")
        })
    }

    @Test fun callerCancellationStillPropagates() = runTest {
        try {
            withTimeout(10) {
                withPartialVodResults<String>(1000) { publish ->
                    publish(listOf("xtream"))
                    delay(100)
                    emptyList()
                }
            }
            fail("Caller cancellation must propagate")
        } catch (_: TimeoutCancellationException) {
            // Expected: leaving Details cancels the lookup, rather than publishing stale results.
        }
    }
}
