# ARVIO 2.0 — Your media, together

**A new Library. A more powerful Search. A better Live TV experience.**

## September 19 Hotfix (Build 317)

The APK below replaces the initial build 316. It restores Telegram's release configuration and fixes disappearing home-server connections and missing library lists after sync. Existing installations can update without clearing their settings. If you already installed 2.0.0 from GitHub, download and install this replacement APK.

ARVIO 2.0 brings a major refresh across TV, phones, tablets and the web. This release brings together the work since v1.9.997: a redesigned Library for your watchlists and home servers, richer discovery filters, more flexible IPTV and sports navigation, and improvements to the everyday experience of browsing, watching and keeping everything in sync.

## A completely redesigned Library

- A unified OLED-friendly layout with clearer navigation for **Watchlists, My Lists and Homeserver**.
- Browse your personal lists and connected Jellyfin, Plex and Emby libraries in one place, with distinct Trakt and SIMKL sections.
- Responsive layouts for the TV remote, phone, tablet and browser.
- Support for poster and landscape cards, including clearlogos on landscape artwork.
- Progressive loading beyond the old 60-item home-server limit, more accurate library totals, earlier clearlogo loading and fixes for clipped focused cards.
- Better shared Plex source resolution, Emby episode-version handling and Silo compatibility through the Jellyfin integration.

## Find your next watch

- Filter discovery by **genre, rating, decade/year, certification where supported, original language and watched state**, with sorting and a reset action.
- Filtered results load as a continuous grid, with improved pagination and recovery after failed requests.
- Better remote focus and keyboard entry, plus a phone-friendly filter panel.
- Open **View all** from Home rows to explore their catalogues in a full grid.
- Default trending rows now lead with **Movies → TV Shows → Anime**; newly discovered home-server rows no longer displace the trending defaults.

## Live TV and sports, reworked

- Refined TV guide workspace, programme navigation, fullscreen channel switching and remote-control focus.
- A refreshed mobile Live TV experience with playlist overviews, improved mini-player rendering, clearer fullscreen controls and better handling of system insets.
- Reorder IPTV categories with the remote or by touch; preserve category order while switching channels.
- Connect up to **five IPTV playlists**.
- Improved sports schedules and channel matching, provider logos, programme identities and guide coverage.
- A clearer alternative-source selector and technical stream information based on what the player actually reports.
- More efficient startup, playlist parsing and guide loading, with improved cleanup when playback leaves the foreground.
- Improved catch-up/archive selection, seeking and playback controls.

## More capable source integrations

- Stalker movie and series/episode source resolution, with more reliable portal requests and caching.
- Separate per-portal switches for **Live TV, Movies and Series** imports.
- Better handling of failed lookups, completed episode-source results and changing provider catalogues.

## Watching, tracking and subtitles

- Better Continue Watching episode reconciliation and progress synchronization.
- Improved SIMKL API compatibility, shared request limiting, retry/backoff behavior and explicit history deletion handling.
- Trakt activation now handles rate limits and retry timing more gracefully.
- Per-profile autoplay quality and file-size limits.
- Faster subtitle auto-matching with AI text verification, expanded mobile subtitle timing adjustment to ±120 seconds, and improved language matching including Malay.
- Frame-rate matching based on decoder metadata and fixes around playback cleanup and next-episode validation.
- **Manual trailers from Details use the official YouTube embedded player**, with ARVIO controls outside the video. Moving focus on Home does not automatically open a trailer player; hero/card background trailer autoplay is not included.

## A smoother mobile experience

- Transparent, accent-aware bottom navigation that responds to scrolling and returns when needed.
- Improved edge-to-edge spacing, navigation-bar behavior and separation between screens.
- Refined profile PIN entry and fixes for profile activation, retained navigation state and Search controls.

## Web app and languages

These improvements are part of the wider ARVIO 2.0 work; web changes are delivered through the web app, not installed by the APK.

- The redesigned Library adapts to desktop, tablet and mobile browsers.
- Web interface translations follow the synced app language across all **51 supported language variants**.
- Improved Live TV favourites/recent synchronization, category controls, channel activation, docked-player navigation and startup recovery.
- Better profile hydration behavior and tracker synchronization.
- Expanded Android translations, including Search, Live TV, sports, global categories and fallback screens.

## Thank you, contributors

This release was made possible by **@Himanth-reddy, @ReichiMD, @Aaronnn17, @silentbil, @Saelon600 and @AndreOliveira23**, alongside everyone who tested builds, reported problems and helped with translations.

- **@Himanth-reddy:** mobile navigation and Live TV, profile/PIN refinements, startup and parsing performance, guide loading, SIMKL and core reliability work.
- **@ReichiMD:** Search filters, Stalker sources and import controls, TV remote behavior, category reordering, trailer-player refinements and localization.
- **@Aaronnn17:** Live TV source selection and technical stream information, plus Spanish and interface improvements.
- **@silentbil:** Home View all and subtitle auto-matching.
- **@Saelon600:** five-playlist support, Continue Watching episode state and EPG playback fixes.
- **@AndreOliveira23:** expanded mobile subtitle offset range.

[Merged contributions](https://github.com/ProdigyV21/ARVIO/blob/v2.0.0/releases/v2.0.0/MERGED_CONTRIBUTIONS.md) · [Complete commit history](https://github.com/ProdigyV21/ARVIO/blob/v2.0.0/releases/v2.0.0/COMMIT_CHANGELOG.md) · [Full comparison](https://github.com/ProdigyV21/ARVIO/compare/v1.9.997...v2.0.0)

## Get ARVIO 2.0

- **GitHub:** download `ARVIO-v2.0.0-sideload-release.apk` below. This signed ARM-universal build supports compatible ARMv7/ARM64 phones, tablets and TVs and retains sideload updates and plugin support.
- **Google Play:** the Play build is submitted separately and becomes available after Google's review and rollout.
- **Checksums:** `SHA256SUMS.txt` covers the public APK download.

**Version 2.0.0 · Android version code 317 · Android 6.0 or newer.**

ARVIO does not include movies, television channels or streaming subscriptions. Connect your own compatible, authorized sources. Provider, browser and device capabilities determine playback and feature availability.
