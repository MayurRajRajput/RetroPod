# RetroPod source archive repair

## Repairs made
- Removed accidental duplicate paths suffixed with `(1)` from the uploaded archive.
- Kept one canonical `RetroPod-main` project tree.
- Added the missing `SettingsActionRow` composable referenced by the Settings backup/restore section. This addresses the two unresolved-reference errors shown in the supplied GitHub Actions log.

## Verification performed
- ZIP structure and integrity checked after packaging.
- Android XML resources and manifest parsed.
- Kotlin source checked for balanced braces and for a single `SettingsActionRow` definition with two call sites.

## Not verified
- No Gradle/Kotlin build was run in this environment.
- No APK installation or physical-device testing was performed.
- A successful GitHub Actions build is still required to confirm compiler and dependency compatibility.
