# RetroPod — complete Android music player project

RetroPod is an iPod-inspired local music player starter for Android. It includes:
- Album library grouped from music on the device
- Song list and search across song, artist and album names
- Embedded album artwork extraction from audio files
- Four original fallback album-art images included in the project
- Animated player transitions and playback controls
- Previous / play-pause / next and seek controls
- AndroidX Media3 ExoPlayer + MediaSessionService for background playback and system media controls
- GitHub Actions cloud build workflow

## Build the APK from a phone using GitHub
1. Extract this ZIP.
2. Upload the **contents** of the `RetroPod-Everything` folder to the root of your GitHub repository. Do not upload the ZIP itself.
3. Keep the `.github` folder; it contains the APK build workflow.
4. Open the repository's Actions tab.
5. Choose **Build RetroPod APK** and tap **Run workflow** (or push to `main` to trigger it).
6. When the run succeeds, download the `RetroPod-debug-apk` artifact.
7. Extract the artifact ZIP and install `app-debug.apk`.

## Gradle setup
The GitHub Actions workflow installs Gradle 8.9 using `gradle/actions/setup-gradle`, then runs `gradle :app:assembleDebug`. This project therefore does not require `gradlew` or `gradle/wrapper/gradle-wrapper.jar` for the cloud build. The root and app Gradle configuration files are included.

## Notes
- Requires Java 17 and Android SDK platform 35 in the GitHub runner; GitHub's hosted Ubuntu runner provides the Android SDK.
- The app requests audio library permission when first opened.
- Background playback uses an Android media session. System notification/lock-screen behavior depends on Android's media controls and notification settings.
- This is a complete starter project, but it has not been compiled on a real Android device in this environment. Run the GitHub Actions build and share any error log if it fails; compatibility issues may need a small correction.
- A debug APK is for direct testing. A Play Store release requires a signed release build.
