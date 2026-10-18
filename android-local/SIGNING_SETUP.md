# Dauerhafte Android-APK-Signierung

## Status

Für die App wurde ein neues, dauerhaftes PKCS12-Signaturschlüsselpaar eingerichtet. Der private Schlüssel gehört ausschliesslich dem Entwickler und **darf niemals ins öffentliche Repository**. Der Fingerabdruck des öffentlichen Zertifikats ist fest im Build-Workflow hinterlegt:

```
48:29:26:07:1D:A9:D7:A1:A0:73:64:E2:1E:52:F5:F6:30:51:51:67:98:80:E0:67:E4:63:6B:DA:28:E4:AC:C4
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

Die bisher veröffentlichten APKs bis einschliesslich v1.2.61 wurden mit einem anderen Signaturschlüssel gebaut. Android kann sie daher nicht direkt mit dieser dauerhaft signierten Version aktualisieren. **Für den ersten Wechsel ist eine Neuinstallation nötig; dabei können bisher lokal gespeicherte Daten verloren gehen.**

Ab der ersten erfolgreich mit diesem Zertifikat signierten APK können spätere Versionen mit demselben `applicationId`, einem höheren `versionCode` und demselben Schlüssel regulär als Update installiert werden. Dann bleiben die App-Daten erhalten, solange die App nicht vorher deinstalliert wird.

Der neue GitHub-Workflow **verweigert einen APK-Build**, wenn Secrets fehlen, Zertifikat oder APK nicht dem festgelegten Fingerabdruck entsprechen oder die Signaturprüfung fehlschlägt. Die bisherige veröffentlichte APK bleibt online, bis ein vollständig geprüfter neuer Build veröffentlicht wird.

## Schlüssel-Backup

Mindestens zwei getrennte sichere Backups von `.p12` und Passwort anlegen, etwa in einem vertrauenswürdigen Passwortmanager und einem verschlüsselten Backup. **Verlust des privaten Schlüssels oder des Passworts verhindert künftig Updates derselben installierten Anwendung.** Ein neuer Schlüssel wäre ein neuer einmaliger Installationsbruch.

Die Signierung ermöglicht reguläre Updates, behebt aber keine Einschränkungen der Hintergrundabfrage oder der Coop-API.
