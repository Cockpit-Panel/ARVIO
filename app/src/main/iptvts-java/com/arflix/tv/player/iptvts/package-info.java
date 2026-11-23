/**
 * Vendored copy of media3 1.9.0's MPEG-TS extractor ({@code androidx.media3.extractor.ts}), used for
 * IPTV HLS segments. In HLS mode the stock extractor keeps one track per stream type, so a channel
 * carrying several DVB subtitle languages exposed only the lowest PID; this copy gives each DVB
 * subtitle PID its own track (search for "ARVIO:" in TsExtractor). TsDurationReader and
 * TsBinarySearchSeeker are copied unchanged because they are package-private upstream. Keep in sync
 * when bumping the media3 version: re-copy the three files from the new tag and re-apply the
 * "ARVIO:" changes.
 */
@NonNullApi
package com.arflix.tv.player.iptvts;

import androidx.media3.common.util.NonNullApi;
