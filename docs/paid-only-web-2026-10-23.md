# Paid-only hosted Web — October 23, 2026

## Authorized scope

The user's final decision supersedes the initial one-week proposal: no hosted
Web trials, no automatic rollback and no new scheduled review. Full Premium or
no app access. Existing valid paid/manual memberships, Android, Cloud accounts
and independent self-hosted builds remain unchanged. Price stays $2.99/month.

## Experience and enforcement

- A public, responsive feature preview appears at web.arvio.tv before automatic
  authentication. It uses five existing real ARVIO screenshots, not a playable
  demo. Features, own-source limitations, price and cancellation terms are clear.
- Explicit Cloud connection precedes the existing native Ko-fi handoff. Returning
  customers retain bounded payment checks and verified different-email recovery.
- Profile selection and all application/player children sit inside the paid
  entitlement gate. Player-library prewarming starts only inside that gate.
- New/legacy trial POSTs return 403 trial_unavailable; no trial writes or mail.
  Historical trial records remain intact but do not grant access. The browser
  rejects cached trial reason/source fields, including malformed mixed states.
- Old trial intents are cleared without starting a trial. Marketing pages and
  generated Premium copy no longer advertise one. Retired jobs are suppressed
  without contacting Resend; bounded batches preserve normal audit cleanup.
- The verified Ko-fi webhook remains the source of payments. Browser telemetry
  cannot claim a payment. paid_only_v1 / feature_preview survive reporting.

## Verification

- Backend full regression suite: 157 tests pass.
- Paid-only headless UI: 13 scenarios at 320/390/1440 pixels, all preview tabs,
  no automatic auth, no prepayment app/profile/player mounts, stale trial/cache,
  delayed payment and Dutch/Spanish controls.
- Existing payment/recovery regression: 19 scenarios, using isolated identities
  and HTTP, blocked external networking, no real purchases, trials or emails.
- Website tests, localized generator checks and TypeScript are verified before
  publication. Screenshots remain outside publish roots in artifacts/.
- The repository's full web suite has a pre-existing Android copyright safeguard
  failure (YouTubeExtractor.kt), also present in main's failed October 22 deploy
  run 39791175789. This release does not change Android or weaken/skip that test.
  Report that failure separately; do not call the complete main pipeline green.

Production deployment IDs, build stamp and read-only smoke checks are recorded
after publication. Payment-provider charging is not simulated as a real purchase.
