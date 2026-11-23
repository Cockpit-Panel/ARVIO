# Navigation follow-up: rendering and scroll ownership

## Plan and acceptance

1. Capture physical-device traces before changing rendering.
2. Remove hidden drawing and repeated Sports allocations without reducing images, channels or results.
3. Give guide vertical remote scrolling one owner; preserve horizontal programme navigation and touch scrolling.
4. Allow browsing scheduled channels without enabling early playback.
5. Run unit/device regressions, install the release with the existing signing key, and repeat physical-TV measurements.

Passing functional tests does not establish lag-free rendering. Report frame measurements separately; no zero-jank guarantee or automatic main-branch push.

## Changes

- Home: cache the static full-resolution backdrop/scrim layer when no video player is present. Video retains normal compositing.
- Cards: stop drawing fallback text behind confirmed opaque, full-bleed TV artwork. Transparent art, padded channel logos, failed images and touch crossfades retain their fallback.
- Sports: retain row key maps, ordering ranks and channel counts across focus-only updates. Scheduled channels accept remote focus/scroll but not playback. Close has a visible focus outline.
- Guide: stop automatic vertical focus relocation at each TV row because `revealRow` already performs the bounded scroll. The timeline scroll remains inside the boundary. Touch devices keep the existing relocation behavior.

## Evidence before follow-up

Physical TV: Smart TV Pro G08, existing profile, 54,511-channel playlist. No data cleared.

`home-anime-before.txt`: 206 frames, 50.49% missed deadlines, p50 65 ms, p95 81 ms. Trending Anime, ten right then ten left, 150 ms pauses between shell key inputs. Screenshot confirms the Home catalogue.

`guide-channels-before.txt`: 287 frames, 21.95% missed deadlines, p50 21 ms, p95 65 ms. Netherlands category, channel-list focus, ten down/up, mini-player active. Screenshot confirms channel focus after the pass.

Traces are synchronous wall-time slices, not CPU samples. The verified guide trace includes programme-mode navigation; do not compare its overhead directly with an untraced channel-mode run. `guide-nav-before.atrace` accidentally captured Settings and is excluded. `guide-nav-confirmed-before.atrace` captured the guide. The earlier Home trace's screen was not independently confirmed, so its GPU-wait observations remain diagnostic rather than a verified benchmark.

Unit suite: 1,141 tests, zero failures/errors. Initial Sports emulator run: six passed; one failed in the optional screenshot teardown because Android returned a null bitmap. Scheduled-channel browsing passed. Teardown now tolerates a missing screenshot; rerun required.

## Device regressions

Latest guide/card run: 41/42 passed. All 19 guide navigation/rendering tests and five focus appearance tests passed. The phone-only missing-image callback timed out at five seconds during the full run. A fresh rerun of all 18 card cases plus seven Sports cases passed (25/25), including scheduled-channel scrolling and opaque-artwork pixels. This gives 49 distinct device tests passing, with the two initial transient test failures retained in the logs rather than concealed.

## First physical-TV follow-up

Release build succeeded. Certificate matched the existing APK and `install -r` succeeded. Existing Arvind profile, 12 favourites, 34 recent channels and 54,511 channels retained. No new app crash in the device crash buffer.

| Pass | Frames | Missed deadlines | p50 | p95 |
| --- | ---: | ---: | ---: | ---: |
| Home Trending Anime, before | 206 | 50.49% | 65 ms | 81 ms |
| Home Trending Anime, after | 266 | 7.52% | 23 ms | 24 ms |
| Home Trending Anime, repeated | 256 | 8.20% | 23 ms | 23 ms |
| Guide channels, before | 287 | 21.95% | 21 ms | 65 ms |
| Guide channels, after | 230 | 38.70% | 30 ms | 73 ms |
| Guide channels, repeated | 249 | 28.92% | 21 ms | 65 ms |
| Sports six rows down/up | 84 | 27.38% | 27 ms | 69 ms |
| Sports repeated | 79 | 22.78% | 34 ms | 101 ms |

Home repeated the same catalogue, starting item and 10-right/10-left input sequence. Guide used the same channel/category and video but refreshed programme data and initially different visible offsets; not a controlled causal comparison. Home improvement is substantial; guide and Sports are not established performance wins.

The real Leeds/Newcastle scheduled picker still lists 52 channels. Eight Down presses now scroll to a lower channel and show focus. Select does not open early playback; the picker remains visible. This fixes the previously confirmed remote browsing defect.

## Second pass

Sports focus-order recording is now non-observable bookkeeping: a row focus change must not rebuild presentation lists. Schedule refresh still sorts the focused row against the recorded order. All 26 guide/Sports device tests passed again. Signed release installed as an update.

Sports: 88 frames / 15.91% missed deadlines / p95 57 ms, then 89 frames / 16.85% / p95 53 ms. Same six-down/up pattern, with refreshed live programme data. This is a useful improvement, not a zero-jank result.

An experimental full-resolution offscreen layer per guide row failed the physical test: 32.52% and 34.87% missed deadlines with video. It has been removed. The final release rebuild succeeded and was installed as an update with the same certificate; account/settings retained. Do not describe the removed row cache as a delivered improvement.

Final artifact: `build/verified-navigation.apk`, version 1.9.997 (313). Build log: `build/navigation-verified-release.log`. Final Home spot check repeated ten right/left inputs: 262 frames, 6.87% missed deadlines, p50/p95 23 ms (`build/verified-home-gfx.txt`). App remained running. This confirms the retained Home improvement in the final installed artifact, not completion of the guide performance target.

## Remaining work and limits

The smoothness target is not fully met. Guide scrolling still needs a deeper layout/allocation pass, independent of Home's GPU-bound background work. Next isolate programme text-layout churn from channel focus and decoder load using the same viewport and recorded guide data; retain all navigation/EPG functionality. Do not increase EPG/network requests or reduce result coverage as a performance workaround.

No GitHub push, merge or web deployment. Build/test artifacts are under ignored `build/`.
