# ARVIO 2.2

**A new release Calendar. More ways to build your library. Better source selection, tracking and playback.**

This release includes the changes since **2.0.1** across Android TV, phones, tablets and the web app.

## GitHub APK hotfix (build 320)

- SIMKL playback sync now checks activity timestamps before downloading progress, including forced refreshes. Unchanged playback data is reused instead of fetched every 15 minutes.
- Use `date_from` checkpoints for playback updates, retain them across app restarts and reconcile cleared sessions without losing older unfinished titles. Failed requests preserve the previous checkpoint for a safe retry.
- The web app uses the same activity-gated playback caching and shares concurrent requests.
- The replacement APK is still version 2.2, signed with the existing release key. The Google Play submission remains build 319; this hotfix replaces the GitHub download only.

## Release Calendar

- New Calendar tab in Library on Android and web, with artwork, movie releases and episode schedules from your ARVIO watchlist and connected Trakt, SIMKL and MDBList accounts.
- Include shows you are currently watching, deduplicate titles across services and filter by watchlist source without changing your lists.
- Confirmed air times use your device's timezone. Date-only releases keep their published date and show Time TBA instead of an invented time; movie releases include regional information.
- Open a release directly at its season and episode. Busy dates show multiple posters and additional-release counts, including batch episode releases.
- Consistent Library tabs directly beneath the TV top bar, a compact source menu and responsive phone, tablet and landscape layouts.
- Predictable remote navigation: left/right moves a day, up/down moves a week, and month changes use the month controls. Fixed unexpected jumps into adjacent months and focus loss when entering or leaving the release strip.
- Faster loading through saved month previews, persistent per-title schedules, coalesced requests and batched season metadata. Idle Home preloading stops when you interact or leave the screen.
- Reuse verified results when changing months or retrying failed titles, while retaining specials, overlapping seasons and anime coverage. Slower services and optional artwork no longer hold back available releases.
- Distinguish incomplete results, failed sources and genuinely empty dates. Retry failed data without discarding healthy results; show a Next release action on empty days when available.

## Add-ons, catalogs and collections

- Browse and install community add-ons from the in-app add-on browser. Installation, refresh and removal update the saved add-on state atomically.
- Add TMDB lists, collections, companies, networks, people, keywords and genres as catalogs by pasting their page URL. Resolve person catalogs and mixed movie/series pages correctly.
- Pasting an add-on manifest or stremio:// link into Add Catalog now opens the add-on installation flow instead of rejecting it as an unsupported catalog URL.
- Display titles that belong to an add-on even when TMDB has no matching entry. Use the add-on's own metadata, episodes, artwork and playback identity rather than dropping or mismatching those titles.
- Preserve native add-on identities in saved viewing progress and across device restore. Skip remote scrobbles when a tracker cannot identify an add-on-only title.
- Support catalogs with anime, live TV, podcast and other add-on-defined types, including types containing spaces. Carry the original type through catalog, metadata, stream and restore requests.
- Improved collection add-on matching, manifest-ID resolution, source loading, type handling and encoded URLs. Accept compatible catalog responses with a single genre string.
- Query compatible native add-on search catalogs and publish results progressively. Avoid unsupported metadata calls, repeated per-item registry reads and request floods after rate limits.
- Re-import catalog packs and collections without stale HTTP-cache results. Trakt and MDBList list edits now refresh within the 30-minute request window rather than remaining stale for a day.
- Catalog See all now uses the richer collection-style layout with a hero, title metadata and responsive browsing.
- Align the web app's built-in catalogs and collections with Android. Restore VOD/add-on collection contents and keep intentionally hidden catalogs hidden.

## Home, search and navigation

- Restored optional Android TV card expansion and inline/hero trailer previews using the official YouTube embedded player, with delayed loading, preserved remote focus and cleanup when focus changes or the app is backgrounded.
- Prepare trailers during the focus delay, improve preview sizing and loading, remove the extra expanded-title strip and recover from preview-renderer crashes without closing ARVIO.
- Improve hero text contrast, including right-to-left layouts.
- Favorite TV previews can start from saved channels before the rest of Home finishes loading. Resolve compatible channel addresses without an .m3u8 suffix and restore the Favorite TV row when enabled after importing a Home template.
- Stop hero channel playback and audio when the app is backgrounded. Refresh the Favorite TV guide on focus from saved data instead of repeatedly downloading a full XMLTV guide while Home is idle.
- Search remembers the profile's last eight successful queries, provides clear controls and correctly encodes add-on search URLs.
- Fixed Fire TV/remote users being unable to select Live TV channel or programme search results.
- More reliable first-press remote actions in Details, source selection and next-episode controls.

## Details, menus and source selection

- Episode cards keep the episode title, description, date, runtime and progress inside the artwork, with genuine episode IMDb ratings where available.
- Refined season selectors, compact context menus, profile dialogs and mobile controls across Android and web.
- Consistent white focused menu rows with black content; checkmarks indicate actual selection rather than focus. Settings choices use clearer values, chevrons, switches and connection status.
- Clearer source rows prioritize release names and align technical information beneath them. Fixed flickering and unstable focus while scrolling through sources.
- Removed the extra black inner/underlay borders from focused episode cards, retaining the clean white focus outline.
- Paused TV-show playback displays the current episode's synopsis rather than the series description, without retaining stale episode metadata.
- Recently played sources are pinned above the normal source sort with a Recently played label. Record a source only after ten seconds of actual playback, not buffering, seeking or a failed switch; series remember the exact episode and profiles remain separate.
- Autoplay prefers a matching recently played source, with a bounded wait for its provider and normal fallback when unavailable or excluded by quality preferences.

## Playback, Live TV and home servers

- Improved automatic frame-rate matching when stream metadata is rounded or incorrect, using decoded-frame cadence. Added real Matroska and MP4 decoder regression checks; actual switching still depends on the device and display modes.
- Improved IPTV playback and Home previews for streams that use non-IDR keyframes.
- Fixed playback completion and final progress saving around end-of-file, next-episode transitions and remote actions.
- Show complete channel names when guide data is unavailable rather than truncating the fallback title.
- More resilient local Emby sign-in: normalize copied dashboard addresses while preserving reverse-proxy paths, support server-validated passwordless users and finish authentication before library enumeration.
- Retain authenticated home-server sessions through temporary library outages and bound server-discovery requests.
- Honor negotiated Jellyfin/Silo direct-play and remux routes, required headers and playback sessions instead of choosing storage paths or unnecessary transcoding. Respect server playback denials and avoid forwarding server credentials to external delivery URLs.
- Sync by Hearing now defaults off on low-memory devices to avoid costly speech-model startup. It remains available to enable, and an explicit preference is synchronized without overriding another untouched device's default.

## Tracking, SIMKL and cloud sync

- New SIMKL connections use AUTH V2 with PKCE, device/PIN authorization, validated scopes and per-device refresh handling. Existing V1 tracking connections remain supported during the migration.
- Add SIMKL custom lists as catalogs by URL or discover your owned, followed, shared and featured lists. Preserve list order, pagination and media identities, with clear private-list, permission and rate-limit errors.
- SIMKL custom-list contents require **SIMKL PRO/VIP**. ARVIO Premium does not include a SIMKL subscription. Existing SIMKL users should reconnect once on each device to enable V2 list access; old V1 grants cannot be converted automatically.
- Improved SIMKL Continue Watching reconciliation: remove stale pauses, respect completed history and watched markers, validate real episodes and advance through actual season structures while preserving verified rewatches.
- Keep saved Continue Watching rows when a provider returns an incomplete or failed response instead of clearing useful progress.
- Align SIMKL scrobbling with ARVIO's **90% watched threshold** instead of the provider's default 80%.
- Keep web V2 access/refresh tokens encrypted on the server behind HttpOnly, same-site cookies. Migrate existing browser V2 credentials after server acknowledgement, serialize refresh across instances and exclude device grants from Cloud sync.
- Disconnect revokes the device's SIMKL V2 grant without disconnecting other devices. Web failures retain a pending revocation for retry; Android performs a bounded best-effort revoke after local cleanup.
- Fixed removed watchlist items returning after Cloud sync and protected newer local changes from stale remote data.

## Web app, setup and self-hosting

These changes are delivered through the web app or website; installing the APK does not deploy a web server.

- Reworked player source selection and Details source-button consistency, with corrected layouts, scrolling and action placement.
- Added the pulsing player-loading presentation, repaired autoplay audio and exposed available audio tracks more reliably.
- Fixed selected subtitles failing to load across origins, with safer subtitle handling and playback controls.
- Restored Favorite TV on Home, native add-on VOD collection contents and correct catalog visibility without extra unwanted rows.
- Brought Calendar, episode-card metadata, compact menus and profile controls into alignment with Android while retaining responsive layouts.
- Added a free migration/setup page to carry compatible profiles, add-ons, catalogs and collections from a Nuvio account into ARVIO. Existing destination settings, IPTV playlists and profiles are protected; failures are reported without replacing healthy data. Plugins are not migrated.
- Added Collection Studio, shareable public-safe previews and starter examples for building and sharing your own collections.
- Added validated Open in ARVIO partner title links and a Web App button beside Premium on arvio.tv.
- Improved setup, IPTV, tracking, subtitle and home-server guides, discovery pages and direct Premium subscription links.
- Improved independent Docker/Unraid preview packaging, runtime public configuration, prebuilt translations, source/dependency verification and container defaults. The Unraid preview intentionally excludes Telegram; this does not disable Telegram in the official Android APK.
- Persistent private SIMKL session storage is included for Docker/Node deployments, with shared persistent storage on the hosted Netlify deployment.

## Contributors

Thank you to [@silentbil](https://github.com/silentbil), [@test01203](https://github.com/test01203) and [@Himanth-reddy](https://github.com/Himanth-reddy), plus everyone who tested and reported issues.

- **[@silentbil](https://github.com/silentbil):** community add-on browsing, native add-on catalogs/collections, recent searches, recently played sources and autoplay, Favorite TV previews and guide loading, IPTV keyframes, list freshness, hero contrast and low-memory hearing-sync defaults.
- **[@test01203](https://github.com/test01203)** (Git author koby455): TMDB page catalogs, add-on manifest routing from Add Catalog and the free migration/setup flow, including data-safety follow-ups.
- **[@Himanth-reddy](https://github.com/Himanth-reddy):** animated Discord community icon and video assets.

Claude/Claude Opus also appears in the included Git history as an AI author/co-author; those credits are retained in the complete commit record. Existing third-party collection artwork and data retain their original attribution.

[Merged contributions](https://github.com/ProdigyV21/ARVIO/blob/v2.2/releases/v2.2/MERGED_CONTRIBUTIONS.md) | [Complete commit history](https://github.com/ProdigyV21/ARVIO/blob/v2.2/releases/v2.2/COMMIT_CHANGELOG.md) | [Full comparison](https://github.com/ProdigyV21/ARVIO/compare/v2.0.1...v2.2)

## Downloads

- **ARVIO-v2.2-sideload-release.apk:** signed, optimized ARMv7/ARM64 release for supported Android TVs, phones and tablets. Install over the existing GitHub app to retain your account and settings.
- **ARVIO-v2.2-source.zip:** source snapshot of this release.

**Version 2.2 | Build 319 | Android 6.0+ | Target SDK 36**

This GitHub release does not constitute a Google Play submission.
