# Streamy 2

Android / Android-TV Media-Client (Xtream, Vavoo, Megakino) — Version **3.15** (versionCode **135**).

## Download

- Latest APK: [Streamy2-latest.apk](https://github.com/daniel96865-a11y/streamy2/releases/latest/download/Streamy2-latest.apk)
- Update feed: [docs/streamy2.json](docs/streamy2.json)

## Build

```bash
export JAVA_HOME=/workspace/jdk ANDROID_SDK_ROOT=/workspace/android-sdk
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## 3.15 notes

Low-RAM hardening for weaker Android TV sticks (largeHeap, smaller Exo buffers, EPG/WebView trim, lazy VLC probe). Weak sticks can still OOM under extreme load — prefer Exo + low buffer on bedroom sticks.
