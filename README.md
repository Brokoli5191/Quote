# QuoteFlow

Android application to view, search, and manage quotes.

## Build and Installation

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

## Usage

- **Home Screen Widget**: Long press the home screen, select the QuoteFlow widget, and place it. Change styles (Expressive, Minimal, Compact) in the app settings.
- **Daily Reminders**: Enable notifications in the settings tab and select a time.
- **Backup / Restore**: Use the settings screen to export custom quotes and favorites to JSON, or import them back.

## Features

- **Daily Quote**: Displays a daily quote with manual cycling support.
- **Library**: Quote filtering by text search and multiple categories (Stoicism, Resilience, Joy, Focus, Love, Custom).
- **Custom Quotes**: User-added quotes can be created and deleted.
- **Themes**: Supports Light, Dark, AMOLED, and Dynamic (Material You) modes with Violet, Amber, Green, Blue, and Rose accent colors.
- **Low Performance Mode**: Toggle to disable spring animations and slide transitions on older devices.

## Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Database**: Room
- **Build System**: Gradle with Kotlin DSL
