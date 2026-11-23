# Sports schedule regression verification - 2026-09-12

## Findings and changes

- Sentry event `c5f6671a419d4b02a1d3c70cb496cbf2` fails in Compose's two-dimensional, beyond-bounds focus search while consulting a detached layout. Sports cards now handle directional navigation explicitly, materialize the target row/card, then request focus. Artwork failures no longer reparent focused cards.
- Schedule work now starts on the TV guide instead of waiting for Sports to be selected. Existing results remain visible while enrichment finishes. EPG scan batches avoid empty channel queries, and the cache key includes the guide coverage bucket.
- Backend fixture and broadcast-day requests use bounded concurrency (four), with the same date coverage and no response-row truncation.
- Fighting fixtures use their league to recognize boxing. Country suffix aliases such as DAZN UK are matched to their own regional station variants. A plus service remains distinct from its ordinary cable-channel namesake.

## Verified

- 64 Android sports unit tests passed.
- 47 web/backend sports tests passed; web TypeScript check passed.
- Three sports refresh/navigation instrumentation tests passed on the Android TV emulator, including repeated D-pad movement through offscreen rows during metadata refresh and an assertion that focus reached the final sport.
- One real-data instrumentation test passed: 49,943 captured provider channel labels and 3,247 metadata entries. Garcia-Benn is classified as Boxing, has eight broadcaster-matched channel variants, and its actual poster rendered on the emulator.
- Same-input web matcher comparison: 189 available events before, 199 after; events with artwork increased from 176 to 186. Android returned 200 available events at its later test time. These are snapshot/time-dependent counts, not a guarantee of full upstream coverage.
- Full cold Android catalogue matching measured 11,489 ms on the low-memory emulator while a release build was running on the host. This is NOT an instant-load or physical-TV benchmark. Public metadata responded in approximately 2 seconds; the cold provider channel-list download took approximately 14 seconds.
- Sideload release APK built and signature verified: SHA-256 certificate `9778d7533d4bc1aee80c1d2d7043fb22cba3b79cd11911d3b131c1316d5f17c1`.

## Limits and deployment

The real-data test used channel labels, not provider playback requests. Broadcaster matches are possible channels, not verified live stream contents. Missing upstream artwork is not fabricated. Full cold EPG acquisition and physical-TV latency have not been verified in this pass.

Web/backend edits have not been deployed. The screenshot at `build/sports-real-provider-boxing.png` is the actual sports pane in an emulator test, not the full signed release app shell.

## Follow-up: broader matching and physical TV verification

- Channel normalization now has an ASCII fast path with exact parity against the previous implementation for all 49,943 captured channel labels. Regional broadcaster matching supports ISO countries while preserving station-number and service boundaries.
- Live-channel results are no longer truncated to 12. Scheduled events whose start time has passed remain discoverable in a separately labelled Scheduled now row, without claiming a confirmed live broadcast.
- Upcoming cards expose channel counts and the picker lists confirmed or possible broadcaster matches. This is not a fight-specific workaround.
- Attached remote-focus targets respond immediately; pending navigation follows subsequent keys rather than dropping them while scrolling. Focus has a dark keyline and white outline for contrast against white artwork.
- 133 Android sports/guide unit tests passed, followed by 16 guide/sports device tests. Final captured-data matching returned 225 available events in 3,290 ms on the emulator; channel-key normalization took 1,831 ms with identical keys. This is metadata matching, not full acquisition.
- A short-lived, complete-catalogue disk cache is scoped to profile, provider, source version, exclusions and installed build. It refreshes in the background, not by reducing result coverage. Cache round-trip, boundary and expiry assertions passed for 1,500 events, followed by five sports refresh/navigation tests (6 total, 60.818 seconds).
- Signed release installed as an update on the physical TCL TV with the existing signing certificate. Account, favorites and 54,511-channel playlist remained present.
- Physical first scan: broadcaster lookup 29,157 ms; complete EPG scan 47,635 ms; merged catalogue 1,189 available entries. Opening Sports after prewarming was immediate, but this does NOT meet a five-second uncached-load target.
- Physical 24-direction scrolling sample: 215 frames, 44 janky frames (20.47%). No new ARVIO crash in the crash log; the old 11:13 detached-layout failure remains as historical evidence. Not a lag-free performance claim.
- Actual TV screenshots: `build/tv-sports-white-focus.png` and `build/tv-sports-channel-picker.png`. The latter shows an F1 event with 14 possible channels, demonstrating general matching beyond boxing.
- Later EPG coverage increased the full physical catalogue to approximately 3,900 available entries. Subsequent cold scan measurements ranged from 70 to 141 seconds under changing guide coverage and navigation load; the earlier 48-second result is not an upper bound. A separate 40-direction guide sample returned 325 janky frames out of 1,197 (27.15%).
- Physical cache testing exposed a 19 MB snapshot exceeding the initial 12 MB read limit. The cache now uses streaming gzip at the fastest compression level, preserving the entire snapshot; saving no longer waits for optional artwork network enrichment to settle. Reflection models are retained in R8 release builds. An expanded 1,500-event test with more than 12 MB of descriptions passed in 12.63 seconds (including repeated read/isolation checks), storing 318,016 bytes.
- Final compressed signed APK installed successfully as an update. APK SHA-256: `e84128ecb176698c1dc08fe64e622ec284d1028b26c1203c5726f49caee97110`. Copy: `C:/Users/arvin/Downloads/ARVIO-1.9.997-sports-navigation-fix.apk`.
- Physical full cache saved 3,858 entries in 1,361,716 bytes at 13:25:16. After leaving Home and returning to TV, it restored all 3,858 entries in 19,101 ms at 13:26:47. Complete-result reuse is verified, but the five-second target remains unmet. A prior restart timing run was interrupted by the TV rebooting (device uptime confirmed three minutes); no claim is made about its cause.
