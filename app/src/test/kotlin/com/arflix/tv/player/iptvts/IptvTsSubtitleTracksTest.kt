package com.arflix.tv.player.iptvts

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.Util
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * A broadcast channel carrying Hebrew and Russian DVB subtitles showed only Hebrew: in HLS mode
 * Media3's TS extractor keeps one track per stream type (the lowest PID). ARVIO's copy keeps each.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class IptvTsSubtitleTracksTest {

    @Test fun `every DVB subtitle language of an HLS segment becomes a track`() {
        val languages = textTrackLanguages(IptvTsExtractorUnderTest.ARVIO)

        assertEquals(listOf("he", "ru"), languages)
    }

    @Test fun `the stock extractor keeps only the lowest PID, which is the bug this copy fixes`() {
        val languages = textTrackLanguages(IptvTsExtractorUnderTest.MEDIA3)

        assertEquals(listOf("he"), languages)
    }

    @Test fun `a subtitle service keeps its track id in a rendition that carries it on another PID`() {
        // HLS keeps the text track ids announced first and discards samples for unknown ids, so a
        // quality switch to a rendition with different subtitle PIDs must yield the same ids.
        val first = textTrackIds(subtitles = listOf("heb" to 0x401, "rus" to 0x402))
        val second = textTrackIds(subtitles = listOf("rus" to 0x511, "heb" to 0x512))

        assertEquals(setOf("he", "ru"), first.keys)
        assertEquals(first, second)
    }

    @Test fun `a repeated program table does not add tracks`() {
        val extractorTracks = textTrackIds(subtitles = listOf("heb" to 0x401, "rus" to 0x402), pmtRepeats = 3)

        assertEquals(setOf("he", "ru"), extractorTracks.keys)
    }

    /** Language -> track id of the text tracks an HLS-mode ARVIO extractor announces. */
    private fun textTrackIds(subtitles: List<Pair<String, Int>>, pmtRepeats: Int = 1): Map<String?, Int> {
        val extractor = TsExtractor(
            TsExtractor.MODE_HLS, TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA, SubtitleParser.Factory.UNSUPPORTED,
            TimestampAdjuster(0), DefaultTsPayloadReaderFactory(0, emptyList()), TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
        )
        val output = RecordingOutput()
        extractor.init(output)
        var stream = patPacket()
        repeat(pmtRepeats) { stream += pmtPacket(subtitles) }
        val input = DefaultExtractorInput(ByteArrayReader(stream), 0, stream.size.toLong())
        val position = PositionHolder()
        while (extractor.read(input, position) == Extractor.RESULT_CONTINUE) Unit
        val text = output.tracks.filter { it.type == C.TRACK_TYPE_TEXT }
        assertEquals("duplicate text tracks", text.size, text.map { it.id }.toSet().size)
        return text.associate { it.format?.language to it.id }
    }

    private enum class IptvTsExtractorUnderTest { ARVIO, MEDIA3 }

    private fun textTrackLanguages(which: IptvTsExtractorUnderTest): List<String?> {
        val payloadReaders = DefaultTsPayloadReaderFactory(0, emptyList())
        val extractor: Extractor = when (which) {
            IptvTsExtractorUnderTest.ARVIO -> TsExtractor(
                TsExtractor.MODE_HLS, TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA, SubtitleParser.Factory.UNSUPPORTED,
                TimestampAdjuster(0), payloadReaders, TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
            )
            IptvTsExtractorUnderTest.MEDIA3 -> androidx.media3.extractor.ts.TsExtractor(
                androidx.media3.extractor.ts.TsExtractor.MODE_HLS,
                androidx.media3.extractor.ts.TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
                SubtitleParser.Factory.UNSUPPORTED,
                TimestampAdjuster(0), payloadReaders,
                androidx.media3.extractor.ts.TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
            )
        }
        val output = RecordingOutput()
        extractor.init(output)
        val stream = patPacket() + pmtPacket()
        val input = DefaultExtractorInput(ByteArrayReader(stream), 0, stream.size.toLong())
        val position = PositionHolder()
        while (extractor.read(input, position) == Extractor.RESULT_CONTINUE) Unit
        return output.tracks.filter { it.type == C.TRACK_TYPE_TEXT }.map { it.format?.language }
    }

    // ── A two-packet MPEG-TS: PAT -> PMT with video and two DVB subtitle streams ─────────────

    private fun patPacket(): ByteArray = tsPacket(
        pid = 0,
        section = section(
            tableId = 0x00,
            idField = 1, // transport_stream_id
            body = byteArrayOf(0x00, 0x01, (0xE0 or (PMT_PID shr 8)).toByte(), (PMT_PID and 0xFF).toByte()),
        ),
    )

    private fun pmtPacket(subtitles: List<Pair<String, Int>> = listOf("heb" to 0x401, "rus" to 0x402)): ByteArray {
        val video = esEntry(streamType = 0x1B, pid = 0x100, esInfo = byteArrayOf())
        val body = byteArrayOf(
            (0xE0 or (0x100 shr 8)).toByte(), 0x00, // PCR PID
            0xF0.toByte(), 0x00, // program_info_length = 0
        ) + video + subtitles.fold(byteArrayOf()) { acc, (language, pid) ->
            acc + esEntry(0x06, pid, dvbSubtitleDescriptor(language))
        }
        return tsPacket(pid = PMT_PID, section = section(tableId = 0x02, idField = 1, body = body))
    }

    private fun esEntry(streamType: Int, pid: Int, esInfo: ByteArray): ByteArray = byteArrayOf(
        streamType.toByte(),
        (0xE0 or (pid shr 8)).toByte(), (pid and 0xFF).toByte(),
        (0xF0 or (esInfo.size shr 8)).toByte(), (esInfo.size and 0xFF).toByte(),
    ) + esInfo

    private fun dvbSubtitleDescriptor(language: String): ByteArray =
        byteArrayOf(0x59, 8) + language.toByteArray(Charsets.US_ASCII) + byteArrayOf(0x10, 0x00, 0x01, 0x00, 0x01)

    /** A PSI section with section_syntax_indicator set and a valid MPEG-2 CRC. */
    private fun section(tableId: Int, idField: Int, body: ByteArray): ByteArray {
        val length = 5 + body.size + 4 // id(2) version(1) section numbers(2) + body + CRC
        val withoutCrc = byteArrayOf(
            tableId.toByte(),
            (0xB0 or (length shr 8)).toByte(), (length and 0xFF).toByte(),
            (idField shr 8).toByte(), (idField and 0xFF).toByte(),
            0xC1.toByte(), 0x00, 0x00,
        ) + body
        val crc = Util.crc32(withoutCrc, 0, withoutCrc.size, -1)
        return withoutCrc + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
    }

    private fun tsPacket(pid: Int, section: ByteArray): ByteArray {
        val header = byteArrayOf(0x47, (0x40 or (pid shr 8)).toByte(), (pid and 0xFF).toByte(), 0x10, 0x00)
        val packet = ByteArray(188) { 0xFF.toByte() }
        (header + section).copyInto(packet)
        return packet
    }

    private class ByteArrayReader(private val data: ByteArray) : DataReader {
        private var position = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, data.size - position)
            data.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    private class RecordingOutput : ExtractorOutput {
        val tracks = mutableListOf<RecordingTrack>()
        override fun track(id: Int, type: Int): TrackOutput =
            tracks.firstOrNull { it.id == id } ?: RecordingTrack(id, type).also { tracks += it }
        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) = Unit
    }

    private class RecordingTrack(val id: Int, val type: Int) : TrackOutput {
        var format: Format? = null
        override fun format(format: Format) { this.format = format }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            input.read(ByteArray(length), 0, length)
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) = data.skipBytes(length)
        override fun sampleMetadata(
            timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?,
        ) = Unit
    }

    private companion object {
        const val PMT_PID = 0x1000
    }
}
