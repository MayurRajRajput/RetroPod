# RetroPod 2.2 — Android source project

This package is based on the uploaded `main` backup. It preserves the local MediaStore library, Media3 playback service, album art, Home/Songs/Albums/Playlists navigation, playlist persistence, song actions, player controls, and settings already present in that backup.

## Repair and update in this package
- Removed all 10 unresolved Git merge-conflict blocks from `MainActivity.kt` by reconciling the two sides into one source file.
- Kept the newer monochrome/AMOLED UI and the existing Home/library/player flows.
- Added drag-based A–Z navigation with a floating selected-letter indicator and animated section scrolling.
- Updated app version to 2.2.0.

## Important implementation status
This is **not a claim that every feature in the requested product specification is complete**. The source still needs full implementation/integration and device verification for queue editing/reordering, robust playlist rename/reorder, a fully functional persistent equalizer, sleep timer, lyrics provider/local lyrics UI, waveform-style seek control, persistent click-sound settings, and reliable playback restoration/notification behavior. The existing UI includes some related controls, but not all of these are complete.

## Build
Open this folder in Android Studio and let Gradle sync. The project targets Android SDK 35, min SDK 26, and Java 17. This environment did not have Gradle or the Android SDK installed, so a successful APK build and device test could not be performed here.
