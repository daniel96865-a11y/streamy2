# Streamy für iPhone und iPad

Dies ist der iOS-Port von Streamy 2. Die Android-App bleibt unverändert im bestehenden Android-Projekt.

## Bereits im iOS-Port angelegt

- Xtream Codes und M3U-Wiedergabelisten
- mehrere Wiedergabelisten, Auswahl und Löschen
- Zugangsdaten im iOS-Keychain
- Live-TV mit Kategorien, Suche, Senderlogos und EPG
- XMLTV-EPG und manuelle Aktualisierung
- Catch-up/Timeshift für Xtream-Sender, wenn der Anbieter es meldet
- Filme und Serien inklusive Kategorien, Suche, Beschreibungen und Episoden
- Hybrid-Player: AVPlayer plus VLCKit-Fallback
- Audio-Spurauswahl für AVPlayer
- Bildmodi Anpassen, Zoom und Strecken
- Picture-in-Picture/AirPlay über AVPlayerViewController
- generischer Browser mit WKWebView
- Design-/Player-/EPG-Einstellungen
- lokale Geräte-Synchronisierung über verschlüsseltes MultipeerConnectivity
- iPhone- und iPad-Unterstützung, Hoch- und Querformat

## Unterschied zu Android

Eine APK kann unter iOS nicht installiert werden. Die Verteilung erfolgt zunächst über TestFlight. Für TestFlight werden ein Apple-Developer-Team, App-ID und Signierdaten benötigt. Der Quellcode kann ohne diese Daten bereits gebaut und im Simulator getestet werden.

iOS lässt Hintergrundjobs nicht in frei wählbaren festen Intervallen laufen. EPG wird beim App-Start/manuell aktualisiert; ein späterer BackgroundTasks-Refresh kann zusätzlich vom System eingeplant werden.

## Projekt erzeugen

Das Projekt wird mit XcodeGen erzeugt:

```bash
cd ios
xcodegen generate
open StreamyIOS.xcodeproj
```

Danach in Xcode das eigene Apple-Developer-Team auswählen und auf einem iPhone/iPad oder über TestFlight bauen.
