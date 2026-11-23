package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLHandshakeException
import org.junit.Test

class CrashReportFilterTest {
    @Test
    fun `drops coroutine cancellations`() {
        val throwable = CancellationException("Job was cancelled")

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isFalse()
        assertThat(CrashReportFilter.shouldSendCrashReport(throwable)).isFalse()
    }

    @Test
    fun `drops expected playback diagnostics`() {
        val throwable = IllegalStateException("Playback error displayed")

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isFalse()
    }

    @Test
    fun `drops expected auth and backend diagnostics`() {
        val throwable = RuntimeException(
            "invalid JWT: unable to parse or verify signature, token has invalid claims: token is expired"
        )

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isFalse()
        assertThat(CrashReportFilter.shouldSendCrashReport(throwable)).isFalse()
    }

    @Test
    fun `drops expected trakt and provider diagnostics`() {
        val throwable = IllegalStateException("Trakt token request failed: HTTP 429 Too Many Requests")

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isFalse()
    }

    @Test
    fun `drops expected local rate limit backoff diagnostics`() {
        val throwable = IllegalStateException("Exponential backoff active: wait 300000ms")

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isFalse()
    }

    @Test
    fun `drops provider network failures from handled reports`() {
        val throwable = IllegalStateException(
            "Source lookup failed",
            UnknownHostException("Unable to resolve host \"provider.example\"")
        )

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isFalse()
    }

    @Test
    fun `drops supabase chain validation failures from handled reports`() {
        val throwable = RuntimeException(
            "HTTP request failed",
            SSLHandshakeException("Chain validation failed")
        )

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isFalse()
    }

    @Test
    fun `keeps real fatal crashes even when message resembles provider failure`() {
        val throwable = UnknownHostException("Unable to resolve host \"provider.example\"")

        assertThat(CrashReportFilter.shouldSendCrashReport(throwable, isFatal = true)).isTrue()
    }

    @Test
    fun `keeps fatal crashes with ignored message fragments`() {
        val throwable = IllegalStateException("Trakt token request failed")

        assertThat(CrashReportFilter.shouldSendCrashReport(throwable, isFatal = true)).isTrue()
    }

    @Test
    fun `keeps unexpected app bugs`() {
        val throwable = NullPointerException("items was null")

        assertThat(CrashReportFilter.shouldReportHandledException(throwable)).isTrue()
        assertThat(CrashReportFilter.shouldSendCrashReport(throwable)).isTrue()
    }

    @Test
    fun `drops non fatal message only events`() {
        assertThat(CrashReportFilter.shouldSendCrashReport(null)).isFalse()
    }

    @Test
    fun `keeps genuine crashes regardless of expected provider failure`() {
        val throwable = UnknownHostException("Unable to resolve host provider.example")

        assertThat(
            CrashReportFilter.shouldSendCrashReport(
                throwable = throwable,
                isFatal = true
            )
        ).isTrue()
    }

    @Test
    fun `handled fingerprints ignore changing messages at the same app callsite`() {
        val frame = StackTraceElement("com.arflix.tv.data.Repository", "load", "Repository.kt", 42)
        val first = IllegalStateException("provider response 1").apply { stackTrace = arrayOf(frame) }
        val second = IllegalStateException("provider response 2").apply { stackTrace = arrayOf(frame) }

        assertThat(CrashReportFilter.handledEventFingerprint(first))
            .isEqualTo(CrashReportFilter.handledEventFingerprint(second))
    }

    @Test
    fun `handled installation sampling is deterministic and respects boundaries`() {
        val seed = "installation-a"
        val fingerprint = "failure-a"

        val first = CrashReportFilter.isSelectedForHandledSample(seed, fingerprint, 50)
        val second = CrashReportFilter.isSelectedForHandledSample(seed, fingerprint, 50)
        assertThat(second).isEqualTo(first)
        assertThat(CrashReportFilter.isSelectedForHandledSample(seed, fingerprint, 0)).isFalse()
        assertThat(CrashReportFilter.isSelectedForHandledSample(seed, fingerprint, 1_000)).isTrue()
    }
}
