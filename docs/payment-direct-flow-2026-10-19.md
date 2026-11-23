# Direct membership handoff — October 19, 2026

## Scope

ARVIO Web payment UX and bounded Premium diagnostics only. Existing Ko-fi
membership destination and $2.99/month price, Android runtime, payment webhook
verification, billing ownership verification and privacy opt-outs are unchanged.

## Customer experience

- One native link opens Ko-fi; no preparation dialog or entitlement request must
  complete before navigation. Email guidance is inline on paywall/account screens.
- Post-handoff recovery checks access when the customer returns. Manual recovery
  is separate from the purchase action; pending customers are not asked to pay
  again. Native middle/modified/keyboard activation is preserved.
- Recovery polling remains bounded to four checks, skips hidden tabs, coalesces
  duplicate checks and stops on close/account changes. Verified billing-email
  linking remains available without another purchase.
- The trial navigation action says Keep Premium rather than only showing a
  countdown. Price and trial time remain available in its accessible title.

## Tracking

- Visible CTA, membership-link handoff, return, recovery open/close and status
  outcomes are distinct diagnostics tagged direct_v2 and grouped by placement.
- Small telemetry POSTs use keepalive but never block payment links. Browser
  privacy opt-outs, network failures and blocked storage remain supported.
- Atomic first account-event-day writes prevent concurrent requests replacing
  the first timestamp/placement. Status results retain at most the first
  pending/active/error result per UTC day, with bounded ETag-conditional updates.
- Recovery chronology requires click before return before check. An authenticated
  journey connected only after payment cannot retrospectively claim that sale.
- Only verified server webhooks count payments. Browser status is diagnostic;
  direct anonymous purchases and different unverified billing emails can remain
  unattributed. No personal information or journey IDs are put in Ko-fi URLs.

## Verification and release constraints

- Backend: 146 regression tests, including forged server-event rejection,
  conditional-write races, status transitions and attribution chronology.
- Headless local UI: 19 scenarios including 320/390/1440-pixel layouts, native
  pointer/keyboard handoff, visible-only impressions, delayed payment, errors,
  billing verification, late responses, account changes, exhausted polling cycles
  visibility-before-focus event ordering and account-boundary telemetry.
  The mobile navigation control remains an accessible 38-pixel icon during
  pending/active states. Membership state is keyed per account so a previous account's open dialog
  cannot emit recovery events or access requests for the next account. Local test identity
  and HTTP fixtures only; an allowlist and fail-closed local proxy block external
  payment/email traffic. Screenshots are fixtures, not real payment evidence.
- Privacy/attribution and bounded payment recovery unit tests; TypeScript check.
- Main currently has an unrelated failing Android YouTube-extraction safeguard;
  this release neither changes Android nor removes/weakens that test. Runtime
  release validation uses the unchanged safe Android baseline 0ae9ba8c8 with the
  exact current/live web and auth trees plus this payment delta. Before the delta,
  those runtime trees matched live a417ae4c0 and main 9ee9132a9 exactly. No existing
  web/library/SIMKL changes are rolled back. The known main failure must not be
  presented as a green main pipeline.
- Concurrent full-suite runs hit existing calendar tests with small wall-clock
  budgets. The complete serial runtime suite passed 1067/1067 with no skipped
  tests or test changes. Final focused Premium/recovery tests and TypeScript also
  passed. All 19 headless scenarios passed against the final account-keyed component.
- Publish backend support before the new web client. Check production report
  schema, protected endpoints, exact web build/version stamp and deployed bundle.
- No real charge, customer trial, billing email or cancellation is created by
  these tests. Actual card/PayPal approval remains a provider-side transaction.

Deployment IDs and final validation results belong in the pull request/release
record. New diagnostics have no historical baseline before deployment.

The isolated remote release uses branch `codex/payment-runtime-release-20261019`.
Its web/auth trees are checked against the primary implementation commit; only
deployment orchestration differs. The first candidate (run `39268387543`)
passed tests/build but was cancelled before publication to add the account-boundary
correction. Final source/deployment IDs are recorded on PR #812.
The release workflow tests both runtimes, builds and validates the web version,
then publishes the compatible backend before the browser client. The main PR
contains no Android rollback or deployment-guard change.
