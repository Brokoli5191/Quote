<div align="center">
<img width="1200" height="475" alt="QuoteFlow Banner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />

# QuoteFlow

Android application to view, search, and manage quotes.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-3DDC84)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-3DDC84)](https://developer.android.com/about/versions/15)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Database](https://img.shields.io/badge/Database-Room-4285F4?logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)

</div>

---

## Table of Contents
1. [Build & Installation](#build--installation-)
2. [Usage](#usage-)
3. [Features](#features-)
4. [Architecture & Tech Stack](#architecture--tech-stack-)
5. [Contributing](#contributing-)
6. [License](#license-)

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

2. Decode the keystore file for debug signing:
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

---

## Usage 📱

### Home Screen Widget
1. Long-press on your device's home screen.
2. Select **Widgets** and find **QuoteFlow**.
3. Place the widget on your screen.
4. Open the app settings tab to choose a widget style:
   - **Expressive**: Rich card designs displaying text and author.
   - **Minimal**: Plain, distraction-free quote layout.
   - **Compact**: Small footprint layout.

### Daily Reminders
1. Navigate to the **Settings** tab.
2. Toggle the **Daily Reminder** switch.
3. Configure the specific hour and minute for notification delivery.

### Backup & Restore
1. Navigate to the **Settings** tab.
2. Tap **Export Backup** to save your custom quotes and favorite states to a JSON file.
3. Tap **Import Backup** and select your saved JSON file to restore the data.

---

## Features ✨

- **Daily Quotes**: Automatically schedules a daily quote, with support for manual cycling.
- **Categorized Library**: Multi-category filter support (Stoicism, Resilience, Joy, Focus, Love, Custom) and text search.
- **Custom Quote Management**: Direct interface to add and delete user-authored quotes.
- **Favorites Space**: One-tap bookmarking to organize saved quotes.
- **Theming & Accents**: Light, Dark, AMOLED, and Dynamic (Material You) system colors. Accent color choices include Violet, Amber, Green, Blue, and Rose.
- **Low Performance Toggle**: Settings option to disable spring physics and page slide animations for resource-constrained devices.

---

## Architecture & Tech Stack ⚙️

- **Architecture**: MVVM pattern with Repository layer. Room database handles local SQLite persistence.
- **UI Framework**: Jetpack Compose (Declarative UI)
- **State Management**: Kotlin Coroutines and StateFlow
- **Build System**: Gradle Kotlin DSL and Version Catalogs

---

## Contributing 🤝

1. Fork the repository.
2. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature
   ```
3. Commit your changes:
   ```bash
   git commit -m "Add your feature description"
   ```
4. Push to the branch:
   ```bash
   git push origin feature/your-feature
   ```
5. Open a Pull Request.

---

## License 📄

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details (if available).
