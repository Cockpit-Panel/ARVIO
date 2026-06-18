# ARVIO

ARVIO is an Android media hub for TV, phone, and tablet form factors. This repository is maintained as a source-code and development mirror for the Android application.

This branch is the Cockpit-managed ARVIO build. It keeps the upstream ARVIO client base, but replaces the public account flow with Cockpit Arvio panel authentication and panel-managed IPTV/service provisioning.

The app provides a media browser, player shell, profile support, optional cloud sync, IPTV playlist support, catalog configuration, home-server integrations, and integrations with user-configured sources. ARVIO does not host, store, sell, or distribute movies, series, live TV channels, playlists, streams, or other third-party media.

## Repository Purpose

This GitHub repository is for:

- Source code review and development
- Issue investigation and technical discussion
- Build documentation
- License and privacy documentation
- Contribution review

It is not intended as an advertising page, download landing page, or content distribution repository.

## Features

- Android TV, Fire TV, phone, and tablet UI
- Cockpit Arvio Xtream Codes login with panel-provided service selection
- Panel-managed IPTV playlist provisioning after login
- TMDB-powered movie, series, cast, collection, franchise, and metadata browsing
- IPTV M3U/Xtream playlist support with provider categories, favorites, hidden categories, EPG, and mobile/tablet fullscreen playback
- Optional ARVIO Cloud sync for profiles, settings, catalogs, IPTV state, watch state, and custom profile avatars
- Optional per-profile Trakt.tv integration for watchlist, history, progress, and continue watching
- Catalog management with manual URLs and public Trakt/MDBList list discovery
- Home-server source and catalog support for user-owned Jellyfin, Emby, and Plex libraries
- Optional Telegram integration for searching video files in your own connected channels and groups
- Third-party addon support for user-configured sources
- Watchlist and continue-watching state with profile isolation
- Subtitle and audio track selection, subtitle language filtering, and AI subtitle tools
- Profile PINs and custom profile avatars
- ExoPlayer/Media3 playback with TV remote, mobile, and tablet controls

## Cockpit Mod Notes

This distribution is wired for a Cockpit Arvio module:

- Cockpit API base: `https://demo.cockpit.lol/api/arvio/`
- Users sign in with Xtream Codes credentials issued by Cockpit.
- Services/portals are loaded from the Cockpit panel instead of being manually entered as raw service URLs.
- Login creates the client-side IPTV configuration from the selected Cockpit-managed service and Xtream credentials.
- IPTV add/edit UI hides raw service and EPG URLs where possible so provider URLs are not exposed in normal settings screens.
- TMDB direct-call credentials are intentionally hardcoded for this mod build.
- Sideload plugin runtime receives the same TMDB key/token through `TMDB_API_KEY` and `TMDB_READ_ACCESS_TOKEN`.

When pulling upstream ARVIO changes, preserve the Cockpit integration points in:

- `app/src/main/kotlin/com/arflix/tv/data/api/CockpitArvioApi.kt`
- `app/src/main/kotlin/com/arflix/tv/data/api/CockpitArvioModels.kt`
- `app/src/main/kotlin/com/arflix/tv/data/repository/AuthRepository.kt`
- `app/src/main/kotlin/com/arflix/tv/ui/screens/login/`
- `app/src/main/kotlin/com/arflix/tv/ui/screens/settings/`
- `app/src/main/kotlin/com/arflix/tv/util/Constants.kt`
- `app/src/main/kotlin/com/arflix/tv/updater/AppUpdateRepository.kt`


## Availability

ARVIO is available on Google Play:

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" width="160">](https://play.google.com/store/apps/details?id=com.arvio.tv)

## Support ARVIO

ARVIO is a free hobby project built and maintained with a lot of time, testing, hosting, and service costs. The goal is to keep ARVIO free as it grows, but running and improving it still costs money every month.

If ARVIO helps you and you want to support development, donations are appreciated:

[Support ARVIO on Ko-fi](https://ko-fi.com/arvio)

## Screenshots

| Home | Details |
|------|---------|
| ![Home screen](screenshots/home_v190.png) | ![Details screen](screenshots/details_v190.png) |

| Live TV | Collections |
|---------|-------------|
| ![Live TV screen](screenshots/live_tv_v1991.png) | ![Collections screen](screenshots/collections_v1991.png) |

| Mobile | Profiles |
|--------|----------|
| ![Mobile screen](screenshots/mobile_home.webp) | ![Profiles screen](screenshots/profiles_v1991.png) |

## Content And Source Policy

ARVIO is a media browser and player interface for user-configured sources. It works like a media player or browser: users provide their own services, playlists, addons, and URLs.

This repository does not include hosted media content, bundled playlists, IPTV credentials, debrid accounts, third-party streaming catalogs, or links intended to enable unauthorized access to content. No movies, series, live TV channels, playlists, or other third-party media are hosted by this repository or by ARVIO.

Users are solely responsible for their usage and must comply with applicable local laws. If you believe content accessed through an external source violates copyright law, contact the actual file host, service provider, or source maintainer. The ARVIO repository and developers cannot remove content hosted by third parties.

Contributors should not submit copyrighted media, credentials, private keys, access tokens, or links intended to enable unauthorized access to content.

## Cloud Sync

ARVIO Cloud is optional. When enabled, it can sync profiles, settings, catalogs, IPTV state, watch progress, watchlist state, and profile avatars across devices. See [PRIVACY.md](PRIVACY.md) for details and account deletion instructions.

## Build And Run

Requirements:

- Android Studio or Android SDK command-line tools
- JDK 17
- Android SDK 35

Use the tracked Gradle wrapper:

```bash
./gradlew :app:assemblePlayDebug
./gradlew :app:assembleSideloadDebug
```

On Windows PowerShell or Command Prompt:

```powershell
.\gradlew.bat :app:assemblePlayDebug
.\gradlew.bat :app:assembleSideloadDebug
```

Install a debug build on a connected Android TV, Fire TV, emulator, phone, or tablet:

```bash
./gradlew :app:installPlayDebug
./gradlew :app:installSideloadDebug
```

For network ADB devices:

```bash
adb connect <device-ip>:5555
adb install -r app/build/outputs/apk/sideload/debug/app-sideload-debug.apk
```

Build variants:

- `play`: Play Store build, self-update disabled.
- `sideload`: Direct APK build, self-update enabled.
- `debug`: development build.
- `staging`: release-like build signed with the default Android debug keystore.
- `release`: optimized build signed with a private release keystore when `keystore.properties` exists, otherwise signed with the default Android debug keystore.

## Local Configuration

This Cockpit build bypasses normal Supabase sign-in for the primary login flow. TMDB credentials are hardcoded in the app constants so metadata and plugin flows work without Supabase proxy configuration.

The upstream `secrets.properties` and `keystore.properties` hooks are still supported, but they are optional for this branch. If no release keystore is configured, release and staging APKs use the default Android debug keystore.

## Release Checks

Before publishing a build, run:

```bash
./gradlew :app:compilePlayDebugKotlin
./gradlew :app:assemblePlayRelease
./gradlew :app:assembleSideloadRelease
```

Smoke-test startup, profile switching, playback, stream fallback, subtitle/audio switching, IPTV/EPG loading, addon add/remove, search, settings navigation, background sync, and repeated player open/close on the supported device classes.

## GitHub Maintenance

The repository includes GitHub Actions for:

- Pull request build verification.
- Manual upstream sync PR creation.
- Manual or tagged APK release creation.

Use **Actions -> Sync Upstream** to fetch an upstream repository/branch into a `sync/upstream-*` branch and open a PR. Resolve conflicts in that PR while preserving the Cockpit mod points listed above.

Use **Actions -> Build APK Release** to build and publish APK artifacts. The Cockpit branch does not require repository secrets for the standard APK build; GitHub Actions will use the checked-in defaults and Gradle's default debug signing fallback.

## Privacy

See [PRIVACY.md](PRIVACY.md) for the privacy policy. Cloud account and synced data deletion is available at [auth.arvio.tv/delete](https://auth.arvio.tv/delete).

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## AI Disclosure

This application was developed with significant AI assistance. Contributions should still be reviewed, tested, and treated as normal source code changes.

If you have concerns about using AI-generated software, please do not use this application.
