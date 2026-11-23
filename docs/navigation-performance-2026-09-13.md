# Navigation performance verification

## Scope

Keep the existing appearance, controls, data sources, catalog coverage and image quality. Test on the emulator only; the physical TV is in use. Emulator frame timings are diagnostic comparisons, not certification of performance on a low-end TV.

## Changes under test

- Home: one owner for row/title restoration, synchronous identity capture on navigation, saved per-row anchors and consistent placeholder filtering. Interrupted scrolling must resume toward its actual target.
- Settings: reveal the measured selected row instead of estimating its location from its index. Detached coordinates are ignored.
- Sports: do not request the first card again when card focus closes the sidebar. Cancel obsolete navigation when leaving cards or opening the picker.
- Guide: cancel obsolete focus work when changing focus zone or category.
- Shared focus: read animation state during drawing rather than recomposing a control on each animation frame.
- Sports matching: retain a scoped normalized broadcaster index, preserving all variants and source ordering. Invalidate on profile, provider, source version or exclusion changes.
- Home background work: VOD warming runs on the I/O dispatcher and respects the owning coroutine's cancellation.
- Details: rating/badge-only episode updates no longer reset the selected episode. Season, initial resume target and empty-to-loaded changes still position focus.
- Player: initial control focus waits for attachment rather than a 300 ms timer; menu dismissal keeps ownership of its own focus restoration.

## Baseline

The running emulator reports 984,568 kB total memory (approximately 1 GB), not 2 GB. Baseline used the already installed debug build, so it is an exploratory baseline rather than a controlled release-build comparison.

`GuideRenderingBenchmark.scrollDenseGuide`: passed; 55,000-channel fixture with a 144-row render window; 80 down and 80 up inputs. 1,022 frames, p50 43.15 ms, p95 150.68 ms, p99 240.51 ms; 45.11% missed deadlines. Heap 55 MiB, limit 256 MiB. No network or video decoding in this fixture.

## Acceptance checks

- Home: actual remote input plus asynchronous row reorder must open the selected title, not its old index; partial refreshes must not erase the anchor.
- Settings: variable-height rows must remain visible with minimal scrolling.
- Sports: repeated directional navigation, metadata updates, sidebar closure and picker transitions must not steal focus or crash.
- Guide: dense channel navigation, favorites and mini-player focus tests must pass.
- Matching: compare every matched ID and its ordering against the prior full-scan algorithm over 55,000 labels.
- Preserve visuals: check focus contrast and capture emulator screenshots.
- Repeat frame measurements after functional tests, identifying emulator/host limitations explicitly.

Results below are from completed tests, not inferred from implementation.

## First pass

163 unit checks ran; 162 passed. The new index equivalence test hit a test-only Java API mismatch (`LinkedHashSet.reversed`); changed it to reverse a list for Java 17 compatibility. The first emulator mini-player focus test passed, but the remaining run was stopped when the 1 GB emulator became memory-starved (about 55 MB available; even screenshot capture stalled). Restarted the same AVD with an explicit 2 GB allocation and without loading its old snapshot. This changes the environment: do not compare its frame percentages directly with the 1 GB baseline.

## Final functional results

Final build: successful. 164 unit tests passed, zero failures/errors. Final APK and instrumentation APK installed with `adb -s emulator-5554 install -r`.

The 2 GB emulator reports 2,015,224 kB total RAM. All 26 device tests passed in 84.743 seconds:

- HomeNavigationRefreshDeviceTest
- SettingsFocusGeometryDeviceTest
- LiveFocusAppearanceDeviceTest (including draw-only animation and first-frame focus clearing)
- MiniPlayerFocusDeviceTest
- GuideNavigationDeviceTest (including 55,000-channel fixture/page-boundary navigation)
- SportsRefreshDeviceTest (including drawer transitions, scrolling and metadata refresh)
- GuideFavoriteActionDeviceTest

Build/test logs are in the ignored `build/navigation-final-verification.log` and `build/emulator-final-tests.log`. The scan equivalence test verifies every matched ID and provider ordering; it does not measure remote provider response times. No physical TV was queried or operated. No production deployment or GitHub push was performed.

Two additional `WatchlistHomeLibraryDeviceTest` tests passed in 8.61 seconds: TV and phone layouts retain navigable saved Jellyfin/Emby sources while a mocked server is stalled. Log: `build/emulator-library-tests.log`.

`SearchScreenDeviceTest` completed with `OK (13 tests)` in 22.587 seconds. Log: `build/emulator-search-tests.log`.

## Final frame measurement

`GuideRenderingBenchmark.scrollDenseGuide` passed in 36.455 seconds on the final APK and 2 GB software-rendered emulator. Same 55,000-label fixture and 80 down/80 up input pattern: 1,296 frames, p50 25.68 ms, p95 48.93 ms, p99 70.14 ms, 25.46% missed deadlines. Heap 44 MiB, limit 512 MiB. Logs: `build/emulator-final-benchmark.log` and `build/emulator-final-frame-metrics.log`.

This is not a lag-free result. RAM and installed build differ from the exploratory baseline, so the percentages are not a controlled before/after comparison. Release-build measurements on representative physical hardware remain necessary before claiming low-end smoothness.

## Interactive smoke checks and limitations

Used a local PerformanceTest profile on the emulator, without connecting a cloud account. Loaded Home catalogs, navigated between cards and rows, returned from Settings, scrolled to the final subtitle setting, selected an episode on Details, searched for Reacher, and opened the empty Library and TV configurations. Captured Settings and Details screenshots in the ignored build directory.

Real-provider playback, authenticated integration sync, and a five-second cold sports schedule are not verified by this run. Cached matching avoids repeat full-label scans without dropping matches, but remote provider/EPG latency remains outside that measurement. No web code or deployment changed. The installed APK is a debug test build, not a signed public release.
