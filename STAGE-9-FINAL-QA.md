# RetroPod — Step 9: Final integration and quality review

This is the cumulative project archive after the eight planned development stages. Step 9 performs a source/configuration audit and adds a repeatable verification checklist. It does **not** claim a successful Android build or device QA.

## Integration checks completed in this environment

- Confirmed the cumulative Android project source is present.
- Parsed all Android XML resources and the app manifest.
- Checked the four Kotlin source files for balanced brace counts.
- Confirmed the app namespace/application ID remains `com.retropod.player`.
- Confirmed SDK configuration remains compile SDK 35, min SDK 26, Java 17.
- Updated version metadata to `2.4.8-stage9` and versionCode 7.
- Updated the GitHub Actions APK artifact name to match.
- Checked archive integrity after packaging.

## Build verification still required

This environment does not contain Android SDK/Gradle tooling, and this source archive does not include a Gradle wrapper. Therefore the following have **not** been verified here:

- Kotlin/Compose compilation and dependency resolution.
- APK installation or launch.
- Playback, notification, lock-screen, and process-death restoration on a physical device.
- Equalizer behavior across audio devices.
- Sleep timer and lyrics behavior.
- Backup export/import round-trip.
- Accessibility, rotation, permission-denial, and Android-version compatibility.

## Build in Android Studio

1. Open the `RetroPod-main` project directory.
2. Install/use JDK 17 and Android SDK Platform 35.
3. Sync the Gradle project and resolve any plugin/dependency issues.
4. Run `:app:assembleDebug`.
5. Install the generated `app/build/outputs/apk/debug/app-debug.apk` on a test device.

## Final device QA checklist

- [ ] Fresh install and first-run audio permission flow.
- [ ] Scan local audio; handle empty library and denied permissions.
- [ ] Play, pause, seek, skip, shuffle, repeat, and adjust in-app volume.
- [ ] Verify album artwork, Cover Flow gestures, and screen navigation.
- [ ] Search by title, artist, and album; test A–Z fast scroll.
- [ ] Create, rename, reorder, and delete playlists; add/remove tracks.
- [ ] Reorder/remove queue items without losing the current track.
- [ ] Verify favorites and recently played persistence after app restart.
- [ ] Verify equalizer on supported and unsupported devices.
- [ ] Verify sleep timer and local/embedded lyrics, if implemented.
- [ ] Lock screen, background playback, notification actions, and dismiss from Recents.
- [ ] Force-stop/recreate process and confirm paused queue/position restoration.
- [ ] Export backup, reinstall/clear app data, restore backup, and verify data.
- [ ] Test Android 8 through current supported Android versions where practical.

## Known incomplete or unverified items

Earlier stage notes identify unfinished items, including local/embedded lyrics, complete sleep-timer lifecycle behavior, and comprehensive device testing. A UI control or stage note is not proof of a complete implementation. Review `STAGE-6-AUDIO.md` and `STAGE-7-BACKGROUND-PLAYBACK.md` alongside actual device tests before calling this a finished release.

**Release status: source package assembled; build and physical-device verification pending.**


## Follow-up consolidation
The consolidated `2.5.0-final-source` package adds local LRC/plain-text lyric import and a persisted service-owned sleep timer. These additions have not been Gradle-built or device-tested. See `FINAL-IMPLEMENTATION-STATUS.md`.
