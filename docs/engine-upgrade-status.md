# Engine 0.11 Android integration

Current app: 0.0.1, version code 1990. Engine: 0.11.0.
Upstream: https://github.com/storytold/wordcraft/releases/tag/v0.11.0
Commit: `e31f2f06a5f8004ceaa88f68c00b47908e321aa4`.

The 0.10 → 0.11 changes were three-way merged into the Android integration,
retaining Android picture frames/effects, original SVGs, chart tools, gradients,
opacity, mobile view and Arabic/English UI. No existing command IDs were removed.
The 0.11 bounded picture decoder and raster-edit allocation checks are retained;
Android picture framing still stores document properties without modifying source pixels.

Validation: 3217 Rust tests (18 targets, serialized shared-dictionary fixtures),
22 Kotlin tests, 4 Arabic resource tests; signed universal Release + compatibility
Debug build; Lint 0 errors / 36 warnings / 8 hints. APK signatures, ABIs and checksums
verified. Emulator x86_64 UI/update checks do not imply physical ARM-phone testing.

The app checks its own GitHub Releases for newer stable APKs, with manual checks,
a six-hour foreground cache, a daily persisted network job, a themed in-app notice,
and one system notification per release (permission required). It opens the release
page for download and does not install updates automatically.

See [the Arabic 0.0.1 release report](RELEASE-0.0.1-ar.md) for artifacts, signing,
upgrade compatibility, implementation details and validation limits. The beta5 and
beta6 reports remain historical reports of those earlier builds.
