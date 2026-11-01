# RetroPod 2.1 — Modern Monochrome

This project is based on the newly supplied RetroPod-main project. Existing MediaStore scanning, album artwork extraction, Compose player UI and Media3 playback service are retained.

## Changes in this source update
- Pure black AMOLED background and restrained silver/white controls (purple/blue tint removed).
- Search field is hidden until the search icon is tapped.
- Settings entry point in the app header.
- Song rows no longer show track numbering and include compact artwork.
- Reduced player artwork height and removed the promotional footer / swipe prompt.
- Updated app version to 2.1.0.
- Existing Cover Flow pager remains dot-free; no page-indicator dots are added.
- Media3 playback service retains last-track/position checkpoint and paused restoration behavior.

## Build
GitHub Actions uses Java 17 and Gradle 8.9. Push the extracted project to your repository and check the Actions run for `:app:assembleDebug`.

## Verification
The ZIP archive is integrity-checked. This environment cannot run an Android SDK/Gradle build or device tests. This update is a source/UI refinement, not a claim that every requested advanced feature (persistent playlist editor, equalizer DSP, lyrics provider, sleep timer, animated waveform, gesture-driven A–Z index and Cover Flow click effects) has been implemented. Verify the build and app behavior on-device before replacing your current install.
