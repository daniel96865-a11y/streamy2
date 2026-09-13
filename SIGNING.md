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
- `STREAMY_PREVIOUS_APK`: tatsächlich veröffentlichte vorherige APK für die Upgrade-Prüfung.
- `STREAMY_KEY_ALIAS`: optional, Standard `streamy`.

```sh
python3 tools/sign-rotated-release.py \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  Streamy2-3.29-Android9plus.apk
```

Das Skript prüft die Mindestversion der APK, signiert ausschließlich mit dem neuen
Schlüssel und dem Android-v3-Signaturverfahren und prüft anschließend die Signatur.
Zusätzlich vergleicht es Paketname, Versionscode, Signatur-Abstammung und die
Berechtigungsnamen mit der vorherigen APK. Der alte Schlüssel behält ausschließlich
die für die Datenübernahme nötige Freigabe.
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

## Installationskorrektur nach dem ersten 3.29-Test

Der erste, noch nicht veröffentlichte 3.29-Kandidat wurde auf dem Handy abgelehnt.
Ein nachgewiesener Fehler war die erneut deklarierte AndroidX-Berechtigung
`app.streamy2.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: Android verlangt auch beim
Update derselben App die PERMISSION-Freigabe des alten Schlüssels, wenn sie denselben
Berechtigungsnamen wieder verwendet. Diese Freigabe wurde bewusst widerrufen.

Die Berechtigung erhält deshalb den Suffix `_2026`. Ein eng begrenzter ASM-Schritt
passt den dazugehörigen Namen in AndroidX ContextCompat an, damit die bestehende
Signaturprüfung für dynamische Receiver auch auf Android 9–12 weiter funktioniert.
Die Prüfung selbst wird nicht entfernt. Ändert AndroidX die erwartete Konstante,
bricht der Build ab. Der neue private Schlüssel und die restriktive Lineage bleiben
unverändert; Datenübernahme erfordert keine Deinstallation.

`tools/VerifyUpgrade.java` erkennt den Fehler anhand der tatsächlichen signierten
APKs und Manifest-Dateien und ist Teil des Signierskripts. Ein erfolgreicher
Offline-Vergleich ersetzt weiterhin keinen Installationstest auf dem Gerät.

Androids Prüfung: https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/pm/InstallPackageHelper.java
