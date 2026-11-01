# RetroPod source update 2.3 — implementation status

This archive is based on the user-provided `RetroPod-main (3).zip` (GitHub `main`). It contains real Kotlin source edits, not a design-only handoff.

## Changes made in this update
- Songs in the Songs library now open their action menu on long-press; the always-visible three-dot action button has been removed from each song row.
- The album carousel page counter has been removed.
- The Now Playing cover pager now applies perspective rotation and scale to adjacent covers for a more dimensional Cover Flow feel.
- Added an Up Next dialog to Now Playing. It lists the active queue and lets the user jump to a queued track.
- Refined the monochrome neutral text palette.

## Existing functionality retained from the supplied project
- Local MediaStore audio-library scan and permission flow.
- Media3 playback service and media session.
- Home, Songs, Albums, and Playlists navigation.
- Search field opened from a search icon.
- Basic persistent playlist storage, create, delete, and add-to-playlist actions.
- A–Z song index with drag-to-jump and floating letter indicator.
- Now Playing controls, seek slider, in-app player volume, shuffle, and repeat.
- Full-screen Settings destination with haptic and animation toggles and library rescan.
- Track details and share action.

## Not yet implemented or not verified — do not treat this as the final complete app
- Favorites collection and persistence.
- Playlist rename, reorder, and robust song removal management.
- Queue reorder and remove controls (queue is view/jump only in this update).
- Actual Android Equalizer / audio-effect integration and preset persistence.
- Sleep timer.
- Embedded/local lyrics reader.
- Persistent playback checkpoint/restore after process death and explicit paused restoration.
- Lock-screen/notification behavior and lifecycle verification on a physical device.
- Configurable click sounds and full settings coverage (appearance, audio, backup/restore, about details).
- Final premium Cover Flow polish, album reflections, custom launcher branding, and the animated waveform-style seek UI.
- Full test/build verification.

## Build verification
The editing environment used to prepare this source archive did not have the Android SDK or Gradle installed. No APK build or device test is claimed. Import the project into Android Studio and run the Gradle build before installing it.
