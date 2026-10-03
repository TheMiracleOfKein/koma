# Optional source packs

## Importable (Madara / LibSocial / HiveToons)

Place a JSON file with `"engine": "madara" | "libsocial" | "hivetoons"` and open it via
**Settings → Источники → Импортировать пак по ссылке** (host the file anywhere HTTPS).

Examples:

- [madara/likemanga.json](madara/likemanga.json)
- [madara/toongod.json](madara/toongod.json)

## Site manifests (not importable)

Sample identity packs for one-site engines that ship only with the app binary.
Re-add under `app/src/main/assets/sources/` only if you decide to bundle them again.

- [site/weebcentral.json](site/weebcentral.json)
- [site/demonicscans.json](site/demonicscans.json)
