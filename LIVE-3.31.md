# Streamy 2 — 3.31 (151)

## Live playback changes

- Reuse the prepared VLC player and video view when zapping with VLC selected. Native stop/play/pause/seek operations run serially away from the UI thread. Superseded channel requests and late native callbacks are rejected.
- Observe playback position rather than trusting only the playing flag. Live startup has a 12-second allowance; stalled progress is detected after 8 seconds. Pause/background transitions reset monitoring. Sustained progress restores the retry budget.
- Limit live recovery to three attempts. Try alternate stream URLs and allow one engine fallback in Auto mode; explicitly selected engines remain selected. Film/catch-up retry policy and end-of-film behavior remain separate from live recovery.
- Restore Vavoo address prefetch after channel changes, consume fresh different replacement URLs once, and request a fresh address when no usable replacement exists. Prefer the last working resolver host, leave time for bounded fallback requests, and reject results from canceled/obsolete requests.
- Apply Vavoo's saved player choice on every channel change.
- VLC respects the buffer setting (normal remains 2200 ms on phone / 2800 ms on TV). Exo live startup thresholds are 1000 ms for normal and 600 ms for low; normal buffer capacity is preserved.

## Validation

72 automated tests passed with zero failures, errors or skipped tests. Playback tests run on Robolectric Android 9 and Android 14, including blocked native stop, rapid channel replacement, obsolete events, pause/resume/seek, film completion, Vavoo preference/prefetch/recovery, and bounded live retries. Native calls are represented by a controllable test backend; these tests do not measure real-device decoding or provider performance.

The final build ran from an isolated source snapshot. All Java/Gradle/XML input hashes were checked against the repository before signing. APK ZIP integrity, alignment, packaged playback classes, unchanged native libraries and the renamed AndroidX receiver permission were checked. Offline upgrade preflight against the published 3.30 APK passed (package, increasing version, signatures, rotation lineage and signature-permission ownership).

Real-device channel switching and provider/network behavior still require validation.

## Distribution

- File: `Streamy2-3.31.apk`
- Android 9 or newer; versionCode 151.
- Size: 67694815 bytes.
- SHA-256: `07dd3f30e8afe02244147c1b3b5ef5a77c00353e7306b1543782542f190c093f`.
- Signed using the existing release key and rotation lineage. No signing material is in this repository.
- Keep update feeds on 3.30 until this exact APK is published as `v3.31/Streamy2-3.31.apk` and its public download is verified.
