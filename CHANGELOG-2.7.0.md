# RetroPod 2.7.0 - full redesign to the concept image

Rebuilt the Compose UI screen by screen from the 12-screen concept, and fixed notification behaviour.
The code is now split into focused files instead of one 1,700-line `MainActivity.kt`.

## Notification / background fixes
- **Tapping the media notification (or lock-screen card) now opens RetroPod on Now Playing.**
  The MediaSession has a session activity; `MainActivity` is `singleTop` and handles `onNewIntent`.
- Android 13+ notification permission is now actually requested (it was declared but never asked for,
  so the notification could be hidden). It is requested together with audio access.
- Album art is attached to the notification/lock screen metadata.
- Wake lock for reliable background playback; shuffle/repeat are restored with the queue.
- Queue is saved only when it changes (previously the whole queue was rewritten every second).

## Screens (matching the concept)
| Concept screen | Implementation |
|---|---|
| Splash | Animated wave lines, drawn iPod logo, "Music, Timelessly.", progress bar |
| Home | Time-based greeting + your name, Songs/Albums/Playlists/Favorites cards with live counts, Recently Played (See All), Made for You mix |
| Songs | Filter chips (All / Favorites / Downloaded / Recent), hearts, A-Z index with bubble, sort menu, long-press menu |
| Now Playing | Swipeable art, favorite, animated wave seek bar, glowing morphing play/pause, shuffle/repeat(all/one), EQ/volume/timer/queue row, reactive wave |
| Cover Flow | 3D cover flow with reflection, click-wheel dial (haptic + optional click sound), transport buttons, page dots; Albums grid toggle |
| Visualizer / Spectrum | Particle-wave and bar spectrum driven by the live audio output |
| Playlists | Art rows, play button, options menu (shuffle, rename, reorder, delete), detail screen |
| Context menu | Play Next, Add to Queue, Add to Playlist, Favorites, Song Details, Share, Delete from Device |
| Equalizer | On/off, Custom/Bass/Pop/Rock/Vocal presets, vertical band sliders (real Android equalizer) |
| Lyrics / Queue | Synced LRC lyrics with auto-scroll and tap-to-seek, plain text lyrics, queue with reorder/remove |
| Sleep Timer | Countdown ring, 15/30/45/60/90/Custom, Start/Cancel, pauses playback automatically |
| Settings | Appearance, Playback, Audio, Library, Experience, Backup & Restore, About |

## Other improvements
- Search tab, favorites, persisted recently played, accent colour choices, editable name.
- Library: hide short tracks, exclude folders, rescan.
- Playback: skip silence, pause on headphone unplug, resume last session toggle.
- Backup/restore now refreshes the running app instead of asking for a restart.
- Delete from Device uses Android's system confirmation.
- Artwork is cached (smooth scrolling), and downscaled before display.

## Known limits (please read)
- **Not compiled or tested here.** There was no Android SDK/Gradle in the build sandbox. Run the GitHub Action
  (or `:app:assembleDebug` in Android Studio) and send me any compiler errors; they will be quick fixes.
- Equalizer band count comes from the phone (commonly 5, not always 7). Labels show the real frequencies.
- Live visualizer needs Android's audio-capture permission; without it a simulated animation is shown.
- Crossfade is not implemented (Media3 has no built-in crossfade). Gapless depends on the file format.
- "Downloaded" means songs inside a folder named Download.
- The cast icon opens the system output/cast settings; it is not a built-in Chromecast sender.
