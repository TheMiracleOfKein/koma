# Contributing to Lume

Thanks for helping improve Lume.

## Setup

1. Fork and clone the repository.
2. Copy `local.properties.example` → `local.properties` and set `sdk.dir`.
3. Open the project in Android Studio (JDK 17+) or build with Gradle:

```bash
./gradlew :app:assembleDebug
```

OAuth keys (`LUME_*`) are optional for catalogue/reader work.

## Pull requests

- Keep changes focused; match existing Kotlin / Compose style.
- Do not commit `local.properties`, APKs, IDE workspace files, or personal probe scripts.
- Do not add real OAuth client secrets to the repository.
- Update or add docs under `docs/` when behaviour of engines or packs changes.

## Issues

Use GitHub Issues for bugs and feature ideas. Include device/Android version and steps to reproduce when reporting bugs.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0 (see [LICENSE](LICENSE)).
