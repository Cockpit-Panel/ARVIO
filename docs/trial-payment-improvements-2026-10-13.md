# Trial activation and payment attribution — 13 October 2026

## Evidence and scope

Protected report for 8–12 October UTC: 36 trials, 23 with recorded source configuration, 15 requesting playback, 10 with browser playback starting, 10 with browser playback failure, seven external-player handoffs and 16 with a membership-page click. These stages overlap. Missing diagnostics are not proof of failure, and a handoff is not playback success. Nine new membership starts and seven renewals were recorded separately.

Only one new payment matched an anonymous website journey. A read-only replay against retained production records, using the corrected historical lookup, still matched one: eight cannot honestly be recovered from the available authenticated links. No historical identities, customer access, payment records or tracking sources were rewritten.

No pricing, trial duration, billing settings, Android code, extra email campaigns or marketing-site direct Ko-fi buttons change in this release.

## Changes

- Shared web checkout preparation from the trial navigation, paywall and account settings. Verify current access before offering payment; show/copy the account email, explain different PayPal/Ko-fi email linking, and retain a native external Ko-fi link without sending an email or journey identifier in its URL.
- Automatically recheck after the user returns from Ko-fi: at most four checks per recovery cycle, no hidden-tab polling, no concurrent duplicate checks, cleanup on close/account switch. A manual retry is available after propagation delays. An active trial never counts as paid access.
- Make verified different-email recovery visible after the payment handoff. Verification codes and single-owner billing rules remain server-enforced. Network errors do not invite a duplicate payment.
- Explain that the trial ends automatically without a charge. Account settings provide source-setup actions and encourage testing audio/subtitles before subscribing. Configured sources do not imply compatibility or successful playback.
- New account-day diagnostics for checkout returns and payment-status checks. These are not provider checkout attempts; paid state still comes only from the server.
- Reports look back up to 30 days for retained authenticated journeys of new payers in the selected period, following only verified billing aliases. Historical visits do not increase period visit/click counts. Direct anonymous purchases and ambiguous or expired links remain unmatched.
- Add overlapping trial-dropoff counts, checkout-recovery counts and explicit unmatched-payment diagnostics. No claim of net subscriber growth, abandonment reasons or mature conversion rates.
- Existing locale packaging retained. New Dutch checkout text supplied; untranslated new text falls back to English rather than claiming native coverage in every locale.

## Verification

- Backend regression suite including historical/ambiguous ownership, bounded context, no future sales and recovery diagnostics.
- Web regression suite and TypeScript checks; focused recovery tests for bounded retries, delayed payment, hidden tabs, concurrent events, errors, close/account switches and manual retry.
- Isolated headless UI fixtures use production components with mocked identity/HTTP, blocking all external network. Verified mobile/desktop layout, no horizontal overflow, Escape/focus restoration, delayed activation, invalid-code rejection and valid-code linking. Screenshots are test fixtures, not live payments.
- Production build completed. The UI fixture server and UI test are not production entry points.
- A real Ko-fi charge was not made. Actual PayPal/Ko-fi payment approval and cancellation/refund reconciliation remain outside these tests.

Local commands:

```text
cd netlify-auth-site && npm test
cd web && npm test
cd web && npx tsc --noEmit
cd web && npm run build
cd web && node tests/premium-ui-server.cjs
cd web && node scripts/check-payment-flow-ui.cjs
```

Release/deployment references are recorded on the pull request after verification. Evaluate new return/status diagnostics only after this release; absent older events are unmeasured, not zero returns.
