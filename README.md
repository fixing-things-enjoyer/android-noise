# 40 Hz Gamma Clicks

Android app that generates a 1 ms rectangular click every 25 ms (40 Hz), matching the auditory timing described by Chan et al. It uses a foreground media-playback service and deliberately never requests audio focus, allowing Android to mix it with music, podcasts, and other media.

Playback can run indefinitely or stop automatically after a duration set in hours, minutes, and seconds. Playback can always be stopped immediately from the app or notification.

## Build

```sh
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Important limits

- The paper used calibrated equipment at 68 dB for patients. A phone volume percentage cannot reproduce a specified sound-pressure level.
- The reported intervention combined synchronized light and sound. This app provides sound only.
- The study was a small feasibility/pilot study, not proof that this app prevents or treats Alzheimer's disease.
- Some vendor-specific Android versions or apps using exclusive audio output may behave differently.

Source: Chan D, et al. (2022), [Gamma frequency sensory stimulation in mild probable Alzheimer's dementia patients](https://doi.org/10.1371/journal.pone.0278412), PLOS ONE 17(12): e0278412.
