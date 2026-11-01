# RetroPod — Step 6: Audio Features

## Implemented in this stage
- Added a real Android `android.media.audiofx.Equalizer` bound to the active ExoPlayer audio session.
- Added a Settings audio section that lists the device equalizer presets and applies the selected preset to playback.
- Persists the selected preset index and releases the effect when the playback service is destroyed.
- Preserves all files from the previous staged source archive.

## Still outstanding
- Sleep timer UI and lifecycle-safe timer behavior.
- Embedded/local LRC lyrics loading and display.
- Custom equalizer bands and device-specific effect testing.

## Verification
The archive is checked for ZIP integrity and XML well-formedness. A Gradle/Kotlin build and device test were not possible in this environment. Android audio-effect support varies by device; the app reports when no active audio session is available.
