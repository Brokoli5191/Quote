# QuoteFlow (Aura)

QuoteFlow is a premium, beautifully crafted Android application designed to curate, organize, and deliver inspiring philosophical and motivational quotes. Built on top of **Jetpack Compose** and Android's modern design patterns, it offers a seamless, highly customizable, and fluid experience to help you stay inspired throughout your day.

---

## 🌟 Key Features

### 1. **Curated Daily Quote**
- Displays a thoughtfully chosen quote every day.
- Supports **manual quote cycling** to refresh your screen and discover new wisdom instantly.

### 2. **Philosophical Quote Library**
- Browse quotes across several core philosophical and thematic categories:
  - 🏛️ **Stoicism**
  - ⚓ **Resilience**
  - ☀️ **Joy**
  - 🎯 **Focus**
  - 💖 **Love**
  - ✍️ **Custom** (your own additions)
- **Multi-category filtering**: Select multiple categories simultaneously to drill down to specific philosophies.
- **Full-Text Search**: Instantly search across quote text, author names, or descriptive tags.

### 3. **Custom Quote Creation**
- Create and manage your own collection of custom quotes.
- Edit text, assign authors, specify categories, and add custom tags.

### 4. **Collections & Favorites**
- Favorite any quote with a single tap.
- Save and organize your favorite quote library in a dedicated, clean "Saved" space.

### 5. **Backup & Restore System**
- **Export Backup**: Export all custom quotes and favorite states into a structured JSON file.
- **Import Backup**: Restore your custom quotes and favorite states on any device.

### 6. **Daily Reminders**
- Set up automated daily notifications.
- Fully customize the delivery hour and minute from the settings panel.

### 7. **Dynamic Home Screen Widget**
- Brings daily quotes directly to your Android home screen.
- Offers three layout styles:
  - **Expressive**: Rich card designs with authors.
  - **Minimal**: Sleek, distraction-free text.
  - **Compact**: Space-saving layout.
- Auto-updates instantly when the daily quote updates.

---

## 🎨 Premium Design System

QuoteFlow is designed to be highly responsive, modern, and visually delightful:

*   **Customizable Theme Modes**: Choose between **Light Mode**, **Dark Mode**, true **AMOLED Black**, or native **Dynamic Color** (Material You on Android 12+).
*   **Palette Accents**: Personalize the interface with elegant primary color accents including **Violet**, **Amber**, **Green**, **Blue**, and **Rose**.
*   **Micro-Animations & Physics**:
    *   Dynamic bouncy spring transitions for tab switching.
    *   Elastic scaling animations for click gestures.
    *   Integrated tactile **Haptic Feedback** for physical feedback during interactions.
*   **Low Performance Mode**: A toggle designed for older or battery-restricted devices, scaling back intensive spring animations and complex transitions to ensure a smooth 60fps experience.

---

## 🛠️ Technical Stack & Architecture

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) (declarative UI toolkit)
- **Database**: [Room SQLite Database](https://developer.android.com/training/data-storage/room) (offline caching and storage)
- **State Management**: Kotlin [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [StateFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/) (reactive architecture)
- **Build System**: Gradle Kotlin DSL (settings and dependency version catalog via version alignment)

---

## 🚀 Building & Running the Project

To compile and build QuoteFlow on your local machine:

### Prerequisites
- Java JDK 21
- Gradle 9.4.1+ (or use the Gradle wrapper)
- Android SDK

### Steps to Build
1.  **Configure Android SDK**:
    Create or open `local.properties` in the project root directory, and specify your Android SDK path:
    ```properties
    sdk.dir=C:/Users/<Username>/AppData/Local/Android/Sdk
    ```
2.  **Decode Keystore**:
    The build requires a signing keystore. Decode the base64-encoded keystore file located in the root:
    - **Windows (PowerShell/CMD)**:
      ```cmd
      certutil -decode debug.keystore.base64 debug.keystore
      ```
    - **macOS / Linux**:
      ```bash
      base64 -d debug.keystore.base64 > debug.keystore
      ```
3.  **Generate Gradle Wrapper** (if needed):
    ```bash
    gradle wrapper
    ```
4.  **Assemble Debug Build**:
    - **Windows**:
      ```powershell
      .\gradlew.bat assembleDebug
      ```
    - **macOS / Linux**:
      ```bash
      ./gradlew assembleDebug
      ```

The output debug APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`
