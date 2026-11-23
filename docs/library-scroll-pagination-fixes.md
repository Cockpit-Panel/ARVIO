# TV Library follow-up — 14 September 2026

Focused cards now request visibility for their full layout, including the title and spacing around the focus outline. Both landscape and poster grids reserve room above the first row. Provider headings use larger bold white uppercase text, recognizable Trakt and Simkl icons, and group spacing to distinguish providers when unfocused.

Sixty remains a network page size, not a library limit. Server loaders continue when a response omits its total count and advance using the raw server cursor, including when display items are deduplicated. Returning to a cached library preserves the accumulated pages. The UI retries paging after loading transitions, handles filtered empty pages, exposes failures for retry, and shows a plus sign while more titles remain.

Custom catalogs use paging instead of the previous 120-title load. Tracker lists publish their complete item set before background artwork enrichment, removing the screen's 240-item truncation. Trakt personal-list loading continues beyond its former 500-item limit, and public Trakt catalogs request subsequent pages rather than only the first 100 entries per type.

Logo results publish individually instead of waiting for a whole batch. A shared five-request limit bounds concurrency, upcoming cards request logos ahead of scrolling, and resolved images warm the existing Coil cache. Transient lookup failures remain retryable.

## Verification

- Debug application and instrumentation APKs built and installed on the TV emulator.
- All 12 emulator tests passed, including focused-card bounds in both layouts, remote navigation, loaded artwork, and reaching item 185 through successive pages.
- Regression tests cover Jellyfin, Emby and Plex responses without total counts; duplicate-page cursor advancement; a 305-title custom catalog; a 605-title tracker list with stalled artwork; independent logo publication; and logo retries.
- The full unit run reported 1,140 tests with one skip and one failure in a new test's missing profile stub. After correcting that test setup, the affected repository and ViewModel test classes passed in a targeted rerun. The subsequent provider-heading refinement was rebuilt after rebasing onto current main; all five Library screen emulator tests passed again, including both card layouts and focused scrolling.
- Inspected top and scrolled captures for both card modes in `artifacts/library-fixes-2026-09-14`. Captures use deterministic offline artwork; repeated titles are scrolling fixtures.

The physical TV was not used. The provider-heading screenshots were visually inspected in both layouts.
