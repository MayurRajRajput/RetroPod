# RetroPod 2.0 — AMOLED Edition

RetroPod is an Android local music player built with Kotlin, Jetpack Compose and AndroidX Media3.

## Included
- Existing Media3 ExoPlayer + MediaSession background playback architecture
- Local device music scanning and embedded/fallback album artwork
- Home, Songs, Albums and Playlists destinations
- Persistent user playlists and add-to-playlist actions
- Song details and Android share intent
- Full-screen settings destination (not a settings pop-up)
- Pure-black AMOLED palette with neutral monochrome accent
- Compact bottom icon navigation and mini-player
- Animated player controls, seek progress and album swipe pager
- In-app playback volume control
- Haptic and animation preferences
- Custom launcher icon resources
- GitHub Actions workflow to build and upload a debug APK

## Build from GitHub
1. Extract the ZIP.
2. Upload the contents of this folder to the root of your repository (keep `.github`).
3. Commit and push to `main`, or run **Build RetroPod APK** from Actions.
4. Download the `RetroPod-2.0-debug-apk` artifact and extract `app-debug.apk`.

## Build environment
The workflow uses Java 17 and Gradle 8.9. It runs `:app:assembleDebug`.

## Important verification note
This project ZIP has been packaged and its archive integrity checked. A complete Android Gradle build and on-device test have not been run in this environment. Treat the GitHub Actions result as the build verification step. Test playback, library permissions, playlists, sharing, haptics, volume, and background playback on the phone before replacing your existing installed build.

The debug APK is intended for direct testing. A signed release build is needed for Play Store distribution.
