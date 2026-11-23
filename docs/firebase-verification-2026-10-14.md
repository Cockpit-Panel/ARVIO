# Firebase Migration Verification

Tested locally on the Android TV API 31 emulator in the separate
`com.arvio.tv.beta` diagnostics package. No production release was uploaded and
the user's TV was not changed.

## Confirmed

- Firebase project `arvio-15c5a` created on Spark ($0/month), with a dedicated
  Analytics account. Optional Analytics data-sharing choices were disabled.
- Android and beta clients registered; public SDK configuration downloaded with
  the official Firebase CLI.
- The emulator caught a Ktor/coroutines startup ABI mismatch introduced by the
  new dependency graph. Ktor 2.3.13 and aligned coroutines 1.10.2 fix it. The app
  subsequently started and the exact failing event-subscription call has a test.
- Firebase's Crashlytics API returned the deliberate Java fatal test and the
  deliberately recorded non-fatal test, with their stack traces.
- A deliberate native SIGSEGV was captured, uploaded and returned by the
  Crashlytics API as a fatal native issue.
- The Java fatal event contained custom key
  `arvio_report_id=3ee559b7-6c01-4854-9d3d-ec84f5b43118`, identical to the local
  crash screen and stored report reference.
- Analytics logging confirmed the fixed `profiles` screen name. Advertising
  storage, user-data and personalization consent remained denied.
- Opt-out persisted both local switches as false. SDK logs confirmed measurement
  disabled, Analytics storage denied, and local Analytics data reset.
- Full Android unit suite: 2,358 tests, zero failures, two existing skips.
- Web type check passed. Three crash-report bridge tests passed, covering the
  Firebase reference, historical Sentry links and untrusted error text handling.

## Release Checks Still Required

The diagnostics APK is deliberately debuggable, beta-only and includes emulator
ABIs. It is not a release artifact. Before distributing the migration, build
the normal ARM release, verify its signing/packaging, upload R8 mappings/native
symbols, and review Google Play Data Safety. R8 mapping upload and native-symbol
upload are configured, but an optimized release was not built in this check.

Older API 23-29 freeze detection is implemented but was not exercised on a
physical older Fire TV. The API 31 emulator verifies Crashlytics itself, not that
legacy watchdog path. Production Analytics reports and custom-metric dashboards
have not been audited; the evidence above is SDK event logging and consent
behavior, not a claim that all production statistics are already populated.

Sentry cancellation is confirmed in its subscription overview: a downgrade to
the free Developer plan takes effect on November 4, 2026, after the paid period
ends November 3. The next bill is $0.00 and the pay-as-you-go limit remains $0.
No organization/history deletion was requested or performed. The free plan is
limited to one user, so other team members will lose access after the downgrade.

The default npm/npx Firebase cache was corrupted. Its Firebase-only directory
was moved to a backup and rebuilt. The standard `npx -y firebase-tools@latest
--version` command now returns 15.33.0; the isolated backup/cache used during the
initial setup is no longer required to run the CLI.
The MCP server from that repaired standard cache authenticated successfully,
selected `arvio-15c5a`, recognized both Android app IDs, and confirmed billing is
disabled. Gemini terms remain unaccepted, as intended.
