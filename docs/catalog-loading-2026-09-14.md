# Catalogue loading and pagination

## Findings and changes

- TV waited for custom catalogue batches behind built-in/MDBList work. Custom requests now start independently and publish completed rows in saved order, retaining the existing one/two-request concurrency limit. Initial and deferred MDBList rows publish individually too.
- Home overwrote provider pagination flags using row length. This disabled browsing for initial eight/ten-item rows. Provider flags and source offsets now survive initial publication and are used when loading further pages.
- Add-on responses could contain 100 entries while Home requested eight. Only the consumed source slice is matched now. Unmatched entries still advance the source cursor; short provider pages do not imply exhaustion. Probing remains bounded to three requests per page operation.
- Standard path-based `skip` requests precede the query-format compatibility fallback. Exhaustion and request failures are distinct; failures remain retryable and cancellation propagates.
- Appended pages publish before optional logo enrichment and merge into current state, rather than replacing unrelated rows with an old snapshot. Late initial results cannot overwrite extended rows or loaded deferred placeholders.
- The catalogue observer no longer drops the first actual update or ignores changes to source descriptors by comparing only three fields.

No layout, animation, Top 10 presentation, watched threshold or playback-sync policy changes. No request concurrency increase, no new View All screen.

## Verification

Regression coverage: large provider pages, bounded short-page probing, subsequent source offsets, failed metadata, network errors, cancellation, URL ordering/configuration, extended rows, changed source order, Continue Watching refresh and deferred placeholders.

The first suite run had one test-harness failure: reflection into a mocked URL-builder method. The builder was extracted into a pure function for direct testing. Final suite: 1,172 tests, 1,171 passed, one skipped, zero failures/errors. Results are in `build/catalog-pagination-verified-tests.log`.

These are Android code/fixture checks, not a timed reproduction with the reporter's private add-on. The add-on name/manifest and playback/sync logs are still needed to diagnose their specific Trakt report. No TV installation or web deployment was performed for this change.
