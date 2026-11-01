# Build repair for GitHub Actions run #27

## Fixed
The build log showed AndroidManifest.xml used invalid permission attributes:
- `android:permission="permission.VIBRATE"`
- `android:permission="permission.FOREGROUND_SERVICE"`

Permission declarations must use the `android:name` attribute and fully qualified Android permission names. They are now:
- `android.permission.VIBRATE`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `android.permission.WAKE_LOCK`
- `android.permission.READ_MEDIA_AUDIO`
- `android.permission.READ_EXTERNAL_STORAGE` (maxSdkVersion 32)
- `android.permission.POST_NOTIFICATIONS`

Also removed the obsolete `package` attribute from the source manifest. The Gradle namespace/applicationId remain `com.retropod.player`.

## Validation
- Manifest XML parsed successfully.
- All ZIP entries checked for duplicate paths.
- Archive integrity checked.
- Android Gradle compilation/device testing were not run in this environment. Run GitHub Actions `Build RetroPod APK` on this ZIP to confirm the next build stage.
