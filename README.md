<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
<h1>QuoteFlow</h1>
An Android application to view, search, and manage a collection of quotes.
</div>

---

## Build & Installation 🛠️

### Prerequisites
- JDK 21
- Android SDK

### Steps

1. Configure the Android SDK path in `local.properties`:
   ```properties
   sdk.dir=C:/Users/<Username>/AppData/Local/Android/Sdk
   ```

2. Decode the keystore file for signing:
   - **Windows**:
     ```cmd
     certutil -decode debug.keystore.base64 debug.keystore
     ```
   - **Linux / macOS**:
     ```bash
     base64 -d debug.keystore.base64 > debug.keystore
     ```

3. Build the debug APK:
   - **Windows**:
     ```powershell
     .\gradlew.bat assembleDebug
     ```
   - **Linux / macOS**:
     ```bash
     ./gradlew assembleDebug
     ```

The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Usage 📱

- **Home Screen Widget**: Long press the home screen, select the QuoteFlow widget, and place it. Change styles (Expressive, Minimal, Compact) in the app settings.
- **Daily Reminders**: Enable notifications in the settings tab and select a time.
- **Backup / Restore**: Use the settings screen to export custom quotes and favorites to JSON, or import them back.

## Features ✨

- Curated daily quote with manual cycling support
- Search and multi-category filtering (Stoicism, Resilience, Joy, Focus, Love, Custom)
- Creation and deletion of custom quotes
- Import and export collections via JSON backup
- System themes (Light, Dark, AMOLED, Dynamic Material You) and accent colors (Violet, Amber, Green, Blue, Rose)
- Low performance mode toggle to disable animations on older devices

## Tech Stack ⚙️

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room
- **Build System**: Gradle with Kotlin DSL
