# QuoteFlow

An Android application for viewing, searching, and managing quotes. Includes a daily quote widget and category browsing.

## Features

- Daily quote selection with manual cycle/refresh
- Filter by topics like Stoicism, Resilience, Joy, Focus, Love, and Custom entries
- Text search across quote content, authors, and tags
- Favorite quotes and custom quote creation
- System themes (Light, Dark, AMOLED, and Dynamic/Material You) with Violet, Amber, Green, Blue, Rose accents
- Low performance toggle to disable spring animations for older devices

## Usage

### Widgets
Long press home screen -> Widgets -> QuoteFlow. Choose Expressive, Minimal, or Compact styles in the app settings.

### Notifications
Toggle "Daily Reminder" in Settings and select the notification time.

### Backup
Export/import custom quotes and favorites to JSON in Settings.

## Build and Run

### Download
Get the APK from the GitHub releases page.

### From Source
1. Add your Android SDK path to `local.properties`:
   `sdk.dir=C:/Users/<Username>/AppData/Local/Android/Sdk`
2. Decode the debug keystore:
   `certutil -decode debug.keystore.base64 debug.keystore`
3. Run `.\gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug` (Mac/Linux).

The APK is built at `app/build/outputs/apk/debug/app-debug.apk`.

## Tech Stack
- Kotlin & Jetpack Compose
- Room Database
- Gradle Kotlin DSL
