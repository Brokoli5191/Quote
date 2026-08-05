<div align="center">
  <img src=".github/icon.svg" width="96" alt="Quote app icon">
  <h1>Quote</h1>
  <p><strong>An Android quote app with an offline curated library and moderated community quotes.</strong></p>

  <a href="https://github.com/Brokoli5191/Quote/releases/latest"><img src="https://img.shields.io/github/v/release/Brokoli5191/Quote?style=flat-square&amp;color=8b5cf6" alt="Latest release"></a>
  <a href="https://github.com/Brokoli5191/Quote/releases/latest"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 7.0+"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Brokoli5191/Quote?style=flat-square" alt="MIT License"></a>
  <a href="https://github.com/Brokoli5191/Quote/releases"><img src="https://img.shields.io/github/downloads/Brokoli5191/Quote/total?style=flat-square&amp;color=0ea5e9" alt="Release downloads"></a>

  <br>
  <a href="https://github.com/Brokoli5191/Quote/releases/latest"><strong>Download the latest APK</strong></a>
  &nbsp;&middot;&nbsp;
  <a href="https://quote.cowsay.win/privacy">Privacy policy</a>
</div>

## About

Quote brings daily inspiration, a searchable quote library, and personal collections to Android. The curated library is bundled with the app, so the core experience works without an internet connection. Community quotes are downloaded when online and remain available offline after syncing.

<div align="center">
  <img src=".github/screenshots/daily.png" width="30%" alt="Daily quote screen">
  <img src=".github/screenshots/library.png" width="30%" alt="Quote library">
  <img src=".github/screenshots/settings.png" width="30%" alt="Theme and quote source settings">
</div>

## Features

- Daily quotes with manual cycling and no-repeat history
- Search by quote, author, or tag and browse by topic
- Curated and community quote sources
- Favorites, sharing, and personal quotes
- Optional community submissions with human review
- Home-screen widgets and scheduled reminders
- JSON backup and restore
- Light, dark, Material You, and AMOLED themes
- Custom accent colors and reduced-effects mode

Personal quotes stay on your device unless you explicitly submit one for community review. Submissions are never published automatically.

## Install

Quote supports Android 7.0 and newer.

1. Open the [latest release](https://github.com/Brokoli5191/Quote/releases/latest).
2. Download the APK.
3. Allow installation from your browser or file manager if Android asks.
4. Install and open Quote.

## Build from Source

Requirements:

- JDK 17 or newer
- Android SDK with API 36

Add your Android SDK path to `local.properties`, then build with the Gradle wrapper:

```properties
sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk
```

```sh
./gradlew test assembleDebug
```

On Windows, use `.\gradlew.bat test assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The optional community service is a Cloudflare Worker backed by D1. See [`backend/README.md`](backend/README.md) for local development and deployment instructions.

## Built With

- Kotlin, Jetpack Compose, and Material 3
- Room and WorkManager
- Cloudflare Workers and D1
- JUnit, Robolectric, Roborazzi, and Vitest

## Privacy

The curated library, favorites, and personal quotes are stored locally. Community sync and optional submissions use the Quote backend. See the [Privacy & Submission Policy](https://quote.cowsay.win/privacy) for details.

## Contributing

Issues and focused pull requests are welcome. Please run the relevant Android and backend tests before opening a pull request.

## License

Quote is available under the [MIT License](LICENSE).
