# Dauerhafte Android-APK-Signierung

## Status

Die App besitzt nun ein vorbereitetes, dauerhaftes PKCS12-Signaturschlüsselpaar. Der private Schlüssel gehört ausschliesslich dem Entwickler und **darf niemals ins öffentliche Repository**. Der Fingerabdruck des öffentlichen Zertifikats ist fest im Build-Workflow hinterlegt:

```
4F:AA:C0:EA:65:3F:83:E6:DE:EA:D3:65:B9:EB:BD:2B:BD:D0:87:41:B1:AA:49:24:95:52:D1:12:BC:39:86:47
```

Schlüsselalias: `pcf-update`; Dateityp: `PKCS12`; GitHub-Actions-Workflow: `.github/workflows/build-android-test.yml`.

## Einmalige Einrichtung der beiden GitHub-Repository-Secrets

1. Die drei **privat** ausgehändigten Dateien sicher aufbewahren: `pokemon-card-finder-signing-key.p12` (unersetzliches Backup), `pokemon-card-finder-ANDROID_KEYSTORE_BASE64.txt` und `pokemon-card-finder-ANDROID_KEYSTORE_PASSWORD.txt`. Keinesfalls committen oder veröffentlichen.
2. Auf GitHub im Repository `gutschnertim-droid/gutschnertim-droid-pokemon-card-finder` zu **Settings → Secrets and variables → Actions → New repository secret**.
3. Secret `ANDROID_KEYSTORE_BASE64` erstellen; als **Value** den gesamten Inhalt der `...BASE64.txt` einfügen (eine lange Zeile).
4. Secret `ANDROID_KEYSTORE_PASSWORD` erstellen; als **Value** den Inhalt der `...PASSWORD.txt` einfügen.
5. Unter **Actions → Build local Android live test → Run workflow** einmal manuell bauen. Erst der *erfolgreiche* Run veröffentlicht die neue Release-APK. GitHub überprüft den Zertifikatsfingerabdruck sowohl am Keystore als auch an der resultierenden APK.

**Alternative auf einem eigenen Rechner (GitHub CLI und Java JDK installiert):**

```bash
gh auth login
bash scripts/configure_android_signing.sh /PFAD/pokemon-card-finder-signing-key.p12 /PFAD/pokemon-card-finder-ANDROID_KEYSTORE_PASSWORD.txt
```

Das Skript kontrolliert den Zertifikatsfingerabdruck und lädt die Secrets direkt über die GitHub CLI hoch, ohne die Schlüsselwerte zu protokollieren.

## Wichtige Einschränkung beim ersten Wechsel

Die bisherigen APKs bis einschliesslich v1.2.61 wurden im GitHub-Runner als Debug-Builds mit **temporärem Signaturschlüssel** gebaut. Deren privaten Debug-Schlüssel besitzen wir nicht. Deshalb kann Android die alte App nicht in-place zu einer mit dem neuen Schlüssel signierten App aktualisieren. **Beim ersten Wechsel ist eine Neuinstallation nötig. Dabei können lokal gespeicherte Daten verloren gehen.** Die alte App nicht löschen, solange daraus noch Daten gebraucht werden.

Ab der ersten erfolgreich mit diesem dauerhaften Zertifikat signierten Version können spätere Builds mit demselben `applicationId`, einem höheren `versionCode` und demselben Schlüssel regulär als Update installiert werden. Sie dürfen die App dann bei einem Update nicht vorher deinstallieren, sonst gehen die App-Daten weiterhin verloren.

Der neue GitHub-Workflow **verweigert einen APK-Build**, wenn Secrets fehlen, Zertifikat oder APK nicht dem festgelegten Fingerabdruck entsprechen oder die Signaturprüfung fehlschlägt. Die bisherige veröffentlichte APK bleibt online, bis ein vollständig geprüfter neuer Build veröffentlicht wird.

## Schlüssel-Backup

Mindestens zwei getrennte sichere Backups von `.p12` und Passwort anlegen, etwa in einem vertrauenswürdigen Passwortmanager und einem verschlüsselten Backup. **Verlust des privaten Schlüssels oder des Passworts verhindert künftig Updates derselben installierten Anwendung.** Ein neuer Schlüssel wäre ein neuer einmaliger Installationsbruch.

Die Signierung ermöglicht reguläre Updates, behebt aber keine Einschränkungen der Hintergrundabfrage oder der Coop-API.
