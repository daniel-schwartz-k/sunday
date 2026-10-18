# Sunday

A minimal, offline-capable task manager. No build step for web — just open `index.html` or install as a PWA. Also available as a native Android app wrapper (Capacitor) with future Android home-screen widget support.

**[Live Demo / PWA](https://danielschwartz85.github.io/sunday)**

## Quick Start (Web / PWA)

```bash
git clone https://github.com/daniel-schwartz-k/sunday.git
cd sunday
python3 -m http.server 8080
# then open http://localhost:8080
```

Serve `index.html` with any static server or deploy to GitHub Pages.

## Android Native App

The native Android version wraps the identical HTML, JS and CSS via [Capacitor](https://capacitorjs.com/) into a standalone APK. It runs on Google Pixel, Samsung Galaxy, and other modern Android devices.

### Installing the APK on your device
1. Go to the GitHub repository's **Releases** or the latest run in **Actions** → `Android Build`.
2. Download `sunday-app.apk`.
3. Transfer or open the APK on your Android device (Pixel, Samsung, etc.).
4. When prompted by Android, allow **"Install unknown apps"** for your browser / file manager (Settings → Security & privacy → Install unknown apps).
5. Open **Sunday** from your launcher. It runs completely offline with its own local storage.

### Data storage notes
- **Web PWA:** Tasks are stored in your browser's `localStorage` for the web origin (`https://danielschwartz85.github.io` or `http://localhost`).
- **Android App:** Tasks are stored in the Android WebView's independent `localStorage` (`https://localhost`). Each install keeps its own data.
- **Syncing between Web and Android:** If you want tasks synchronized between your browser and your Android phone, choose **AirTable** mode on both installs — they sync to the same AirTable base.

### Building locally (optional)
Building the Android APK locally requires Node 20+, JDK 21, and Android Studio:

```bash
npm install
npm run cap:sync   # copies web assets to www/ and syncs with android/
npx cap open android # opens project in Android Studio to run on device/emulator
```

Automated release builds run in GitHub Actions on every push to `main` and on version tags (`v*`).

### Release signing configuration (GitHub Actions)
To generate signed release APKs in CI:
1. Generate a keystore once:
   ```bash
   keytool -genkeypair -v -keystore sunday-release.jks -alias sunday -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Encode to base64:
   ```bash
   base64 -w0 sunday-release.jks
   ```
3. Add repository secrets in GitHub (`Settings` → `Secrets and variables` → `Actions`):
   - `ANDROID_KEYSTORE_BASE64`: base64 output from step 2
   - `ANDROID_KEYSTORE_PASSWORD`: keystore password
   - `ANDROID_KEY_ALIAS`: `sunday`
   - `ANDROID_KEY_PASSWORD`: key password

If the secrets are not configured, CI automatically falls back to building an unsigned/debug APK.

## Storage Modes

On first load you pick a mode:

| Mode | Description |
|------|-------------|
| **LocalStorage** | Default. Tasks stay in your browser/device. |
| **AirTable** | Syncs to an AirTable base (prompts for credentials once). |

### AirTable Setup

1. [Sign up for AirTable](https://airtable.com/signup) (free) and create a new "base"
2. Add a table with a single **Long text** field named `data`
3. Grab your Base ID and table name from the AirTable URL:

<img width="1455" height="488" alt="Airtable credentials" src="https://github.com/user-attachments/assets/dd883098-a0d3-4324-ac53-ab18d02a5b4b" />

4. Create a [Personal Access Token](https://support.airtable.com/docs/creating-personal-access-tokens#creating-personal-access-tokens)
