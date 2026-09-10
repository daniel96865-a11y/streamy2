# Streamy 2

Android / Android-TV Media-Client (Xtream, Vavoo, Megakino) — Version **3.17** (versionCode **137**).

## Download

- Latest APK: [Streamy2-latest.apk](https://github.com/daniel96865-a11y/streamy2/releases/latest/download/Streamy2-latest.apk)
- Update feed: [docs/streamy2.json](docs/streamy2.json)

## Build

```bash
export JAVA_HOME=/workspace/jdk ANDROID_SDK_ROOT=/workspace/android-sdk
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## 3.17 notes

Fixes crash/force-close when opening Vavoo after 3.16. Heavy EPG apply/unify runs off the UI thread; fuzzy name scan is last-resort only; askEpg uses cheap sibling unify. Live-TV name-unify + internet XMLTV kept.
