# Streamy 2

Android / Android-TV Media-Client (Xtream, Vavoo, Megakino) — Version **3.36** (versionCode **156**).

Release APK is **ARM-only** (`armeabi-v7a` + `arm64-v8a`) for Fire TV Stick / phones / Android TV install reliability (no x86).

## Download

- Latest APK: [Streamy2-latest.apk](https://github.com/daniel96865-a11y/streamy2/releases/latest/download/Streamy2-latest.apk)
- Update feed: [docs/streamy2.json](docs/streamy2.json)

## Build

```bash
export JAVA_HOME=/workspace/jdk ANDROID_SDK_ROOT=/workspace/android-sdk
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## 3.28 notes

Fire-TV-Hang nach 3.26/3.27: der EPG-Fortschrittsbalken nutzt kein Android-`ProgressBar` mehr (animierter Refresh + Clip-Drawable hat die UI auf dem Stick eingefroren). Stattdessen eine leichte Zeichen-View; EPG-Updates nur für sichtbare Zeilen. Megakino hält beim Host-Lookup nicht mehr den UI-Lock.

## 3.27 notes

EPG „Jetzt“ nur für wirklich laufende Sendung (`start ≤ jetzt < ende`); Fortschrittsbalken nur im Slot. Bare XMLTV-Zeiten als Europe/Berlin; VIP-Playlist-Prefix wird beim Namensmatch entfernt.

## 3.26 notes

Live-TV / Vavoo: dünner Fortschrittsbalken unter der „Jetzt:“-Zeile zeigt den Anteil der laufenden EPG-Sendung (Start→Ende vs. jetzt). Versteckt ohne gültige Zeiten; Filme/Serien-Poster unverändert.

## 3.25 notes

Megakino-Folgenliste: keine doppelte Beschriftung mehr (`Folge 1 · Episode 1` → `Folge 1`). Detailtitel ohne Staffel-Suffix, Staffel in der Meta-Zeile, Player-Untertitel `Staffel 6 · Folge 1`.

## 3.24 notes

Megakino-Serien: Wiedergabe über aktuelle VOE-Spiegel (voe.sx ist DDoS-Guard). Serien in der Liste nach Titel A–Z gruppiert (eine Karte je Serie, Staffeln im Detail). Folgen numerisch sortiert.

## 3.23 notes

ANR-Fix: Nach dem 3.22 Cache-Fix liefen EPG-Lookups + Sibling-Unify (tausende `normName`-Regexes) auf dem UI-Thread — „App reagiert nicht“ auf Fire TV. Bulk/askEpg nutzen nur Exact-Match; Fuzzy nur im Player off-UI; Unify im IO-Thread.

## 3.22 notes

Bugfix: Live-TV from disk cache no longer marks every channel as a non-clickable header (and no longer duplicates rows). EPG name matching no longer steals parent guides (e.g. RTL Crime → RTL). XMLTV `</channel>` now clears the current id; extra EPG feeds merge programmes instead of replacing them. Vavoo/Gx/logo HTTP connections always disconnect.

## 3.21 notes

ARM-only release: native libs restricted to `armeabi-v7a` and `arm64-v8a` (excludes x86/x86_64). Smaller APK for Fire TV Stick; same app behavior on Fire TV, phones, and Android TV. No feature changes vs 3.20.

## 3.20 notes

Einstellungen → Darstellung: „Filme/Serien Spalten“ mit **1 / 2 / 4** Poster nebeneinander. Persistiert in Prefs; gilt für Filme, Serien und Megakino (inkl. Kategorien). Live-TV/Vavoo bleiben Liste. Raster bleibt per DPAD fokussierbar. Default: 2 Spalten.

## 3.19 notes

Cold start: main UI opens immediately. Large Xtream `CatalogCache` JSON is parsed off the main thread (show „Katalog lädt…“); WebView + AdBlock are created only when Browser is opened; libVLC is never probed in `Application.onCreate`. Vavoo/Kino/EPG hydrate after first paint. Keeps 3.18 EPG cache-first + 45min TTL.

## 3.18 notes

EPG UX: open Live-TV/Vavoo immediately (never freeze on download). Disk/memory cache shows „Jetzt: …“ instantly; full net refresh at most every 45 minutes (session/TTL) or cold start without valid cache. Subtle „EPG lädt…“ status while background refresh runs; fail soft keeps cache. Force refresh via settings still available. 3.16/3.17 unify + off-UI apply kept.

## 3.17 notes

Fixes crash/force-close when opening Vavoo after 3.16. Heavy EPG apply/unify runs off the UI thread; fuzzy name scan is last-resort only; askEpg uses cheap sibling unify. Live-TV name-unify + internet XMLTV kept.
