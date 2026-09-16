# Streamy 2

Native Android IPTV client with Xtream support, EPG, Live TV, movies, series, Vavoo, Megakino and TV/remote-control support.

## App lines from 3.38

Streamy 2 is split into two independent APK lines while sharing the same source code:

- **Streamy 2 TV** — package `app.streamy2` — Android TV / Fire TV. This keeps the existing package ID so installed TV versions can continue to receive in-place updates.
- **Streamy 2 Mobile** — package `app.streamy2.mobile` — Android phones/tablets. This is a separate installation and has its own update channel.

Both variants are ARM-only (`armeabi-v7a` + `arm64-v8a`).

## Build

```bash
export JAVA_HOME=/workspace/jdk ANDROID_SDK_ROOT=/workspace/android-sdk
./gradlew :app:assembleTvRelease
./gradlew :app:assembleMobileRelease
```

Expected outputs:

- TV: `app/build/outputs/apk/tv/release/`
- Mobile: `app/build/outputs/apk/mobile/release/`

## Update channels

- TV: `docs/streamy2.json` / `docs/streamy2.txt`
- Mobile: `docs/streamy2-mobile.json` / `docs/streamy2-mobile.txt`

A release must not be advertised in either production feed until the exact APK has been published and its signing certificate has been checked against the installed app line.

## Signing

The production TV line must use the private **Streamy 2** signing key and the existing Android 9+ signing lineage. A CI/debug-signed APK must never replace `Streamy2-latest.apk` in the production TV feed.

The private keystore and lineage are intentionally not stored in Git. See `SIGNING.md` and `tools/sign-rotated-release.py`.

## 3.38

- separates TV and Mobile into independent application IDs
- gives each app its own update channel
- keeps `app.streamy2` for TV upgrade compatibility
- prevents a Mobile release from being offered to TV devices and vice versa
- production TV feed remains on the last compatible signed release until a correctly signed 3.38 TV APK is published

## Current update safety status

The previously published 3.37 APK used a different signing certificate from the established production line. The production feeds were therefore rolled back to the last compatible signed release rather than continuing to offer an APK that Android cannot install as an in-place update.
