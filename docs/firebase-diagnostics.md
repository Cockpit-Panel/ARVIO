# Firebase Diagnostics

Project: `arvio-15c5a` (Spark, no billing account required).

- Android: `1:970677063444:android:681fdda874ad4cecec7c01`
- Beta / diagnostics: `1:970677063444:android:5e98426cf2709f19ec7c01`
- [Crashlytics](https://console.firebase.google.com/project/arvio-15c5a/crashlytics)
- [Analytics](https://console.firebase.google.com/project/arvio-15c5a/analytics)

The checked-in `app/google-services.json` contains public client configuration,
not administrator credentials. Never commit service-account keys or CLI tokens.
Fork maintainers should register their own apps and replace that configuration
before distributing their builds. Ordinary debug builds disable reporting.

Review the Play Console Data Safety declaration for the new optional Analytics
collection before publishing a production update. This setup does not upload a
new Play/GitHub release or change existing users' installed APKs.

## Collection

Crash reporting preserves the existing device-local diagnostics preference.
Usage statistics have a separate, device-local opt-in, off by default. Neither
preference is cloud-synced. Disabling analytics resets its local data. All
advertising consent is denied and advertising permissions are removed.

Usage events: `screen_view`, `playback_start`, `playback_failed`, and one
`playback_buffering` sample per player listener. Screen names come from a fixed
allowlist. Playback events contain only live/VOD, elapsed milliseconds and a
numeric Media3 error code. Firebase also measures sessions and engagement for
opted-in installations. No titles, searches, provider URLs or account IDs are
passed to Firebase Analytics. Existing first-party app-open measurement also
requires the usage opt-in. Website/Premium conversion measurement is unchanged.

Crashlytics captures Java/Kotlin and native failures. API 30+ has automatic ANR
reporting; older devices use a foreground heartbeat with at most one non-fatal
freeze sample per process. These samples are not labeled as fatal crashes.
Handled network/provider failures remain filtered and repeated handled failures
are deduplicated. Fatal crashes are not sampled away.

QR reports include `arvio_report_id`, a custom key attached before the fatal
handler runs. It is an ARVIO reference, not a Firebase event ID. Find the issue
by timestamp, version and exception, then compare the event custom key. Old QR
links without `provider=firebase` still lead to historical Sentry reports.

## Builds And Symbols

Use Java 17 and the configured Android SDK:

```sh
./gradlew assembleSideloadRelease uploadCrashlyticsSymbolFileSideloadRelease
./gradlew bundlePlayRelease uploadCrashlyticsSymbolFilePlayRelease
```

R8 mapping upload is part of minified builds. Native upload is explicit and must
run for each distributed native build. ARVIO's CMake outputs are available to
the plugin. Prebuilt third-party libraries can only be symbolicated as far as
their vendors' embedded symbols allow; retain vendor symbols when available.
The Firebase BoM stays on the compatible 34.x line to retain API 23 support.

Ktor is aligned to 2.3.13 and coroutines to 1.10.2. Older Ktor releases call an
internal coroutines method removed in newer coroutines, which caused the
Telegram streaming proxy to crash during dependency injection with the Firebase
Sessions dependency. A regression test covers that event subscription.

## Isolated Verification

```sh
./gradlew testSideloadDebugUnitTest assembleSideloadDiagnostics -PincludeX86Abis=true
adb -s EMULATOR install -r app/build/outputs/apk/sideload/diagnostics/app-sideload-diagnostics.apk
adb -s EMULATOR shell setprop debug.firebase.analytics.app com.arvio.tv.beta
adb -s EMULATOR shell am start -f 0x14000000 -n com.arvio.tv.beta/com.arflix.tv.DiagnosticsTestActivity --es action enable
adb -s EMULATOR shell am start -f 0x14000000 -n com.arvio.tv.beta/com.arflix.tv.DiagnosticsTestActivity --es action nonfatal
adb -s EMULATOR shell am start -f 0x14000000 -n com.arvio.tv.beta/com.arflix.tv.DiagnosticsTestActivity --es action crash
# Restart after the crash so the report can upload.
adb -s EMULATOR shell am start -n com.arvio.tv.beta/com.arflix.tv.DiagnosticsTestActivity --es action status
```

Verify the fatal and non-fatal reports in the **Beta** Crashlytics app, and
`screen_view` in Analytics DebugView. Use action `disable` to verify collection
stops and preferences survive restart. Clear the Analytics debug property after
testing. DiagnosticsTestActivity exists only in the diagnostics source set and
must never appear in release manifests. Never trigger a test crash on a user's
TV or ship an emulator/x86 APK as the public release.
