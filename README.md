# Streamy 2

Android / Android-TV Media-Client (Xtream, Vavoo, Megakino) — Version **3.16** (versionCode **136**).

## Download

- Latest APK: [Streamy2-latest.apk](https://github.com/daniel96865-a11y/streamy2/releases/latest/download/Streamy2-latest.apk)
- Update feed: [docs/streamy2.json](docs/streamy2.json)

## Build

```bash
export JAVA_HOME=/workspace/jdk ANDROID_SDK_ROOT=/workspace/android-sdk
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## 3.16 notes

Live-TV uses internet XMLTV (epg.pw) like Vavoo. HD/FHD/UHD name variants share one EPG via normalized-name unify — playlist `epgChannelId` no longer splits siblings; Xtream shortEpg only when no XMLTV name match.
