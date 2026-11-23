# Privacy Policy

**Last updated: October 14, 2026**

## 1. Scope And Controller

This policy explains how the ARVIO project processes personal data in the Android app, ARVIO Cloud account portal, and ARVIO Web. ARVIO is an open-source media hub and does not host, sell, or distribute movies, series, live TV channels, playlists, or third-party streams.

The ARVIO project owner is the data controller for ARVIO Cloud, the account portal, first-party usage measurements, and first-party website services. Third-party services that you connect may act as separate controllers under their own privacy policies.

For privacy requests, use the secure account deletion page at [auth.arvio.tv/delete](https://auth.arvio.tv/delete) or contact the project through [GitHub](https://github.com/ProdigyV21/ARVIO). Do not put passwords, tokens, identity documents, or other sensitive data in a public GitHub issue.

## 2. Data Stored Only On Your Device

ARVIO can store profiles, preferences, playback progress, watch history, watchlist entries, addon configuration, IPTV configuration, home-server configuration, caches, and authorization tokens locally on your device. This local data is not received by ARVIO unless you enable a cloud feature, send a diagnostic report, or connect a third-party service that needs it.

You can remove local data by using the relevant in-app controls, clearing Android app data, or uninstalling the app.

## 3. ARVIO Cloud Account And Sync

Creating an ARVIO Cloud account processes:

- Your email address, account identifier, password hash, authentication sessions, and password-reset records
- Profiles, settings, catalog order, addon configuration, IPTV configuration, watchlist, watch history, playback progress, and other data you choose to sync
- Security and operational information such as timestamps, request metadata, and IP address in infrastructure logs

ARVIO stores a one-way password hash, not your plain-text password. ARVIO Cloud is optional; the Android app can be used without an ARVIO Cloud account.

The legal basis for account authentication and requested cloud sync is performance of the service you request (GDPR Article 6(1)(b)). Security, abuse prevention, reliability, and limited operational logging are based on ARVIO's legitimate interests in operating and protecting the service (Article 6(1)(f)).

## 4. Diagnostics And Usage Measurement

Release builds have two independent, device-local choices in Settings: **Share crash diagnostics** and **Share usage statistics**. Neither choice is cloud-synced. Usage statistics are off by default. Existing crash-reporting preferences are preserved.

### Crash diagnostics

ARVIO can send Java/Kotlin and native crash stack traces, exception details, app version and build, device model, Android version, and limited diagnostic breadcrumbs to Firebase Crashlytics. Older Android devices can send a bounded non-fatal report when the foreground UI freezes. No screenshots or session recordings are collected. App-provided diagnostic logs are sanitized to remove URLs, email addresses, tokens, and IP addresses. Exception text and infrastructure transport can still contain personal data such as an IP address, so ARVIO does not describe crash reports as anonymous. New builds no longer contain the Sentry SDK; older installed builds can continue reporting to Sentry until updated.

### Optional usage statistics

When you enable **Share usage statistics**, Firebase Analytics measures sessions, fixed screen names, playback startup time, playback error codes, and a bounded buffering sample. It uses an app-instance identifier and device/app information. ARVIO does not send watched titles, searches, provider URLs, account identifiers or email addresses to Firebase Analytics. Advertising identifiers, ad storage, advertising personalization and automatic screen reporting are disabled. Disabling usage statistics stops collection and resets the local Analytics state. These optional statistics use your consent; declining them does not affect playback or cloud sync.

### App-open measurement

At most once per 24 hours, ARVIO can send an `app_open` event containing a random install identifier, app version, Android API level, device type, distribution type, and account/profile identifiers when signed in. The backend converts direct identifiers into keyed pseudonymous values before storing the event. This measurement is used only to estimate daily and weekly active use and release adoption; it is not used for advertising or user profiling.

Crash diagnostics are based on ARVIO's legitimate interests in finding crashes and maintaining a reliable service (Article 6(1)(f)); you can object by turning **Share crash diagnostics** off. The app-open measurement above runs only with the separate usage-statistics opt-in. Turning either choice off does not affect playback, accounts, or cloud sync.

### Website analytics and the ARVIO Premium funnel

The public website uses Netlify's server-side traffic analytics to count page requests, approximate unique visitors, referrers, and countries from delivery logs. ARVIO does not add advertising trackers or cross-site profiling cookies.

Premium pages also measure page views and clicks to ARVIO Web or Ko-fi using a random, short-lived navigation ID and coarse campaign tags. The website keeps this ID in memory and passes it only between ARVIO pages; it is not sent to Ko-fi. ARVIO Web can retain this attribution in session storage for up to 24 hours to connect a visit with the subsequent sign-in. Browser Do Not Track and Global Privacy Control signals disable this browser measurement. The endpoint uses short-lived keyed network hashes to limit abuse; it does not store raw IP addresses in these measurement records.

After you sign in to ARVIO Web, ARVIO records a small set of Premium service events such as viewing the membership screen, opening checkout, linking a membership, and completing the first successful playback. Historical records may include trials offered before October 23, 2026; new hosted Web trials are no longer available. Account identifiers are converted into a keyed pseudonymous value before storage. Events can contain coarse campaign attribution and device or outcome labels, but not your email address, watched title, stream URL, addon URL, or payment details. Ko-fi separately handles payment details under its own privacy policy.

These measurements are used to understand whether account connection, trials, playback, and membership activation work correctly and where the service needs improvement. They are not used for advertising or sold to third parties. They are based on ARVIO's legitimate interests in operating and improving the optional Premium service (Article 6(1)(f)).

### Retired trial emails

Hosted ARVIO Web no longer offers trials or sends trial confirmation, reminder or expiry messages. Pending legacy trial jobs are suppressed in bounded background batches and their encrypted delivery addresses are removed. Minimal audit records remain until normal retention cleanup. This retired queue is not a general marketing mailing list. Account verification and password-reset emails are unchanged.

## 5. Optional Third-Party Services

Data is also sent to services you deliberately use or connect:

| Service | Purpose | Privacy policy |
| --- | --- | --- |
| Netlify infrastructure | ARVIO websites, account functions, cloud sync, and storage | [netlify.com/privacy](https://www.netlify.com/privacy/) |
| Resend | Account verification and password-reset email delivery | [resend.com/legal/privacy-policy](https://resend.com/legal/privacy-policy) |
| Firebase Crashlytics and Google Analytics for Firebase | Optional crash diagnostics and separately opted-in usage statistics | [firebase.google.com/support/privacy](https://firebase.google.com/support/privacy) |
| TMDB | Movie and TV metadata and images | [themoviedb.org/privacy-policy](https://www.themoviedb.org/privacy-policy) |
| Trakt.tv | Optional watch history, progress, ratings, and watchlist sync | [trakt.tv/privacy](https://trakt.tv/privacy) |
| Discord | Optional Rich Presence login and the title/playback status you choose to display | [discord.com/privacy](https://discord.com/privacy) |
| Ko-fi | Optional ARVIO Web membership verification | [ko-fi.com/privacy](https://ko-fi.com/privacy) |
| User-configured services | Addons, IPTV providers, Plex, Jellyfin, Emby, debrid services, and URLs you add | Governed by the provider you configure |

ARVIO sends only the data needed for the requested integration. Third-party services may process data outside the European Economic Area under their own transfer mechanisms and terms.

## 6. Retention

- Local data remains until you remove it, clear app data, or uninstall ARVIO.
- ARVIO Cloud account and sync data remains while the account is active and is deleted when account deletion completes, except for a minimal record required to prove or secure the deletion.
- Stored first-party app-open events are retained for up to 31 days.
- Pseudonymous Premium funnel events are retained for up to 90 days.
- Public Premium navigation events are removed after a 30-day calendar window, and measurement rate-limit records after two days. Cleanup runs periodically.
- Retired Premium trial email jobs are suppressed without sending; encrypted delivery addresses are removed during suppression. Completed or suppressed audit records are removed after 14 days by periodic cleanup.
- Password-reset and device-pairing codes expire after a short operational period.
- Crash diagnostics follow Firebase retention; historical Sentry reports remain subject to its retention. Analytics follows the configured Google Analytics retention. Data is kept only as long as needed to investigate stability and service usage.
- Infrastructure security logs follow the hosting provider's operational retention schedule.

ARVIO may keep data longer where required by law, to resolve a dispute, or to prevent abuse, and will keep only what is necessary for that purpose.

## 7. Security

ARVIO uses HTTPS for first-party cloud services and supported APIs, hashes account passwords with a salted memory-hard password function, limits crash-report contents, and scopes cloud data to authenticated accounts. No system is risk-free.

ARVIO also permits user-configured HTTP URLs for local servers and IPTV providers because some private-network services do not support HTTPS. HTTP traffic is not encrypted. Prefer HTTPS and only add services you trust.

## 8. Your Rights

Depending on where you live, you may have the right to access, correct, export, restrict, object to, or delete your personal data, and to complain to your local data-protection authority. You can:

- Disable diagnostics and usage reporting in app Settings
- Disconnect Trakt, Discord, and other optional services in app Settings
- Revoke ARVIO from the relevant third-party account
- Delete your ARVIO Cloud account and synced data at [auth.arvio.tv/delete](https://auth.arvio.tv/delete)

ARVIO may need to verify that a request concerns your account before acting on it.

## 9. Children's Privacy

ARVIO is not directed at children who cannot legally manage their own online account in their country. ARVIO does not knowingly create cloud accounts for such children without authorization from a parent or guardian.

## 10. Changes

This policy may change when ARVIO's services or legal obligations change. Material changes will be published with a new date, and an in-app or account notice will be used when appropriate.

---

*ARVIO is an open-source project licensed under Apache 2.0.*
