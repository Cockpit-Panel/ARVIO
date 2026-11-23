@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arflix.tv.ui.screens.tv.live

import android.net.Uri
import android.text.TextUtils
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import java.io.EOFException
import com.arflix.tv.player.iptvts.TsExtractor as IptvTsExtractor

/**
 * HLS segment extractors for IPTV. MPEG-TS segments go to ARVIO's copy of Media3's TS extractor,
 * which keeps every DVB subtitle language of a channel (Media3's keeps one per stream type, the
 * lowest PID, so a second subtitle language never appeared). Every other segment type is handled
 * by Media3's [DefaultHlsExtractorFactory] exactly as before.
 */
internal class IptvHlsExtractorFactory(
    private val payloadReaderFactoryFlags: Int,
    private val exposeCea608WhenMissingDeclarations: Boolean,
) : HlsExtractorFactory {

    private val fallback = DefaultHlsExtractorFactory(payloadReaderFactoryFlags, exposeCea608WhenMissingDeclarations)
    private var subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var parseSubtitlesDuringExtraction = false

    override fun createExtractor(
        uri: Uri,
        format: Format,
        muxedCaptionFormats: List<Format>?,
        timestampAdjuster: TimestampAdjuster,
        responseHeaders: Map<String, List<String>>,
        sniffingExtractorInput: ExtractorInput,
        playerId: PlayerId,
    ): HlsMediaChunkExtractor {
        val tsExtractor = createTsExtractor(format, muxedCaptionFormats, timestampAdjuster)
        val isTs = try {
            tsExtractor.sniff(sniffingExtractorInput)
        } catch (_: EOFException) {
            false
        } finally {
            sniffingExtractorInput.resetPeekPosition()
        }
        if (isTs) return TsChunkExtractor(tsExtractor)
        return fallback.createExtractor(
            uri, format, muxedCaptionFormats, timestampAdjuster, responseHeaders, sniffingExtractorInput, playerId,
        )
    }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): HlsExtractorFactory {
        this.subtitleParserFactory = subtitleParserFactory
        fallback.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    override fun experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction: Boolean): HlsExtractorFactory {
        this.parseSubtitlesDuringExtraction = parseSubtitlesDuringExtraction
        fallback.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction)
        return this
    }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(
        codecsToParseWithinGopSampleDependencies: Int,
    ): HlsExtractorFactory {
        fallback.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
        return this
    }

    override fun getOutputTextFormat(sourceFormat: Format): Format = fallback.getOutputTextFormat(sourceFormat)

    /** Same configuration as Media3 1.9.0's `DefaultHlsExtractorFactory.createTsExtractor`. */
    private fun createTsExtractor(
        format: Format,
        declaredCaptionFormats: List<Format>?,
        timestampAdjuster: TimestampAdjuster,
    ): IptvTsExtractor {
        var flags = DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM or payloadReaderFactoryFlags
        val captionFormats = when {
            declaredCaptionFormats != null -> {
                // The playlist declares closed caption renditions: ignore the stream's descriptors.
                flags = flags or DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS
                declaredCaptionFormats
            }
            exposeCea608WhenMissingDeclarations ->
                listOf(Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build())
            else -> emptyList()
        }
        val codecs = format.codecs
        if (!TextUtils.isEmpty(codecs)) {
            // Streams declared in the TS but absent from the codecs attribute are ignored.
            if (!MimeTypes.containsCodecsCorrespondingToMimeType(codecs, MimeTypes.AUDIO_AAC)) {
                flags = flags or DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM
            }
            if (!MimeTypes.containsCodecsCorrespondingToMimeType(codecs, MimeTypes.VIDEO_H264)) {
                flags = flags or DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM
            }
        }
        var extractorFlags = 0
        val parserFactory = if (parseSubtitlesDuringExtraction) {
            subtitleParserFactory
        } else {
            extractorFlags = extractorFlags or IptvTsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA
            SubtitleParser.Factory.UNSUPPORTED
        }
        return IptvTsExtractor(
            IptvTsExtractor.MODE_HLS,
            extractorFlags,
            parserFactory,
            timestampAdjuster,
            DefaultTsPayloadReaderFactory(flags, captionFormats),
            IptvTsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
        )
    }
}

/**
 * Media3's `BundledHlsMediaChunkExtractor` recognises its own TS extractor by class to reuse it
 * across segments (and throws when asked to recreate an unknown one), so ARVIO's copy gets this
 * equivalent wrapper: reused across segments, never recreated.
 */
private class TsChunkExtractor(private val extractor: IptvTsExtractor) : HlsMediaChunkExtractor {
    private val positionHolder = PositionHolder()

    override fun init(extractorOutput: ExtractorOutput) = extractor.init(extractorOutput)

    override fun read(extractorInput: ExtractorInput): Boolean =
        extractor.read(extractorInput, positionHolder) == Extractor.RESULT_CONTINUE

    override fun isPackedAudioExtractor(): Boolean = false

    override fun isReusable(): Boolean = true

    override fun recreate(): HlsMediaChunkExtractor =
        throw IllegalStateException("A reusable TS extractor is never recreated")

    override fun onTruncatedSegmentParsed() = extractor.seek(0, 0)
}
