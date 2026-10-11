# Pokémon Card Finder – lokaler Android-Live-Test

Diese native Test-App fragt die **sieben Pokémon-TCG-Artikel im vorhandenen Migros-Katalog** in allen Filialen aus `app/src/main/assets/branches.json` direkt auf dem Android-Gerät ab. Die aktuelle Filialliste umfasst 37 Fribourger und Berner Standorte. Damit umfasst ein vollständiger Lauf bis zu 259 Artikel-Filial-Kombinationen. Anfrage und letzte Anzeige laufen lokal; die App benötigt weder GitHub-Feed noch eigenen Server oder Migros-Login. Der Katalog ist der erfasste Stand für PLZ 1700 und keine schweizweite Sortimentserhebung.

## APK herunterladen oder selbst bauen

Der Repository-Workflow baut nach Änderungen am Ordner `android-local/` eine Debug-APK. Das ist nur der Paketbau: **Bestandsanfragen und Ergebnisanzeige laufen auf dem Handy direkt bei Migros.** Die APK kann im erfolgreichen Actions-Lauf als `pokemon-card-finder-android-test` heruntergeladen werden; das Artefakt bleibt 14 Tage verfügbar. Alternativ benötigt ein lokaler Bau Android Studio (JDK 17, Android SDK 35): Ordner in Android Studio öffnen und **Build → Build APK(s)** wählen. Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Auf dem Android-Gerät muss die Installation von APK-Dateien erlaubt werden.

## Test

App öffnen, gewünschten Artikel im Auswahlfeld wählen und **Alle Artikel in allen Filialen abfragen** antippen. Die App fragt alle sieben Produktseiten nacheinander ab und sucht die Filialen jeweils im Verfügbarkeitsdialog. Erfasste Ergebnisse werden lokal gespeichert und erscheinen fortlaufend in der Liste. Unlesbare oder fehlende Antworten stehen ausdrücklich als „Nicht eindeutig geprüft“ und werden nicht als „Nicht auf Lager“ ausgegeben. Bei einer Migros-Wartungs- oder Sperrseite wird der Lauf gestoppt; bereits erfasste Ergebnisse bleiben lokal gespeichert.

Die Produktbilder werden von der Migros-Bildquelle geladen. Android kann Hintergrundaufgaben verzögern; die Schaltfläche startet die Abfrage verlässlich auf Wunsch. Bei 259 Kombinationen kann ein Lauf länger dauern. Die App sollte dabei geöffnet bleiben.
