# RetroPod 2.6.0 — UI refresh

This update uses the user's supplied RetroPod concept image as the visual direction. It improves the real Compose screens; it is not a static mockup.

## Updated
- Home screen rebuilt as a compact dashboard: greeting, two-row category cards, recent-played horizontal artwork cards, and a larger album Cover Flow area.
- Album Cover Flow retains interactive horizontal paging, perspective rotation, scale/depth treatment, and artwork reflection in the album browser.
- Playlist screen rebuilt with consistent rounded AMOLED collection cards, artwork, song counts, play action, and compact edit/reorder/delete controls.
- Back navigation now closes the active player/settings/queue/sleep/menu/search layer first, returns library tabs to Home, and requires a second Back press within two seconds to exit from Home.
- Kept existing Media3 playback, MediaStore library, favorites, playlist storage, waveform seek, local lyrics import, sleep timer, and settings source.

## Not yet a complete match to the reference concept
The concept includes additional dedicated experiences that are not fully implemented by this update, including a live audio-reactive particle visualizer/spectrum, the full custom-band equalizer screen, a dedicated lyrics/queue tab design, and the exact final visual polish across every screen. This update should be treated as a real UI improvement, not a claim that every pixel and feature in the concept is complete.

## Verification
- ZIP integrity and XML parsing checked.
- Basic Kotlin source structural checks performed.
- Android Gradle compilation and physical-device QA were not run in this environment. Please run the repository's GitHub Actions build before installing.
