# Signierung nach dem Audit von 3.28

Der bisher im Repository enthaltene private Debug-Schlüssel gilt als kompromittiert.
Das Entfernen aus dem aktuellen Git-Stand widerruft weder Kopien noch frühere APKs.
Er darf nicht als regulärer Release-Schlüssel weiterbenutzt werden. Die Git-Historie
wurde nicht umgeschrieben.

Ein neuer privater RSA-3072-Schlüssel und eine Lineage vom bisherigen Schlüssel sind
vorbereitet und separat privat gesichert. Sie gehören niemals in dieses Repository.
Die Lineage erlaubt die Übernahme bestehender App-Daten, jedoch keinen Rückwechsel
zum alten Schlüssel und keine Berechtigungen für Apps mit der alten Signatur.

## Vorschau und gewöhnlicher Build

`./gradlew :app:assembleDebug :app:testDebugUnitTest` baut eine separat installierbare
Vorschau mit Paketname `app.streamy2.preview`. Die normale Installation und ihre Daten
bleiben bestehen. Beide Geräte brauchen die neue Version für die verschlüsselte Kopplung.
Die Vorschau verwendet einen lokalen Debug-Schlüssel und lädt keine Produktionsupdates.

`./gradlew :app:assembleRelease` erstellt ohne Signierumgebung eine **unsignierte** APK.
Mit `STREAMY_KEYSTORE`, `STREAMY_STORE_PASSWORD`, `STREAMY_KEY_PASSWORD` und optional
`STREAMY_KEY_ALIAS` (Standard `streamy`) wird mit dem privaten Schlüssel signiert.
Dieser normale Build eignet sich für Neuinstallationen; für das Upgrade einer alten
Installation ist zusätzlich die Lineage nötig.

## Sicheres Upgrade ab Android 9 / API 28

Zunächst ohne Signierumgebung bauen:

```sh
./gradlew -PstreamyMinSdk=28 :app:assembleRelease
```

Die folgenden Umgebungsvariablen privat setzen:

- `ANDROID_BUILD_TOOLS`: Verzeichnis der Android Build Tools, z. B. SDK/build-tools/34.0.0.
- `STREAMY_KEYSTORE`: absoluter Pfad des neuen Keystores.
- `STREAMY_STORE_PASSWORD`, `STREAMY_KEY_PASSWORD`: dessen Passwörter.
- `STREAMY_LINEAGE`: absoluter Pfad der vorbereiteten Lineage.
- `STREAMY_KEY_ALIAS`: optional, Standard `streamy`.

```sh
python3 tools/sign-rotated-release.py \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  Streamy2-3.29-Android9plus.apk
```

Das Skript prüft die Mindestversion der APK, signiert ausschließlich mit dem neuen
Schlüssel und dem Android-v3-Signaturverfahren und prüft anschließend die Signatur.
Es lädt keine Datei hoch und veröffentlicht kein Release. Vor einer Veröffentlichung
muss ein Upgrade von der tatsächlich veröffentlichten 3.28 auf einem Gerät getestet
werden; die Prüfung mit `apksigner` ersetzt diesen Gerätetest nicht.

## Android 7/8 / API 24–27

Diese Android-Versionen unterstützen keine Signaturrotation. Für den sicheren
Schlüsselwechsel ist dort eine Neuinstallation erforderlich. Die Nutzer müssen
vorher ihre Playlist-Zugangsdaten sichern; die Deinstallation löscht lokale Daten.
Der reguläre Build mit Mindestversion 24 bleibt für solche Neuinstallationen möglich.
Eine mit dem alten Schlüssel signierte Kompatibilitäts-APK würde das Sicherheitsproblem
auf diesen Geräten bestehen lassen und wird deshalb nicht automatisch erzeugt.

Android-Dokumentation: https://developer.android.com/tools/apksigner
