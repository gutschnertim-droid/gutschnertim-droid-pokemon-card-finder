# Pokémon Card Finder – lokaler Android-Live-Test

Diese kleine native Test-App fragt genau **ein Migros-Produkt** (Pokémon Booster Blister, ID `746658000000`) in **einer Filiale** (Bern Marktgasse) direkt auf dem Android-Gerät ab. Die Anfrage und die letzte Anzeige laufen lokal; die App benötigt weder GitHub-Feed noch eigenen Server oder Migros-Login.

## APK herunterladen oder selbst bauen

Der Repository-Workflow baut nach Änderungen am Ordner `android-local/` eine Debug-APK. Das ist nur der Paketbau: **Bestandsanfragen und Ergebnisanzeige laufen auf dem Handy direkt bei Migros.** Die APK kann im erfolgreichen Actions-Lauf als `pokemon-card-finder-android-test` heruntergeladen werden; das Artefakt bleibt 14 Tage verfügbar. Alternativ benötigt ein lokaler Bau Android Studio (JDK 17, Android SDK 35): Ordner in Android Studio öffnen und **Build → Build APK(s)** wählen. Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Auf dem Android-Gerät muss die Installation von APK-Dateien erlaubt werden.

## Test

App öffnen und **Migros jetzt abfragen** antippen. Die App lädt Migros direkt, öffnet die Verfügbarkeitsabfrage, sucht Bern Marktgasse und zeigt den gemeldeten Status samt Prüfzeit. Bei einer Migros-Wartungs- oder Sperrseite wird das als Fehler angezeigt; die App erfindet dann keinen Bestand.

Dies ist ein Machbarkeitstest und noch nicht die vollständige Filial-App. Nach erfolgreichem Test kann die Abfrage auf die weiteren Produkte und Filialen ausgebaut werden. Android kann Hintergrundaufgaben verzögern; die Schaltfläche startet die Abfrage verlässlich auf Wunsch.
