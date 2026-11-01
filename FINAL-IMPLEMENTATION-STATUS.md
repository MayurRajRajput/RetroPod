# RetroPod 2.5.0 — Consolidated final source package

This archive consolidates the staged RetroPod project and includes additional implementation for the two previously identified feature gaps.

## Added in this consolidation

### Sleep timer
- Added 15, 30, 45, 60, 90, and 120 minute choices in the Now Playing screen.
- Added cancel-timer action.
- Timer deadline is persisted in the playback service preferences.
- Service recreation checks the saved deadline and re-arms the timer or pauses playback if it has expired.
- Timer pauses playback; it does not stop or delete the queue.

### Local lyrics
- Added Android document picker to import a local `.lrc` or plain-text lyrics file for the currently playing track.
- Imported lyrics are saved per MediaStore track ID.
- LRC timestamps are parsed and the current line follows playback position.
- Plain text files display their opening lines.
- Lyrics preferences are included in the JSON backup/restore data.

### Consolidation
- Updated app version metadata to `2.5.0-final-source`.
- Updated the GitHub Actions APK artifact name.
- Preserved the previous staged source and documentation.

## Important limits

- This imports local LRC/text lyrics. It does not fetch lyrics online and does not extract embedded ID3 lyrics automatically.
- Sleep timer scheduling is service-owned and persisted as a deadline. It has not been tested under every OEM battery-management/process-kill condition.
- Existing stage limitations remain: some UI details and device-specific playback behavior need physical-device QA.
- This archive is source code, not an APK.

## Verification status

The archive is checked for ZIP integrity. Android XML resources and manifest are parsed. Kotlin source receives basic structural checks. This environment does not have Android SDK/Gradle, so Kotlin/Compose compilation, APK installation, and physical-device tests have not been performed.

## Build and test

Open the project in Android Studio with JDK 17 and Android SDK Platform 35, sync Gradle, then run `:app:assembleDebug`. Install the resulting debug APK and complete the checklist in `STAGE-9-FINAL-QA.md`.
