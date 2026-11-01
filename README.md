<<<<<<< HEAD
# RetroPod 2.1 — Modern Monochrome

This project is based on the newly supplied RetroPod-main project. Existing MediaStore scanning, album artwork extraction, Compose player UI and Media3 playback service are retained.

## Changes in this source update
- Pure black AMOLED background and restrained silver/white controls (purple/blue tint removed).
- Search field is hidden until the search icon is tapped.
- Settings entry point in the app header.
- Song rows no longer show track numbering and include compact artwork.
- Reduced player artwork height and removed the promotional footer / swipe prompt.
- Updated app version to 2.1.0.
- Existing Cover Flow pager remains dot-free; no page-indicator dots are added.
- Media3 playback service retains last-track/position checkpoint and paused restoration behavior.

## Build
GitHub Actions uses Java 17 and Gradle 8.9. Push the extracted project to your repository and check the Actions run for `:app:assembleDebug`.

## Verification
The ZIP archive is integrity-checked. This environment cannot run an Android SDK/Gradle build or device tests. This update is a source/UI refinement, not a claim that every requested advanced feature (persistent playlist editor, equalizer DSP, lyrics provider, sleep timer, animated waveform, gesture-driven A–Z index and Cover Flow click effects) has been implemented. Verify the build and app behavior on-device before replacing your current install.
=======
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
>>>>>>> parent of 906647e... Last Sync: 2026-11-01 07:56 (Mobile)
