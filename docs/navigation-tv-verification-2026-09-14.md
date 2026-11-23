# Physical TV navigation verification

## Installation

User authorized physical-TV testing on September 14 after the emulator-only pass. Tested Smart TV Pro (G08), approximately 2.4 GB usable RAM, existing profile and 54,511-channel playlist.

Built `assembleSideloadRelease` from the local navigation-performance changes. The first build timed out waiting for another Gradle instance's shared build-cache lock. Retry with `--no-build-cache --max-workers=2` succeeded. No other build process was interrupted.

Compared the APK signing certificate against the installed APK before installation: identical. Installed with `install -r`, without uninstalling or clearing data. Version remains 1.9.997 (313). Existing profiles, Cloud/Trakt connection, 12 favorites and 34 recent channels remained visible. Saved the previous APK in ignored `build/tv-before-navigation.apk` as a rollback artifact. No GitHub push or deployment.

## Functional checks

- Home: 10 right/10 left inputs returned to the first Trending Movies card. No unexpected title jump observed during this pass; this does not exhaustively reproduce asynchronous refresh races.
- Settings: navigated sections down/up, then scrolled the Subtitles controls to the last row. Selected row remained fully visible. No settings were toggled.
- Guide: entered the existing Netherlands category, collapsed the sidebar, navigated 10 channels down/up twice with the mini-player playing. Focus remained in the channel list and navigation did not lock up.
- Sports: entered with the real playlist, collapsed the sidebar, navigated six rows down/up twice, opened and dismissed event pickers. No crash observed. Process PID remained 4696, and the crash buffer contained only old system-init entries, not a new ARVIO crash.
- Live-channel picker: directional focus moved to the third channel correctly.
- Playback: mini-player and fullscreen playback both worked for the existing ESPN 4 channel.

## Frame measurements

Android `dumpsys gfxinfo` short diagnostic passes, no screen recording during collection. Network activity, refreshed EPG content and cache warmth were not controlled, so these are not rigorous before/after benchmarks or release-wide guarantees.

| Pass | Frames | Missed deadlines | p50 | p95 |
| --- | ---: | ---: | ---: | ---: |
| Previous build, Home | 272 | 56.25% | 61 ms | 93 ms |
| New build, Home | 204 | 50.00% | 65 ms | 85 ms |
| New build, Settings sidebar | 85 | 0.00% | 13 ms | 15 ms |
| Previous build, guide with video | 254 | 24.80% | 25 ms | 61 ms |
| New build, initial guide pass | 222 | 53.60% | 38 ms | 133 ms |
| New build, warmed guide pass | 248 | 30.24% | 27 ms | 69 ms |
| Previous build, Sports | 99 | 17.17% | 22 ms | 57 ms |
| New build, initial Sports pass | 84 | 38.10% | 32 ms | 77 ms |
| New build, warmed Sports pass | 87 | 22.99% | 24 ms | 61 ms |

Logs and screenshots are in ignored `build/tv-*-gfx.txt` and `build/tv-*.png` files. These measurements do NOT establish improved guide or Sports frame performance. The smoothness acceptance target has not passed.

## Remaining findings

1. Home still has substantial rendering delays during rapid horizontal navigation. Focus correctness and rendering speed are separate issues.
2. Guide and Sports still miss too many deadlines, especially during initial loading. A controlled physical-device profile is needed to identify remaining main-thread/rendering costs.
3. Upcoming event picker cannot be navigated through by remote: Leeds United vs Newcastle United listed 52 scheduled channels, but focus stayed on Close. Code disables all channel rows until the event can be opened, also preventing focus-driven scrolling. This should allow browsing without enabling early playback. Live-event picker navigation works. The Close icon also lacks a clear focus indicator.
4. Some featured live entries remain generic program/channel entries with weak artwork. This test does not certify complete or accurate sports matching.
5. Sports was populated at the screenshot taken a few seconds after entry, using existing data/cache. This is not proof of a five-second complete cold schedule fetch.

Verdict: functional progress, but not ready to claim lag-free navigation or merge as a completed performance fix. Leave the changes unpushed for further work.
