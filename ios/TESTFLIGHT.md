# Streamy iOS – TestFlight

Der iOS-Port baut bereits im GitHub-CI als Simulator-App. Für eine installierbare iPhone/iPad-Version über TestFlight benötigt GitHub zusätzlich die privaten Apple-Signierdaten.

## App-Daten

- Bundle-ID: `de.dgstudios.streamyios`
- Anzeigename: `Streamy`
- Ziel: iPhone + iPad
- Mindestversion: iOS 16

## Benötigte GitHub Actions Secrets

Im Repository unter **Settings → Secrets and variables → Actions**:

- `IOS_TEAM_ID`
- `IOS_DISTRIBUTION_CERT_BASE64` – Apple-Distribution-Zertifikat als Base64-kodierte .p12-Datei
- `IOS_DISTRIBUTION_CERT_PASSWORD`
- `IOS_PROVISION_PROFILE_BASE64` – App-Store-Provisioning-Profil für `de.dgstudios.streamyios`
- `ASC_KEY_ID`
- `ASC_ISSUER_ID`
- `ASC_PRIVATE_KEY_BASE64` – App-Store-Connect-API-Key (.p8) als Base64

Keine dieser privaten Dateien oder Passwörter in Git committen.

## Veröffentlichung

Sobald die Secrets vorhanden sind, in GitHub Actions **Publish Streamy iOS to TestFlight** manuell starten. Der Workflow erzeugt das Xcode-Projekt, signiert das Archiv, exportiert die IPA und lädt sie in App Store Connect/TestFlight hoch.
