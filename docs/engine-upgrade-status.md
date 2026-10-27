# Engine 0.11 Android integration

Latest stable app: 0.0.6, version code 6990. Engine: 0.11.0.
Current test build: 0.0.7-beta.2, version code 7020.
Upstream: https://github.com/storytold/wordcraft/releases/tag/v0.11.0
Commit: `e31f2f06a5f8004ceaa88f68c00b47908e321aa4`.

The 0.10 → 0.11 changes were three-way merged into the Android integration,
retaining Android picture frames/effects, original SVGs, chart tools, gradients,
opacity, mobile view and Arabic/English UI. No existing command IDs were removed.
The 0.11 bounded picture decoder and raster-edit allocation checks are retained;
Android picture framing still stores document properties without modifying source pixels.

Validation: 3217 Rust tests (18 targets, serialized shared-dictionary fixtures),
22 Kotlin tests, 4 Arabic resource tests; signed universal Release + compatibility
Debug build; Lint 0 errors / 33 warnings / 8 hints. APK signatures, ABIs and checksums
verified. Emulator x86_64 UI/update checks do not imply physical ARM-phone testing.

The app checks its own GitHub Releases for newer stable APKs, with manual checks,
a six-hour foreground cache, a daily persisted network job, a themed in-app notice
opened only by tapping a circular update icon on Home. No Android notification
permission is requested and no system notifications are posted. It opens the release
page as an optional details link. Version 0.0.5 downloads a matching Release APK
in-app, verifies its SHA-256, package, newer version and signing certificate, then
opens Android installation only after the user taps Install. It does not install
updates automatically.

See [the Arabic 0.0.1 release report](RELEASE-0.0.1-ar.md) for artifacts, signing,
upgrade compatibility, implementation details and validation limits. The beta5 and
beta6 reports remain historical reports of those earlier builds.

See [the 0.0.2 UI update report](RELEASE-0.0.2-ar.md) for the circular icon and settings sections.

See [the 0.0.3 report](RELEASE-0.0.3-ar.md) for the independent New destination and responsive template cards.

See [the 0.0.4 report](RELEASE-0.0.4-ar.md) for synchronized Android IME composition,
existing-word replacement and removal of the overlapping composition overlay.
This release passed 53 Android Rust bridge tests, 29 Kotlin tests and 4 Arabic
resource tests. The earlier 3217-test engine run above belongs to the engine merge;
the full engine suite was not rerun for this bridge/input fix.

See [the 0.0.5 report](RELEASE-0.0.5-ar.md) for bilingual release details, cancellable
in-app downloads and the Android installer handoff.

See [the 0.0.6-beta.2 test report](TEST-0.0.6-beta.2-ar.md) for the new app themes,
centered language/appearance windows and fixed English launcher name.
The user approved beta.2. Stable 0.0.6 contains the same implementation with the final version metadata. See [the 0.0.6 release report](RELEASE-0.0.6-ar.md).

See [the 0.0.7-beta.1 test report](TEST-0.0.7-beta.1-ar.md) for physical versus logical paragraph alignment, inherited RTL styles, mixed-direction selections, and Arabic alignment controls. Version 0.0.6 remains stable while this correction is tested.

See [the 0.0.7-beta.2 test report](TEST-0.0.7-beta.2-ar.md) for preserving Arabic-Indic, Western and Persian digits as written in Android display and PDF export. Explicit numeral-conversion modes retain their existing behavior.
