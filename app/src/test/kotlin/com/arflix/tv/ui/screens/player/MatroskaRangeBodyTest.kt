package com.arflix.tv.ui.screens.player

import com.arflix.tv.ui.screens.player.subtitles.MatroskaIndexSource
import com.arflix.tv.ui.screens.player.subtitles.MatroskaSubtitleIndex
import com.arflix.tv.ui.screens.player.subtitles.SubtitleSyncMatcher
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class MatroskaRangeBodyTest {
    private fun response(range: String, text: String): Response = Response.Builder()
        .request(Request.Builder().url("https://example.com/video.mkv").build())
        .protocol(Protocol.HTTP_1_1).code(206).message("Partial Content")
        .header("Content-Range", range)
        .body(object : ResponseBody() {
            private val buffer = Buffer().writeUtf8(text)
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = buffer
        }).build()

    @Test fun acceptsExactAndEofRanges() {
        response("bytes 10-13/14", "abcd").use {
            assertArrayEquals("abcd".toByteArray(), MatroskaIndexSource.readRangeBody(it, 10, 8))
        }
    }

    @Test fun rejectsOversizedWrongOffsetAndTruncatedReplies() {
        listOf("bytes 0-3/20" to "abcd", "bytes 10-19/20" to "abcdefghij",
            "bytes 10-13/20" to "abcdefgh", "bytes 10-13/20" to "ab").forEach { (range, body) ->
            response(range, body).use { assertNull(MatroskaIndexSource.readRangeBody(it, 10, 4)) }
        }
    }

    @Test fun samplingIncludesTheEndEvenJustBelowTwiceTheLimit() {
        val cues = List(119) { SubtitleSyncMatcher.TimedCue(it * 1000L, it * 1000L + 500, "") }
        val sampled = MatroskaSubtitleIndex.sampleCues(cues, 60)
        assertEquals(60, sampled.size)
        assertEquals(cues.first(), sampled.first())
        assertEquals(cues.last(), sampled.last())
        assertEquals(sampled.size, sampled.distinct().size)
    }
}
