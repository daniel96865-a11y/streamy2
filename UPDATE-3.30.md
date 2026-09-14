# Streamy 2 3.30: Update-Ablauf

Im bisherigen Ablauf gingen der vorgemerkte Update-Auftrag und die Download-ID
beim Neuerstellen der Activity verloren. Ein fertiger Systemdownload wurde nur
über einen kurzzeitig registrierten BroadcastReceiver erkannt. Der Status des
Systemdownloads blieb nach einem App-Neustart unberücksichtigt. Die Update-Suche
teilte außerdem ihre Warteschlange mit Playlist- und anderen Netzwerkanfragen.

## Änderungen

- Ein eigener Update-Bildschirm führt durch Installationsberechtigung, Download,
  APK-Prüfung und Android-Installer. Download-Fortschritt und Fehler bleiben sichtbar.
- URL, Versionsnummer, Download-ID und fertige Datei werden dauerhaft vorgemerkt.
  Die Rückkehr aus den Android-Einstellungen und das Wiederöffnen nach einem
  Prozessneustart setzen denselben Auftrag fort.
- Der Android-DownloadManager übernimmt den Download. Der Bildschirm fragt den
  gespeicherten Auftrag beim Wiederöffnen ab und benötigt keinen Abschluss-Broadcast.
  Fehlt der Systemdownload-Dienst, bleibt der begrenzte direkte Download verfügbar.
- Jeder Systemdownload erhält ein eigenes Ziel. Eine alte oder fehlgeschlagene APK
  wird nicht als Ersatz installiert. Vor der Übergabe werden ZIP-Struktur,
  Paketname und die angeforderte Versionsnummer geprüft.
- Die validierte APK wird aus dem privaten App-Verzeichnis über einen FileProvider
  mit Lesefreigabe und ClipData an den Installer übergeben. Die Übergabe hängt nicht
  von `queryIntentActivities()` und dessen Sichtbarkeitsfilter ab.
- Die Update-Suche verwendet eine eigene Warteschlange. Der Knopf heißt
  „Nach Updates suchen“; der aktuelle Stand und Verbindungsfehler bleiben sichtbar.
- Veraltete Installationsfehlermeldungen empfehlen keine Deinstallation mehr.
  Der alte Installations-Receiver ist nicht länger öffentlich aufrufbar.

Die Android-Dokumentation beschreibt die Prüfung einer Sonderberechtigung bei der
[Rückkehr in die App](https://developer.android.com/training/permissions/requesting-special)
und die [Übergabe an andere Apps ohne vorherige Paketabfrage](https://developer.android.com/training/package-visibility/use-cases).

## Prüfung

Release-Build erfolgreich. 38 Tests bestanden: die bisherigen 22 Prüfungen und acht
Update-Fälle jeweils unter Android 9 / API 28 und Android 14 / API 34. Die neuen Fälle
decken insbesondere Berechtigungsrückkehr nach Neuerstellung, Wiederaufnahme eines
laufenden oder bereits abgeschlossenen Downloads, lesbare Installer-URIs,
fehlgeschlagene Downloads und veraltete/fremde APKs ab.

Die APK hat Paketname `app.streamy2`, Versionscode 150, Versionsname 3.30 und
Mindestversion Android 9. Sie verwendet denselben privaten Release-Schlüssel und
dieselbe Signatur-Lineage wie die veröffentlichte 3.29. Signaturprüfung,
Offline-Upgrade-Prüfung gegen die veröffentlichte 3.29, ZIP-Prüfung und Alignment
sind erfolgreich. Die tatsächlich verpackten DEX-Dateien enthalten den neuen
Update-Ablauf und die umbenannte AndroidX-Berechtigung.

Datei: `Streamy2-3.30.apk` (67.248.351 Bytes)

SHA-256: `d81607b1d284104839ab8945e42b0f9bfbbb21afa5d669442827b80726d3069a`

Die Prüfungen ersetzen keinen vollständigen Installationstest auf einem realen
Android-Gerät oder Fire TV. Der Produktions-Updatefeed bleibt auf der veröffentlichten
3.29, bis eine geprüfte 3.30-APK als Release-Anhang verfügbar ist.
