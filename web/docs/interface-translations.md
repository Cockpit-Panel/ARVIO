# Interface languages

The web interface follows `settings.language`, the same profile preference that
ARVIO Cloud exchanges with Android as `contentLanguage`. Changing it in Settings
updates visible UI through React context without remounting the app or clearing
search input, navigation, or player state.

`scripts/generate-translations.mjs` exports Android `strings.xml` translations to
`public/i18n`. It runs before every production build. The deployment workflow also
watches Android string resources, so translation changes reach the web app.
Only the active language is downloaded; the manifest includes content hashes to
avoid stale dictionaries in installed PWAs. Regional variants fall back to the
matching language, with explicit Chinese script and legacy Hebrew/Norwegian
handling. Missing translations and failed downloads retain readable English.

Add web-specific wording in `lib/i18n/<locale>.json`. Spanish covers the web-only
interface in addition to the shared Android vocabulary. Other languages reuse
their available Android translations and fall back to English for missing keys.
Translations render as escaped React text. URLs, identifiers, profile names,
custom list names, and media titles are not rewritten or saved as translations.
Metadata remains dependent on the language coverage of its provider.

Use `useTranslation()` inside a component and translate at display time. Keep
stored settings values and network identifiers in their original form. Use
named placeholders for dynamic text, e.g. `t("Play {value0}", {value0: title})`.
Never concatenate translated fragments for new sentences.

Validation: `npm test`, `npm run build`, `npm run test:i18n-ui`, and
`npm run test:library-ui`. The translation browser fixture renders production
components with offline library data at TV, tablet and phone sizes. It checks
Spanish navigation, lists, search, settings and dropdowns, live language changes,
preserved search input, unchanged profile names, and viewport overflow.
