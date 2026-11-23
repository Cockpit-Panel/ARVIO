# Live TV background connections

## Findings

The Live TV screen released its player on composition disposal, but only paused it on Android ON_PAUSE. Pressing Home need not dispose that screen. A paused Media3 player can retain its loader and network connection. Source resolution and delayed retries also remained eligible while the activity was in the background.

OkHttp dispatcher cancellation alone is insufficient for response bodies that remain open after execute/response callback completion. A local streaming-socket regression test failed with dispatcher-only cancellation.

## Changes

- Stop the Live TV player on pause/stop, cancel retries, cancel explicitly tracked streaming bodies, and evict idle connections belonging to this screen's private HTTP client.
- Cancel source resolution on background transitions and prevent late resolutions/retries from preparing background playback.
- Resume the retained source once, at the live edge for live channels, preserving catchup position and a user's paused state.
- Stop the hidden mini-player while browsing Sports; resume it when returning to the guide. Fullscreen playback from Sports remains allowed.
- Release and close this screen's connections on disposal. No shared application networking is cancelled.

## Scope

Tests use mocked player lifecycle actions and a real loopback HTTP socket, not an IPTV provider. Existing provider request budgets/cooldowns are unchanged. This fixes identified client-side lifecycle faults, but does not establish that they caused the reported household-wide block. Provider-side account/IP logs would be needed to distinguish session limits, rate limiting and other restrictions. Metadata jobs in the TV ViewModel are not cancelled by this playback-specific change. No TV installation or deployment is implied by these tests.
