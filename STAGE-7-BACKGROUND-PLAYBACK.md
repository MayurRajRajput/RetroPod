# RetroPod — Step 7: Background Playback and Restoration

## Implemented in this stage
- Playback remains owned by `MediaSessionService`, not the Activity, so closing the player screen or Activity does not directly own/stop the ExoPlayer instance.
- Explicitly sets `android:stopWithTask="false"` on the playback service.
- Keeps the media service alive when the app task is dismissed while audio is actively playing.
- Saves the current queue (content URIs and track metadata), current index, and playback position to private app preferences.
- Checkpoints playback state every second and on Media3 player events/service shutdown.
- On service recreation, restores the saved queue and position, prepares the player, and leaves it paused. It does not autoplay after process death.
- On Activity reconnection, rebuilds the visible queue from the MediaSession, including a queue restored by the service.
- Keeps the Media3 MediaSessionService notification/lock-screen integration already provided by the existing service architecture.
- Retains all previous stage source changes.

## Important limitations
- Notification/lock-screen controls depend on Android's MediaSession notification behavior and device policy; they have not been exercised on a physical device here.
- Saved MediaStore content URIs remain usable only while the app retains the relevant media permission and the underlying track remains available. Removed/moved files may not restore.
- Android may stop background work under manufacturer-specific battery policies. This implementation does not bypass user or OS restrictions.

## Verification
- Source and manifest were inspected; XML well-formedness and ZIP integrity were checked.
- No Gradle/Kotlin build, emulator test, or physical-device test was possible in this environment. Compilation and runtime background behavior remain unverified.
