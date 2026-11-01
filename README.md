# RetroPod 2.0

Android local music player built with Kotlin, Jetpack Compose and AndroidX Media3.

## Playback lifecycle
- Playback is owned by a `MediaSessionService`, not by the Activity.
- Dismissing the app UI / locking the phone does not intentionally stop active playback.
- The active song and playback position are checkpointed while playing and when the player state changes.
- If Android later destroys the playback service, the next app launch restores the last selected track at its saved position in a paused state. It does not unexpectedly auto-play.
- Pausing from the notification keeps the last track available for the next launch.

## Build
The GitHub Actions workflow uses Java 17 and Gradle 8.9 and runs `:app:assembleDebug`.
Push to `main` or manually run the workflow, then download the `RetroPod-debug-apk` artifact.

## Important scope and verification
This ZIP is based on the supplied `RetroPod-master.zip`. It includes the playback-state persistence change and retains the existing app source and playback architecture. The supplied source did not contain a Cover Flow page-indicator dot row, so no such indicator was added. This ZIP does not magically include the full visual redesign or every optional feature discussed in design mockups (for example lyrics, sleep timer, and a full equalizer); those require additional implementation.

The archive is integrity-checked, but an Android Gradle build and on-device test have not been run in this environment. Confirm that GitHub Actions succeeds, then test library permissions, playback, notification controls, screen locking, dismissing the app from recents, and reopening after service/process recreation before treating this as a verified release.
