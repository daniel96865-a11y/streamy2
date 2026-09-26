<p align="center">
  <img src="assets/streamy2-icon.png" alt="Streamy 2 Logo" width="160">
</p>

<h1 align="center">Streamy 2</h1>

<p align="center">
  IPTV-Player für Android TV, Fire TV, Smartphone und Tablet – für deine eigenen Playlists.
</p>

<p align="center">
  <a href="https://github.com/daniel96865-a11y/streamy2/releases/tag/v3.80"><img alt="Version" src="https://img.shields.io/badge/Version-3.80-2684ff?style=for-the-badge"></a>
  <img alt="Build" src="https://img.shields.io/badge/Build-200-111827?style=for-the-badge">
  <img alt="Android" src="https://img.shields.io/badge/Android-7%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white">
</p>

<p align="center">
  <img src="assets/streamy2-banner.png" alt="Streamy 2 – TV und Mobile" width="100%">
</p>

## 📥 Download – Version 3.80

| Gerät | Version | Download |
|---|---:|---|
| 📺 **Streamy 2 TV** — Android TV / Fire TV | 3.80 (Build 200) | **[TV-APK herunterladen](https://github.com/daniel96865-a11y/streamy2/releases/download/v3.80/Streamy2-TV-3.80.apk)** |
| 📱 **Streamy 2 Mobile** — Smartphone / Tablet | 3.80 (Build 200) | **[Mobile-APK herunterladen](https://github.com/daniel96865-a11y/streamy2/releases/download/v3.80/Streamy2-Mobile-3.80.apk)** |

<p align="center">
  <a href="https://github.com/daniel96865-a11y/streamy2/releases/latest"><b>➡️ Neueste Release-Seite öffnen</b></a>
</p>

Bereits installierte Apps finden neue Versionen selbst: **Einstellungen → Nach Updates suchen**.

## 🔥 Installation auf Fire TV / Android TV

1. Auf dem Fire TV die App **Downloader** installieren (Amazon Appstore).
2. **Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft installieren** → für *Downloader* erlauben.
   (Falls die Entwickleroptionen fehlen: *Einstellungen → Mein Fire TV → Info* öffnen und 7× auf den Gerätenamen klicken.)
3. In Downloader diese Adresse eingeben:
   `https://github.com/daniel96865-a11y/streamy2/releases/download/v3.80/Streamy2-TV-3.80.apk`
4. **Installieren** wählen, danach kann die APK-Datei gelöscht werden.

Auf Android-TV-Geräten funktioniert es genauso, z. B. mit Downloader oder einem Dateimanager.
Auf dem Handy die Mobile-APK im Browser öffnen und die Installation aus dieser Quelle erlauben.

## ✨ Funktionen

- 📡 **Eigene Playlists** — Zugang per Server-Adresse, Benutzername und Passwort (Xtream-kompatibel)
- 🗂️ **Mehrere Wiedergabelisten** — beliebig viele Playlists speichern, aktive auswählen, umbenennen, einzeln löschen und per „Playlist aktualisieren“ jederzeit frisch vom Server laden; eigener Senderlisten-Cache je Playlist
- 📺 **Live-TV** — Senderlisten mit Kategorien, Sortierung, Suche und Direktwahl per Sendernummer auf der Fernbedienung
- 🗓️ **EPG / Programmführer** — „Jetzt“ und „Danach“ direkt in der Senderliste, Fortschrittsbalken, einstellbares Aktualisierungsintervall (6/12/24 Std.), optional eigene XMLTV-Adresse
- ⏪ **Zurückblicken** — Sendungen nachholen und darin zurück-/vorspulen, sofern der Anbieter der Playlist das unterstützt
- 🎬 **Filme & Serien** — Übersicht mit Postern, Staffeln und Episoden, Suche und wählbarer Spaltenanzahl
- 🔍 **Suche** — über Sender, Filme und Serien; beim Wechsel von Bereich oder Kategorie oder mit Zurück wird sie beendet und die Tastatur ausgeblendet
- ▶️ **Zwei Player** — eigener Player (ExoPlayer/Media3) und VLC; Auto wählt passend, getrennt einstellbar
- 🔊 **Wiedergabe-Optionen** — Audiospur, Untertitel, Geschwindigkeit, Bildformat (Anpassen/Füllen/Strecken), Puffergröße, Surround oder Stereo, Sleep-Timer, Nur-Audio
- 📶 **Puffer-Anzeige** — zeigt im Player Vorlauf in Sekunden, Datenrate und Nachlade-Zähler, damit Ruckler nachvollziehbar werden; einblendbar mit der Bedienleiste oder dauerhaft
- 👆 **Wischen zum Umschalten** — auf dem Handy im Live-TV nach oben/unten wischen, um den Sender zu wechseln (abschaltbar)
- 🎮 **Für die Fernbedienung gemacht** — komplette Bedienung mit D-Pad, gut sichtbare Auswahl, alle Einstellungen erreichbar
- 🔗 **Kopplung per PIN** — Playlist bequem vom Handy an den Fernseher senden (gleiches WLAN)
- 🎨 **Darstellung** — mehrere Akzentfarben, die die ganze Oberfläche einfärben, dunkles Design
- 🩺 **Diagnose** — Wiedergabe-Diagnose und Player-Test direkt in den Einstellungen
- 🔄 **In-App-Update** — TV und Mobile haben getrennte Update-Kanäle und prüfen selbst auf neue Versionen

> Streamy 2 enthält keine Inhalte und keine Sender. Es spielt nur Playlists ab, die du selbst einträgst.

## 📱 Zwei App-Varianten

| Variante | Paket-ID | Geräte |
|---|---|---|
| **Streamy 2 TV** | `app.streamy2` | Android TV / Fire TV |
| **Streamy 2 Mobile** | `app.streamy2.mobile` | Smartphone / Tablet |

Beide Varianten werden aus demselben Quellcode gebaut, laufen ab Android 7 und unterstützen ARM-Geräte (`armeabi-v7a` + `arm64-v8a`).
Sie können parallel installiert werden und erhalten ihre Updates unabhängig voneinander.

## 🆕 Letzte Änderungen

**3.80** – Neue Schaltfläche „Playlist aktualisieren“ über der Senderliste, in den Einstellungen und in der Playlist-Verwaltung: lädt die aktive Playlist frisch vom Server, zeigt das Ergebnis (Anzahl Sender oder Fehlermeldung) und aktualisiert die Liste sofort.

**3.79** – Suche: Beim Wechsel von Bereich oder Kategorie wird die Suche beendet und wieder der normale Inhalt gezeigt; die Tastatur bleibt danach geschlossen. Zurück schließt zuerst die Suche, Treffer öffnen sich mit ausgeblendeter Tastatur.

**3.78** – Handy: Im Live-TV-Player per Wischen nach oben/unten den Sender wechseln, mit kurzer Einblendung von Nummer und Name; abschaltbar unter Wiedergabe.

**3.77** – Neues Einstellungssymbol; die Akzentfarbe färbt jetzt alle bisher blauen Elemente (Fokusrahmen, Optionen, Einstellungen, Dialoge, Zeitleiste, EPG-Balken); App-Symbol und TV-Banner zeigen das Logo aus der App.

**3.76** – Puffer-Anzeige im Player: vorgeladene Sekunden, Datenrate, Stream-Bitrate und Nachlade-Zähler; geladener Bereich in der Zeitleiste. Einstellbar unter Wiedergabe (Mit Bedienleiste / Immer / Aus).

Alle Versionen: [Releases](https://github.com/daniel96865-a11y/streamy2/releases)

<details>
<summary><b>🛠️ Selbst bauen</b></summary>

```bash
./gradlew :app:assembleTvRelease
./gradlew :app:assembleMobileRelease
```

Ausgaben:

- TV: `app/build/outputs/apk/tv/release/`
- Mobile: `app/build/outputs/apk/mobile/release/`

Veröffentlichte APKs sind mit dem Streamy-2-Release-Schlüssel signiert (APK Signature Scheme v2/v3). Updates lassen sich nur über eine bestehende Installation mit gleicher Signatur einspielen.

</details>
