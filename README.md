# Koma

Android manga / comics reader written in **Kotlin** and **Jetpack Compose**.

Koma uses first-party catalogue engines (MangaDex, Madara, LibSocial, and others). JSON packs can clone supported families without shipping new Kotlin code. See [docs/ENGINES.md](docs/ENGINES.md).

![Koma](branding/koma_icon_320.png)

## Features

- Library, home feed, catalogue, search, downloads, offline cache
- Vertical (webtoon) and page reader
- Source packs + in-app login for supported LibSocial sites
- Optional trackers: Shikimori and AniList
- Backup / restore
- English and Russian UI

## Requirements

- JDK 17+
- Android SDK (API 37 recommended; `minSdk` 26)
- Android Studio or command-line Gradle

## Build

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/`.

1. Copy [`local.properties.example`](local.properties.example) to `local.properties`.
2. Set `sdk.dir` (Android Studio usually does this for you).
3. Optionally fill `KOMA_*` OAuth keys for tracker login (see below).

## Tracker OAuth (optional)

Shikimori / AniList need **application** client credentials that identify Koma. End users still sign in with their own accounts.

- Keep real keys only in `local.properties` (gitignored).
- Official release builds you produce locally (or later via CI secrets) can bake keys into the APK via `BuildConfig`.
- Keys inside an APK are not strongly secret (they can be extracted). Keeping them out of git still avoids casual leaks and bot scraping of the repository.
- If you build from source without keys, reading works; tracker login will not.

Register your own OAuth apps on the tracker sites if you maintain a fork or personal build.

## Documentation

- [Catalogue engines](docs/ENGINES.md)
- [Localization notes](docs/I18N.md)
- [Sample packs](docs/packs/README.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Disclaimer

Koma does **not** host manga or comic content. Catalogues and images come from third-party websites and APIs. Koma is not affiliated with those services. You are responsible for complying with the laws and terms that apply where you use the app.

## License

Licensed under the [Apache License 2.0](LICENSE).

Copyright 2026 TheMiracleOfKein
