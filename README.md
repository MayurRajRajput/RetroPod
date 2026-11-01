# RetroPod 2.0 — Android local music player

RetroPod is a dark, iPod-inspired local music player built with Kotlin, Jetpack Compose and AndroidX Media3.

## Included in this project

- Local music library scanning through Android MediaStore (`READ_MEDIA_AUDIO` on Android 13+, legacy read permission on older Android versions).
- Songs, albums, playlists and Home destinations in an icon-only bottom navigation bar.
- Search icon that expands into a compact search field.
- A–Z fast-jump rail in the Songs screen.
- Album cover-flow style horizontal swipe navigation.
- Now Playing screen with swipeable queue, seek/progress control, shuffle, repeat and in-app playback volume.
- Media3 ExoPlayer and MediaSessionService for background playback and system media controls.
- Playlist creation, persistence on device, add-to-playlist actions, and playlist deletion.
- Track details and Android share sheet from the song actions menu.
- AMOLED-friendly dark theme, purple accent, animated transitions and optional haptic feedback.
- Haptics and animation preferences stored locally.
- Custom RetroPod launcher icon (adaptive and legacy density assets).
- GitHub Actions workflow that builds and uploads a debug APK.

## Build with GitHub Actions

1. Extract this ZIP.
2. Upload the **contents** of this folder to the root of your GitHub repository. Keep the `.github` folder.
3. Commit and push to the `main` branch, or open **Actions → Build RetroPod APK → Run workflow**.
4. Wait for the build job to finish.
5. Download the `RetroPod-debug-apk` artifact from the successful run.
6. Extract that artifact ZIP and install `app-debug.apk` on your Android phone.

The workflow installs Gradle 8.9 and uses Java 17. A Gradle wrapper is not required for this cloud workflow.

## Important testing notes

- This ZIP is a source project, not a prebuilt APK.
- The source was updated from the repository ZIP supplied in this conversation. The Android build could not be executed in this environment because an Android SDK/Gradle build environment was not available.
- Run the included GitHub Actions build before installing. If it reports a Kotlin or Android build error, share the failing log so the exact issue can be corrected.
- Test media permission, scanning, background playback, playlist persistence, share action, shuffle/repeat, and volume on the phone.
- The in-app volume control adjusts the ExoPlayer instance while the app's playback service is active. It is separate from the phone's hardware volume buttons.
- Haptic feedback depends on the phone's hardware and system vibration settings.
- Some audio files may not provide embedded album artwork; the included fallback artwork is used in that case.
- A debug APK is intended for testing. A Play Store release requires a separately configured signing key and release build.

## Project layout

- `app/src/main/java/com/retropod/player/` — Kotlin application and playback service.
- `app/src/main/res/` — launcher icons, theme resources and album fallback artwork.
- `app/build.gradle.kts` — Android app build configuration and dependencies.
- `.github/workflows/build-apk.yml` — cloud APK build and artifact upload.
