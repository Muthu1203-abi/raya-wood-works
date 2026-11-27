# Raya Wood Works Android app

Quotation builder for Sakthivel Raya Wood Works, packaged as an Android app with Capacitor.
Everything works offline on the phone: quotations (sqft, running ft, nos, lumpsum items), customers,
payments and balances, reports, Tamil/English UI, and a walnut-letterhead PDF with a UPI scan-to-pay QR,
shared through Android's share sheet.

The whole web app lives in `www/index.html`; libraries are bundled in `www/vendor/` so nothing loads from the internet.

## Updates (GitHub Releases)

Every push to `main` runs `.github/workflows/release.yml`, which:

1. builds the APK with `VERSION_CODE = run number + 1` and `VERSION_NAME = 1.0.<code>`,
2. signs it with the key in the `SIGNING_KEYSTORE_B64` repository secret (the same key as the APK already
   installed on the phones, so updates install over it and keep the data),
3. publishes it as release `v<code>` with the commit message as the release notes.

When the installed app opens, it reads the latest release from the GitHub API. If its `versionCode` is newer,
the app shows **Update available → Download update**, downloads the APK inside the app and opens the Android
installer. The first time, Android asks to allow **Install unknown apps** for Raya Wood Works.
**Settings → App updates** checks manually and shows the installed version.

The repository must stay **public**, because the app reads releases without a token.

## Local build

Requirements: Node.js 22+, JDK 21 (Android Studio's bundled JDK works), Android SDK.

```powershell
npm install
npx cap sync android
cd android
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`. Local builds have versionCode 1, so they
cannot be installed over a newer release from GitHub; install releases on the phones instead.

## Data and backup

- Quotations are stored only on the phone (WebView local storage).
- **Backup → Save backup file** shares one `.json` file with all quotations and settings (keep it in WhatsApp,
  the Drive app or phone storage). **Restore from file** merges it back: the newest edit of each quotation
  wins and deleted quotations stay deleted. The Backup page warns when no file was saved for 7 days.
