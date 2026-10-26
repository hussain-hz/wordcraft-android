# Engine 0.10 Android integration

Upstream target: WordCraft v0.10.0, released 2026-10-22,
commit `9130181a5fdaee2293e67756fd5458c601489f3c`.
Source: https://github.com/storytold/wordcraft/releases/tag/v0.10.0

The workspace now builds against the merged 0.10 engine. This is an Android
integration of that release, retaining the fork's features, not an unmodified
upstream checkout. Validation and universal APK build are complete. Current app version: 0.0.1-beta.5; engine: 0.10.0.
Later UI changes and validation are documented in [the Arabic update report](UPDATE-REPORT-ar.md).

## Preserved Android features

- Picture frames, shadows, reflection, soft edges and SVG originals.
- Chart palettes, radar and stock chart tools, labels and chart styles.
- Shape presets and adjustments; legacy gradients and transparency are converted
  into the 0.10 fill model for rendering and DOCX export.
- Original/markup views, word highlighting during speech, mobile reflow.
- Text encoding selection and Android document reads.
- Arabic UI resources, RTL navigation and localized command search.

## Integration fixes

- Adapted shared drawing commands to the upstream paint, outline and tagging APIs.
- Connected the native Android image style commands rather than baking borders
  into image pixels; undo retains the original bitmap.
- Preserved SVG drawing in raster rendering and PDF export.
- Restored Android-only command registrations and removed a duplicate arrange command.
- Included original revision view in layout cache invalidation.
- Radar markers respect explicit per-series suppression.
- Kept forms-protection restrictions from the newer implementation.

## Validation

Passed checks (logs are in the workspace outputs directory):
- Core libraries and Android bridge: 1988 tests, all passed.
- PDF and raster rendering: 59 + 55 tests, all passed.
- Android compatibility round trips: 2 tests, all passed (one overlaps the core run).
- Regular-file read regression: 1 test, passed.
- Arabic resources: 4 tests; Android JVM tests: 12 tests, all passed.
- Universal debug APK: arm64-v8a and x86_64.
- Emulator visual checks: Arabic home, new document menu, sample document with
  table, live Arabic keyboard input, Arabic RTL/English LTR table settings.

ARM-only builds, including the previous 0.7-based APK, abort in this x86 emulator's
ARM translation layer while drawing. The native x86_64 build opens and edits the
same sample successfully. The delivered universal APK uses the emulator's native
ABI and retains arm64 for phones. Physical ARM hardware was not tested. The
experimental scalar-renderer workaround was reverted because it did not help.

The Android panic logger records future native failures in logcat. The renderer
keeps the original SIMD/performance settings.

The previous APK named
`WordCraft-Compose-Arabic-Compatible-Updates.apk` is the older partial-backport
build, not this upgraded engine. Do not confuse the two artifacts.

This document records the engine integration checks. Repository publication and later
UI releases are described in the Arabic update report; physical-phone testing is not
implied by APK delivery.
