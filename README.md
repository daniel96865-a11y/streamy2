# Streamy 2

Moderner Android-IPTV-Client mit Xtream, EPG, Live-TV, Filmen, Serien, Vavoo und Megakino.

## 📥 Downloads

### 📺 Streamy 2 TV — Android TV / Fire TV

**[Streamy2-TV-3.38.apk herunterladen](https://github.com/daniel96865-a11y/streamy2/releases/download/v3.38/Streamy2-TV-3.38.apk)**

Paket-ID: `app.streamy2`

### 📱 Streamy 2 Mobile — Smartphone / Tablet

**[Streamy2-Mobile-3.38.apk herunterladen](https://github.com/daniel96865-a11y/streamy2/releases/download/v3.38/Streamy2-Mobile-3.38.apk)**

Paket-ID: `app.streamy2.mobile`

**[➡️ GitHub-Release Streamy 2 3.38 öffnen](https://github.com/daniel96865-a11y/streamy2/releases/tag/v3.38)**

> Wichtig: Die neue 3.38-Linie verwendet eine neue Streamy-2-Release-Signatur. Eine ältere TV-Installation mit einer anderen Signatur muss vor der ersten Installation der neuen TV-Linie deinstalliert werden. Streamy 2 Mobile ist ab 3.38 eine eigenständige App und kann separat installiert werden.

## App-Linien ab 3.38

Streamy 2 wird aus demselben Quellcode als zwei unabhängige APKs gebaut:

- **Streamy 2 TV** — `app.streamy2` — für Android TV und Fire TV
- **Streamy 2 Mobile** — `app.streamy2.mobile` — für Android-Smartphones und Tablets

Beide Varianten sind ARM-only (`armeabi-v7a` + `arm64-v8a`).

## Update-Kanäle

Die neue Signatur-Linie ist vollständig getrennt:

- Neue TV-Linie ab 3.38: `docs/streamy2-tv.json` / `docs/streamy2-tv.txt`
- Mobile-Linie ab 3.38: `docs/streamy2-mobile.json` / `docs/streamy2-mobile.txt`
- Alte TV-Linie: `docs/streamy2.json` / `docs/streamy2.txt` bleibt auf der letzten kompatiblen alten Signatur-Version und wird nicht mit der neuen Linie vermischt.

Damit kann ein Mobile-Update nicht versehentlich an TV-Geräte verteilt werden und eine alte TV-Installation bekommt kein inkompatibel signiertes Update angeboten.

## Build

```bash
export JAVA_HOME=/workspace/jdk ANDROID_SDK_ROOT=/workspace/android-sdk
./gradlew :app:assembleTvRelease
./gradlew :app:assembleMobileRelease
```

Ausgaben:

- TV: `app/build/outputs/apk/tv/release/`
- Mobile: `app/build/outputs/apk/mobile/release/`

## Signierung

TV und Mobile der neuen Linie müssen ab 3.38 mit demselben privaten **Streamy 2 Release**-Schlüssel signiert werden. Der private Keystore wird absichtlich nicht im öffentlichen Repository gespeichert.

Die veröffentlichten APKs müssen vor dem Release mit Android `apksigner` geprüft werden. Für Android 7+ wird mindestens APK Signature Scheme v2 verwendet; die aktuelle 3.38-Release-Linie ist mit v2/v3 signiert.

## Version 3.38

- TV und Mobile als getrennte App-Pakete
- eigener Update-Kanal je Geräteklasse
- neuer, einheitlicher Release-Signierschlüssel für die neue Linie
- Android-Signatur v2/v3
- klar getrennte GitHub-Downloads für TV und Mobile
