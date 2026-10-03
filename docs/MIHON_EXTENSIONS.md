# Mihon / Tachiyomi extensions — deferred

Koma deliberately does **not** load third-party Mihon/Tachiyomi extension APKs at runtime.

## Why ExtensionLoader stays out of scope

1. **Security & Play policy** — Dynamic DEX/APK loading of untrusted catalogue code is high-risk.
2. **API drift** — Tachiyomi / Mihon `source-api` evolves with the host app.
3. **Licensing clarity** — Extension APKs carry their own licenses and site ToS.
4. **Native Kotlin stack** — Catalogue logic stays in-process first-party engines.

## What we do instead

**Platform engines + family JSON packs.** See the product model in [ENGINES.md](ENGINES.md).

- New scrapers/APIs → new Kotlin engine in an app release.
- Another Madara / cdnlibs / Iken twin → JSON pack (`madara` / `libsocial` / `hivetoons`) via Sources import.
- Not a plugin marketplace.
