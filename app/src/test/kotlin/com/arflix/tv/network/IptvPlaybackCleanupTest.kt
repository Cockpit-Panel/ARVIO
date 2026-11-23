package com.arflix.tv.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class IptvPlaybackCleanupTest {
    private val requests = mockk<Dispatcher>()
    private val pool = mockk<ConnectionPool>(relaxed = true)
    private val client = mockk<OkHttpClient> {
        every { dispatcher } returns requests
        every { connectionPool } returns pool
    }

    @Test fun `TLS cancellation and eviction run off caller thread`() = runBlocking {
        val caller = Thread.currentThread()
        val cancellationThread = AtomicReference<Thread>()
        val evictionThread = AtomicReference<Thread>()
        val call = mockk<Call> {
            every { cancel() } answers { cancellationThread.set(Thread.currentThread()) }
        }
        every { requests.queuedCalls() } returns emptyList()
        every { requests.runningCalls() } returns listOf(call)
        every { pool.evictAll() } answers { evictionThread.set(Thread.currentThread()) }
        IptvPlaybackConnections().cancelAllAsync(client).join()
        assertNotNull(cancellationThread.get())
        assertNotNull(evictionThread.get())
        assertNotSame(caller, cancellationThread.get())
        assertNotSame(caller, evictionThread.get())
        verify(exactly = 1) { call.cancel(); pool.evictAll() }
    }

    @Test fun `deferred cleanup snapshots requests and leaves resumed stream alone`() = runTest {
        val old = mockk<Call>(relaxed = true)
        val resumed = mockk<Call>(relaxed = true)
        every { requests.queuedCalls() } returns listOf(old)
        every { requests.runningCalls() } returns listOf(old)
        val job = IptvPlaybackConnections().cancelAllAsync(client, StandardTestDispatcher(testScheduler))
        verify(exactly = 0) { old.cancel(); pool.evictAll() }
        every { requests.runningCalls() } returns listOf(resumed)
        every { requests.queuedCalls() } returns emptyList()
        job.join()
        verify(exactly = 1) { old.cancel(); pool.evictAll() }
        verify(exactly = 0) { resumed.cancel(); requests.cancelAll() }
    }

    @Test fun `one failed cancellation does not strand remaining streams`() = runTest {
        val broken = mockk<Call> { every { cancel() } throws IllegalStateException("socket close failed") }
        val other = mockk<Call>(relaxed = true)
        every { requests.runningCalls() } returns listOf(broken, other)
        every { requests.queuedCalls() } returns emptyList()
        IptvPlaybackConnections().cancelAllAsync(client, StandardTestDispatcher(testScheduler)).join()
        verify(exactly = 1) { other.cancel(); pool.evictAll() }
    }
}
