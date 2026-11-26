# EarLink Lab

Android-only direct Bluetooth audio routing experiment. This is not a universal Bluetooth broadcaster. It plays an identical quiet test tone through separate AudioTrack instances using preferred output requests, then records actual routes and manual listening results. Android may ignore requests or expose only one Bluetooth media output. No root, Shizuku, system-wide capture, or Auracast broadcasting is implemented yet. No internet or microphone permissions.

## Build and install

Requirements: Android Studio, JDK 17, Android SDK 35 and Gradle 8.7.

```sh
gradle :app:assembleDebug :app:lintDebug
```

Alternatively, open [GitHub Actions](https://github.com/aksharghatole/EarLink/actions) → Build EarLink APK → successful run → EarLink-debug artifact. Extract and install the APK.

## Device test

1. Keep media volume low and pair two headsets through Android settings.
2. Open EarLink, grant Nearby Devices permission, and refresh.
3. If fewer than two media outputs are exposed, copy the report; the app cannot force a second output.
4. Select both outputs and start the 15-second test. Have a listener at each headset. Audio may fall back to the phone speaker.
5. Record whether both are audible and copy diagnostics. Routing IDs do not prove physical playback or synchronization.
6. Repeat with three headsets only if two work.

## Next experiments

Privileged combined audio routing with Shizuku, manufacturer-supported Auracast controls, and physical latency measurement. System-wide Spotify and YouTube duplication is outside v0.1.

## References

- [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)
- [Combined audio routing](https://source.android.com/docs/core/audio/combined-audio-routing)
