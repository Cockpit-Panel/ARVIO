package com.arflix.tv.ui.screens.tv.live

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LiveTvPlaybackSessionTest {
    private val player = mockk<Player>(relaxed = true)
    private val cancelRequests = mockk<() -> Unit>(relaxed = true)
    private val session = LiveTvPlaybackSession(player, cancelRequests)

    @Test fun `background stops loading and cancels transport once for pause then stop`() {
        every { player.playWhenReady } returns true
        session.suspend()
        session.suspend()
        verifyOrder { player.pause(); player.stop(); cancelRequests() }
        verify(exactly = 1) { player.stop(); cancelRequests() }
    }

    @Test fun `live resume prepares once at live edge`() {
        every { player.playWhenReady } returns true
        every { player.mediaItemCount } returns 1
        session.suspend()
        session.resume(isLive = true, allowed = true)
        session.resume(isLive = true, allowed = true)
        verifyOrder { player.stop(); player.seekToDefaultPosition(); player.prepare(); player.playWhenReady = true }
        verify(exactly = 1) { player.prepare() }
    }

    @Test fun `catchup keeps position and user paused intent`() {
        every { player.playWhenReady } returns false
        every { player.mediaItemCount } returns 1
        session.suspend()
        session.resume(isLive = false, allowed = true)
        verify(exactly = 0) { player.seekToDefaultPosition() }
        verify { player.prepare(); player.playWhenReady = false }
    }

    @Test fun `sports hidden resume does not open a connection`() {
        every { player.mediaItemCount } returns 1
        session.suspend()
        session.resume(isLive = true, allowed = false)
        verify(exactly = 0) { player.prepare() }
        session.resume(isLive = true, allowed = true)
        verify(exactly = 1) { player.prepare() }
    }

    @Test fun `unfinished source lookup does not prepare an empty player on return`() {
        every { player.mediaItemCount } returns 0
        session.suspend()
        session.resume(isLive = true, allowed = true)
        verify(exactly = 0) { player.prepare() }
    }

    @Test fun `background closes an actual in-flight streaming socket`() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newFixedThreadPool(2)
        val connections = com.arflix.tv.network.IptvPlaybackConnections()
        val client = OkHttpClient.Builder().addInterceptor(connections).build()
        val reading = CountDownLatch(1)
        try {
            val disconnected = executor.submit<Int> {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\nx".toByteArray())
                        flush()
                    }
                    input.read()
                }
            }
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/live").build())
            val consumer = executor.submit {
                try {
                    call.execute().use { response ->
                        val source = response.body!!.source()
                        source.readByte()
                        reading.countDown()
                        source.readByte()
                    }
                } catch (_: java.io.IOException) { }
            }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            LiveTvPlaybackSession(player) {
                kotlinx.coroutines.runBlocking { connections.cancelAllAsync(client).join() }
            }.suspend()
            assertTrue(call.isCanceled())
            assertEquals(-1, disconnected.get(5, TimeUnit.SECONDS))
            consumer.get(5, TimeUnit.SECONDS)
        } finally {
            connections.cancelAll()
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            server.close()
            executor.shutdownNow()
        }
    }
}
