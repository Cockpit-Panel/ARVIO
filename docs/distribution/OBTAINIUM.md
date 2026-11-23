# Obtainium Support for ARVIO

[Obtainium](https://github.com/ImranR98/Obtainium) allows Android users (on TV, phone, or tablet) to install and automatically update open-source apps directly from their release pages (such as GitHub Releases) without relying on closed app stores.

---

## 🚀 Quick Install (Users)

### Option 1: 1-Click "Add to Obtainium"

If you already have Obtainium installed on your Android device:

[<img src="../../assets/badges/badge_obtainium.png" alt="Get it on Obtainium" width="160">](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fgithub.com%2FProdigyV21%2FARVIO)

> [!TIP]
> - **Direct protocol link (on-device):** [`obtainium://add/https://github.com/ProdigyV21/ARVIO`](obtainium://add/https://github.com/ProdigyV21/ARVIO)
> - **Web redirect link (with install helper):** [`https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ProdigyV21/ARVIO`](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ProdigyV21/ARVIO)

---

### Option 2: Manual Addition in Obtainium

1. Open **Obtainium** on your device.
2. Tap the **+ (Add App)** button.
3. Enter the repository URL in the **App Source URL** field:
   ```text
   https://github.com/ProdigyV21/ARVIO
   ```
4. *(Optional / Recommended)* Expand **Additional Settings**:
   - **Filter APKs by Regular Expression:** `ARVIO-.*-sideload-release\.apk`
   - **Include Prereleases:** Disabled (unless you want beta builds)
   - **Fallback to older releases if no APK found:** Enabled
5. Tap **Add**. Obtainium will fetch the latest release APK and prompt you to install.

---

### Option 3: Import Pre-configured JSON

1. Download [`distribution/obtainium/com.arvio.tv.json`](../../distribution/obtainium/com.arvio.tv.json).
2. In Obtainium, go to **Settings** (gear icon) → **Import/Export**.
3. Tap **Obtainium Import** and select `com.arvio.tv.json`.
4. ARVIO will be added with optimal settings pre-configured.

---

## 🛠️ Maintainer Guide: Submitting to the Official Obtainium Directory

The official crowdsourced directory at [apps.obtainium.imranr.dev](https://apps.obtainium.imranr.dev) enables users to search for ARVIO and install it with one click.

A pre-formatted configuration conforming to the directory's schema is prepared at:
`distribution/obtainium/apps.obtainium.imranr.dev/public/data/apps/simple/com.arvio.tv.json`

### Submission Steps

1. **Fork** the repository: [ImranR98/apps.obtainium.imranr.dev](https://github.com/ImranR98/apps.obtainium.imranr.dev).
2. Copy `distribution/obtainium/apps.obtainium.imranr.dev/public/data/apps/simple/com.arvio.tv.json` to the same relative path in your fork:
   ```bash
   cp distribution/obtainium/apps.obtainium.imranr.dev/public/data/apps/simple/com.arvio.tv.json \
      path/to/forked/apps.obtainium.imranr.dev/public/data/apps/simple/com.arvio.tv.json
   ```
3. Commit and push:
   ```bash
   git add public/data/apps/simple/com.arvio.tv.json
   git commit -m "feat(apps): add ARVIO (com.arvio.tv)"
   git push origin main
   ```
4. Open a Pull Request to [ImranR98/apps.obtainium.imranr.dev](https://github.com/ImranR98/apps.obtainium.imranr.dev).
   - Check the criteria in their `APP_CRITERIA.md`.
   - Title: `Add ARVIO (com.arvio.tv)`

---

## 📦 Release Asset Conventions

To ensure Obtainium continues tracking ARVIO releases smoothly:
1. Every release must include the signed sideload release APK named matching `ARVIO-v<version>-sideload-release.apk`.
2. Keep releases marked as published (not draft).
3. If pre-releases/betas are tagged, they should be marked as "Pre-release" on GitHub so Obtainium's stable tracking filters ignore them.
