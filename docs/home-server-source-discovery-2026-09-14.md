# Home-server source discovery

## Corrected paths

- Episode resolution previously selected one best series. Separate HD/UHD series entries on a single server are now searched for the requested episode, retaining media versions from each matching entry. Strong matching IDs remain eligible even when another copy supplies more matching IDs.
- Exact title-only series matching scored below the acceptance threshold when no year/provider metadata was available. Exact matches now qualify; conflicting provider IDs and year-mismatched remakes remain rejected.
- Shared Plex GUID-filter failures fall back to title search. Unrelated global search hits no longer prevent section-specific fallback. Case-equivalent Plex GUID queries and literal-ID text searches were removed. Plex movie title lookup and series title lookup run alongside provider queries.
- Details/player background source append previously stopped waiting at five seconds although cached discovery continued. The append now waits up to twenty seconds without blocking independent add-on lookup. This is a maximum wait, not a minimum playback delay or an instant-loading guarantee.

Existing server tokens/permissions and quality labels are unchanged. No owner/admin credentials are required. The code does not bypass denied access or relabel 1080p sources as 4K.

## Verification

Android app compilation succeeded. Full unit suite: 1,177 tests, 1,176 passed, one skipped, zero failures/errors (`build/home-server-validated-tests.log`). Fixtures verify restricted shared-Plex GUID lookup with section-title fallback, permission denial, separate Emby HD/UHD entries, exact title-only matching and conflicting IDs.

Initial fixture failures were test setup issues: ambiguous Call import, a stale Robolectric DataStore directory, and missing mocked profile identity. Source-discovery fixtures now supply the saved connection directly; all three pass.

No access to the reporters' actual servers was available. No live-provider timing, TV installation, release build or GitHub push was performed for this change. Changes are on `codex/home-server-source-discovery`.
