# OLED Library implementation

Visual target: refined-oled concepts, 13 September 2026. Real source names, artwork and user card settings replace illustrative content.

1. Retain existing global navigation. Use one compact row for Watchlists / My lists / Libraries and search/filter actions, black canvas without panel dividers, narrow contextual source navigation, and white focus with black text (settings accent supported).
2. Watchlists groups local and tracker statuses. My lists presents custom catalogs as single-cover collections, then opens their titles. Libraries groups actual server libraries by connection. Preserve source IDs, server paging and error/retry paths.
3. TV uses focusable controls and lazy grids. Phone replaces the sidebar with a source selector; tablet keeps a compact sidebar when space permits. Grid columns derive from available width and poster/landscape preference. Landscape media cards retain clearlogo fetching and title fallback.
4. Filters open from the right, trap focus and restore it on close. Native keyboard handles search. Stable artwork geometry and modest focus animation prevent layout jumps. Preserve viewport per source, and fetch only visible list previews/artwork.
5. Apply the same information architecture to web with responsive CSS, accessible dialogs, lazy content, existing card actions and tracker/server data loaders.
6. Validate source classification and layout sizing, Android build and unit tests, web tests/typecheck/build, and emulator touch/remote navigation. Compare loaded TV, phone/tablet and poster/landscape captures with the visual target.

## Implemented

The Android screen now uses one section toolbar, a narrow source menu on TV/tablet, a source picker on phones, a lazy title grid, and single-cover personal lists. Trakt personal lists are separated from its standard watchlist. Search uses the existing native keyboard modal; filters open at the right. New list opens the existing catalog setup flow. List preview requests are limited to two at a time. Horizontal cards retain the shared clearlogo and artwork fallback behavior; poster mode retains its portrait ratio.

Web follows the same hierarchy with a virtualized grid, bounded collection-preview concurrency, keyboard support, an accessible filter dialog, and source-specific viewport restoration. Existing server paging and media identities remain in use. The tracker-outage regression fixture now explicitly selects both trackers instead of depending on the application's default read preference.

## Verification

- Android unit suite after rebasing on main: 1,133 tests, zero failures, one skipped.
- TV emulator: nine tests covering loaded artwork, filters, title opening, collections, server sources, remote movement, long sidebars, and pagination without a viewport reset.
- Phone and tablet emulator layouts: three loaded-screen tests each, covering horizontal/poster cards, list/server navigation, and filters.
- Web: 579 unit tests passed; TypeScript checking and the production build passed.
- Browser UI checks at 1672×940, 1024×768, and 390×844: no horizontal overflow, virtualized rendering, clearlogos only on horizontal cards, right-side dialog placement, keyboard movement, source scroll restoration, and no runtime exceptions.

Run the browser component checks with `npm run test:library-ui` from `web` (Chrome required). Android device tests require the debug and test APKs built with `-PincludeX86Abis=true` on x86 emulators.

## Visual evidence and scope

Captures are in `artifacts/library-emulator`, with TV, phone, tablet and browser variants. These render the production Library components with deterministic offline artwork and test data, including repeated entries for long-scroll checks. The approximately 2 MB of TMDB artwork lives only in Android test assets and is not part of the production APK. Live personal Trakt, Plex, Jellyfin and Emby accounts were not exercised by these fixture tests.

The implementation follows the concept's information hierarchy, compact spacing, continuous OLED canvas and focus treatment. Generated artwork, real source names, existing global navigation, and user-selected card layouts mean the raster concepts are not a universal pixel-for-pixel reference across devices.

Before delivery, all three approved TV concepts were visually compared with the implementation captures. Phone collections, phone poster/horizontal grids, tablet poster grids and desktop web captures were also inspected. The compact black layout and focus treatment follow the concepts; artwork, typography and available source metadata differ. A clipped Connect server label found in this review now wraps on narrow sidebars. Browser checks, all 579 web unit tests and the production web build passed again on the rebased source.
