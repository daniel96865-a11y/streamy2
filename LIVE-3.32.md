# Streamy 2 — 3.32 (152)

## Recovery build

This build restores the playback implementation from 3.30. It removes the 3.31 asynchronous VLC command/release lifecycle, position-based live watchdog, and the associated resolver, prefetch, retry and buffering changes. No new playback optimization is introduced.

The 3.31 watchdog was reproduced scheduling recovery after 12 seconds with `playing=true` and a constant zero time position. That signal does not establish a stalled video: live inputs may not provide a usable advancing playback clock. The restored watchdog avoids this new trigger. A native crash cause has not been established.

All 129 files under `app/src/main` are byte-for-byte identical to the 3.30 source snapshot at `fb5e170c708c55f4d461d8d9336f65cca63f57b1`. The application version is raised to 3.32 (152), allowing an update from either 3.30 or 3.31 with the existing package, signing key and rotation lineage. The update and security fixes present in 3.30 remain included.

## Validation

- 40 automated tests passed with no failures, errors or skipped tests.
- The added regression case observes 60 seconds of a playing live input without clock progress on Robolectric Android 9 and 14, and asserts that no recovery or engine switch occurs.
- The final build used an isolated source snapshot with input-hash verification. APK integrity, alignment, package/version/minSdk, removed 3.31 playback classes, unchanged native libraries and the renamed receiver permission were checked.
- Offline upgrade preflight passed against both the published 3.30 and 3.31 APKs.
- No physical-device playback, native decoder stability or switching-speed measurements have been made. This restores the prior behavior; it does not claim to resolve the earlier slow channel changes.

## APK

- File: `Streamy2-3.32.apk`
- Android 9 or newer; versionCode 152.
- Size: 67248351 bytes.
- SHA-256: `4f2ff8ac584181e88aaff49a83d9b020b827900d023399fda9e86189bfaac79a`.

The 3.31 update advertisement was suspended. Keep both update feeds on 3.30 while this recovery APK is evaluated. Before any activation, publish the exact signed APK as `v3.32/Streamy2-3.32.apk` and verify its public download.
