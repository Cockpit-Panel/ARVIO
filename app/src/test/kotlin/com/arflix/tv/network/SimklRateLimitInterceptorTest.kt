package com.arflix.tv.network

import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InterruptedIOException

class SimklRateLimitInterceptorTest {
    private val request = Request.Builder().url("https://example.invalid/sync/playback").build()
    private val call = mockk<Call> { every { isCanceled() } returns false }
    private val chain = mockk<Interceptor.Chain> {
        every { request() } returns this@SimklRateLimitInterceptorTest.request
        every { call() } returns this@SimklRateLimitInterceptorTest.call
    }
    private fun response(code: Int, retryAfter: String = "1") = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .header("Retry-After", retryAfter).body("".toResponseBody()).build()

    @Test fun repeated429IsBoundedAndDelaysTheNextRequest() {
        var now = 0L
        val limiter = SimklRateLimitInterceptor({ now }, { now += it })
        val times = mutableListOf<Long>()
        every { chain.proceed(any()) } answers {
            times += now
            when (times.size) {
                1 -> response(429, "2")
                2 -> response(429, "120")
                else -> response(200)
            }
        }
        limiter.intercept(chain).use { assertEquals(429, it.code) }
        limiter.intercept(chain).close()
        assertEquals(listOf(0L, 2_000L, 122_000L), times)
    }

    @Test fun requestsRemainSpacedAfterSuccessfulRetry() {
        var now = 0L
        val limiter = SimklRateLimitInterceptor({ now }, { now += it })
        val times = mutableListOf<Long>()
        every { chain.proceed(any()) } answers {
            times += now
            response(if (times.size == 1) 429 else 200)
        }
        limiter.intercept(chain).close()
        limiter.intercept(chain).close()
        assertEquals(listOf(0L, 1_000L, 2_000L), times)
    }

    @Test(expected = InterruptedIOException::class)
    fun cancellationStopsWaitingWithoutSendingAnotherRequest() {
        var now = 0L
        val limiter = SimklRateLimitInterceptor({ now }, {
            now += it
            every { call.isCanceled() } returns true
        })
        every { chain.proceed(any()) } returns response(200)
        limiter.intercept(chain).close()
        limiter.intercept(chain)
    }

    @Test fun anotherRequestDuringBackoffCannotCollideWithTheRetry() {
        var now = 0L
        var interleaved = false
        val times = mutableListOf<Long>()
        val other = mockk<Interceptor.Chain>()
        every { other.request() } returns request
        every { other.call() } returns call
        every { other.proceed(any()) } answers { times += now; response(200) }
        var attempts = 0
        every { chain.proceed(any()) } answers {
            times += now
            response(if (attempts++ == 0) 429 else 200, "2")
        }
        lateinit var limiter: SimklRateLimitInterceptor
        limiter = SimklRateLimitInterceptor({ now }, {
            now += it
            if (!interleaved) {
                interleaved = true
                limiter.intercept(other).close()
            }
        })
        limiter.intercept(chain).close()
        assertEquals(listOf(0L, 2_000L, 3_000L), times)
    }
}
