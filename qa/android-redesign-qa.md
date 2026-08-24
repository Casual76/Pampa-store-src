# Pampa Store Android redesign - QA record

## Scope

- Android-only application rebuilt on Fluid Engine 1.2.3.
- Device: Samsung SM-S931B (`RFCY61FZDHW`), Android 16 / API 36.
- Physical display restored after adaptive checks: 1080 x 2340 px, physical density 480 dpi, user override 420 dpi.
- No Store release was published and no historical installer asset was deleted.

## Automated gates

- Unit tests: 8/8 passed, covering manifest compatibility, cache round-trip/offline fallback, filters and hero counts, version comparison, stable/beta channels, theme preferences, and install-state modelling.
- Compose device tests: 5/5 passed, covering Home/navigation, Catalog/search/filter, Detail/channel/permission, catalog error state, and More/theme/unknown-sources.
- Android lint: debug variant.
- Build: debug and signed release APK.
- Signing: release APK verified with one signer and APK Signature Scheme v2.

## Physical-device flows

- Navigation and scroll across Home, Catalog, Detail, and More.
- System/light/dark appearance, Nebula 76 and Material You.
- Search for `Codex` and Installed filter.
- Offline startup from the last valid catalog cache with Wi-Fi and mobile data restored to their original state afterwards.
- Android unknown-sources authorization route.
- Full download, verification, Package Installer confirmation, and completed-install state using Codex 0.2.4. The temporary QA installation was removed afterwards; no pre-existing app data was removed.
- Production-package in-place install over the existing signed `com.pampa.store` APK.

## Visual matrix

- Compact: Home, Catalog, Detail, More, light, dark, Material You, offline cache, and completed installation.
- Medium: Home, Catalog, Detail, More light/dark with adaptive rail.
- Tablet: Home, Catalog, Detail, More light/dark with adaptive rail.
- Reference comparison: selected mock and final Home normalized side by side in `artifacts/qa/home-comparison-final.png`.
- Visual evidence is captured on the real device in `artifacts/qa`. These are reviewed golden captures; an automated screenshot-baseline regression runner is not part of this changeset.

## Performance snapshot

- Release warm run 1: 808 frames, 26 janky frames (3.22%); frame percentiles p50/p90/p95/p99 = 12/14/17/38 ms.
- Release warm run 2: 812 frames, 23 janky frames (2.83%); frame percentiles p50/p90/p95/p99 = 12/15/16/29 ms.
- Memory snapshot after the exercised flow: total PSS 283,359 KB. This is a single diagnostic sample, not evidence of a leak trend.
- Raw evidence: `artifacts/qa/performance/gfxinfo-release-run1.txt`, `gfxinfo-release-run2.txt`, and `meminfo-release.txt`.

## Result

The implemented Android redesign passes the functional, build, signing, visual, adaptive-layout, and physical-device checks recorded above. Release preparation and publication remain a separate activity.
