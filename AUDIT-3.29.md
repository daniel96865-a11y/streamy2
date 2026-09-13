# Streamy 2 – Korrekturen für 3.29

Ausgangsstand: `1c0d3e199a499c2eeef68c7cf5379be75fadbc34`, Release `v3.28`.
Die Korrekturen wurden am 13.09.2026 im separaten Zweig vorbereitet.

| Befund aus 3.28 | Änderung für 3.29 |
| --- | --- |
| Eine fehlgeschlagene Xtream-Kategorie verwirft Filme und Serien; Thread-Pool bleibt offen | Vier Antworten unabhängig auswerten, erfolgreiche Inhalte übernehmen, Teilfehler anzeigen, Pool schließen; neue Listen auf dem UI-Thread veröffentlichen |
| Zukünftige Sendung wird im Player als aktuell angezeigt | Gemeinsame Zeitprüfung: Start ≤ jetzt < Ende; kein Zukunfts-Fallback |
| Alter Sender hinterlässt EPG beim Zappen | Programmliste und aktuelle Sendung beim Wechsel leeren; alte Antworten verwerfen |
| EPG-Intervall wird durch feste 45 Minuten und Größenprüfungen übergangen | Gemeinsamer Aktualisierungsdienst, 6/12/24 Stunden pro Quelle, persistenter Cache, Android-Job; Neulesen gespeicherter Daten alle 30 Minuten bei geöffneter App |
| VLC-Pause löst Hängerbehandlung aus | Bewusste Pause und Hintergrundzustand vom Watchdog ausschließen; explizites Pause/Fortsetzen |
| Exo startet nach Rückkehr nicht; VLC läuft im Hintergrund | Beide Engines pausieren; Exo behält Position und Medien; beim Fortsetzen bestehende Ansicht korrekt anbinden |
| Verspätete Vavoo-/VLC-Antwort überschreibt neuen Sender | Generation der Wiedergabe prüfen, alte Initialisierung freigeben, verzögerte Rückrufe begrenzen |
| Fehlgeschlagener Update-Download meldet keinen Fehler | Ausnahme weitergeben, HTTP-/ZIP-/Größenprüfung, begrenzte Weiterleitungen, Teil-Dateien und Verbindungen bereinigen |
| Gültiges kleines XMLTV wird abgewiesen | Starre 100-KiB-Untergrenze entfernen; XML-Inhalt vor Ersetzen des Caches prüfen; kleine XML- und GZIP-Dateien zulassen |
| Globale und lokale TLS-Prüfungen deaktiviert | Android-/OkHttp-Standardprüfung für Zertifikate und Hostnamen wiederherstellen |
| PIN und Playlist-Zugangsdaten offen im LAN | Discovery ohne PIN; J-PAKE mit Schlüsselbestätigung und HKDF, AES-GCM; 2 Minuten, 5 Versuche, eine erfolgreiche Übertragung |
| Öffentlicher privater APK-Schlüssel | Aus Git-Stand entfernen, externe Release-Signierung, separater privater neuer Schlüssel und geprüfte Lineage; Signierskript und Migrationshinweise in SIGNING.md |

## Nachweise

- `:app:testDebugUnitTest`: **20 Tests, 0 Fehler**, einschließlich echter Loopback-TCP-/UDP-Verbindungen für Kopplung, ungültiger PINs, manipulierter verschlüsselter Daten, Wiederverwendung in einer anderen Sitzung, selbstsigniertem TLS-Zertifikat, API-Teilfehlern, EPG-/APK-Downloads und Player-Lebenszyklus.
- Exo-Lebenszyklus mit echter ExoPlayer-Instanz, bereits angebundener PlayerView und erhaltener Position von 42 Sekunden geprüft. VLC-Zustandslogik mit einer Engine-Testinstanz geprüft.
- `:app:assembleDebug` und `:app:assembleRelease` erfolgreich. Zusätzlicher Release-Build mit `-PstreamyMinSdk=28` erfolgreich.
- Vorschau-Paket: `app.streamy2.preview`, Version `3.29-preview` / 149, Mindestversion Android 7 / API 24; APK-Signatur mit `apksigner` geprüft.
- Separater Release-Kandidat: `app.streamy2`, Version 3.29 / 149, Mindestversion Android 9 / API 28, neuer RSA-3072-Schlüssel, v3-Signatur und Lineage mit `apksigner` geprüft.
- Das Zertifikat der tatsächlich veröffentlichten 3.28 stimmt mit dem Vorgänger der Lineage überein.
- Alte Zertifikats-SHA-256: `edf9f9a9e7a435f53dc02a7b3185ce5e17c14223a917929ec284aac7b012ed0e`.
- Neue Zertifikats-SHA-256: `9d7f061b552e04c4e257f59df209c0860d5fc095aa1fc25e782480a5b46761ce`.

## Noch auf Geräten prüfen

Echte Live-Streams, native VLC-Codecs, Fernbedienung, WLAN-Broadcast-Discovery,
Androids Hintergrundplanung und ein Upgrade von 3.28 benötigen einen Handy-/TV-Test.
Die automatisierten Tests ersetzen diese Hardwareprüfung nicht. Die Kopplung benötigt
auf beiden Geräten mindestens 3.29.

Die sichere Signaturrotation ist für Android 9+ vorbereitet. Android 7/8 benötigt für
den neuen Schlüssel eine Neuinstallation nach Sicherung der Playlist-Zugangsdaten.
Ein bereits öffentlich gewordener Schlüssel lässt sich nicht rückwirkend widerrufen.
Details und Build-Befehle: [SIGNING.md](SIGNING.md).

## Installationsfehler des ersten 3.29-Kandidaten

Der erste Gerätetest meldete eine abgelehnte Installation. Die nachfolgende
Offline-Prüfung reproduzierte einen konkreten Update-Blocker:
`INSTALL_FAILED_DUPLICATE_PERMISSION` für die erneut deklarierte interne
AndroidX-Receiver-Berechtigung. Die eingeschränkte Signatur-Lineage erlaubt
Datenübernahme, entzieht dem alten Schlüssel aber bewusst Berechtigungszugriff.
Android prüft diese Freigabe auch beim Aktualisieren derselben App.

Die Berechtigung wird nun im Manifest und in AndroidX ContextCompat gemeinsam
umbenannt. Die Signaturprüfung der Receiver bleibt erhalten, ebenso die restriktive
Lineage und der neue private Schlüssel. Zwei zusätzliche Tests prüfen die
Receiver-Registrierung mit der neuen Berechtigung und die Ablehnung ohne Berechtigung
auf Android 9. Der Signiervorgang prüft jetzt zusätzlich die wirklichen APKs auf
Versions-, Signatur- und Berechtigungs-Kompatibilität.

Der bereits hochgeladene erste APK-Kandidat muss nach erneutem Gerätetest durch die
korrigierte APK ersetzt werden. Die öffentliche Update-Info bleibt bis dahin auf 3.28.
