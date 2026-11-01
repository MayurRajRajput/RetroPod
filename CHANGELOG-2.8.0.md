# RetroPod 2.8.0

## Now Playing (new design, matches the concept image)
- Centered "Now Playing" title, back chevron, equalizer shortcut and a menu (favorite, queue, lyrics, visualizer, sleep timer, volume, output device, more actions).
- Large rounded artwork with a thin border, centered title and artist.
- Thin seek bar with a soft animated glow over the played part, plain white pause bars, and flowing wave lines along the bottom.
- **Swipe down** closes Now Playing (the screen follows your finger). **Swipe up** opens the Queue.
- Swipe the artwork: right = next song, left = previous (reversible in Settings > Experience).
- On the Queue screen, swipe down on the header to return.

## Mini player everywhere
- Now also shown on Settings, Playlist detail and Sleep Timer (it was already on Lyrics/Queue, Visualizer and Equalizer).
- Swipe right = next song, left = previous. Swipe up opens Now Playing.

## Keyboard fix
- The app is now edge-to-edge on every Android version and the keyboard is handled in exactly one place.
  Previously Search added its own keyboard padding on top of the bottom bar's, which pushed the field around and made the keyboard
  look like it covered the screen (worst on Android 15).
- The mini player and tab bar hide while the keyboard is open, so nothing sits between your text and the keyboard.

## Cover Flow
- Real click sound (SoundPool, not the system touch-sound setting) for every wheel step and cover swipe, plus haptics.
  "Interface click sounds" is now on by default.
- Playlists get Cover Flow + click wheel too (Cover Flow | List switch), shared code with Albums.

## Lock screen player (Settings > Lock Screen)
- With the option on, pressing the power button while music plays shows the album art, title, progress and controls above the lock screen.
- Swipe up (or tap "Unlock") to continue to the normal unlock. Swipe the art to change songs.
- Android only allows this with "Display over other apps"; Settings walks you to the permission screen and shows its status.
- The normal lock-screen media card from the notification keeps working either way.

## Other
- Version 2.8.0 (code 11).
