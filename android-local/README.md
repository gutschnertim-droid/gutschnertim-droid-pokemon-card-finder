# Pokémon Card Finder – lokaler Android-Live-Test

Diese kleine native Test-App fragt genau **ein Migros-Produkt** (Pokémon Booster Blister, ID `746658000000`) in allen Filialen aus `app/src/main/assets/branches.json` direkt auf dem Android-Gerät ab. Die aktuelle Filialliste umfasst 37 Fribourger und Berner Standorte. Die Anfrage und die letzte Anzeige laufen lokal; die App benötigt weder GitHub-Feed noch eigenen Server oder Migros-Login.

## APK herunterladen oder selbst bauen

Der Repository-Workflow baut nach Änderungen am Ordner `android-local/` eine Debug-APK. Das ist nur der Paketbau: **Bestandsanfragen und Ergebnisanzeige laufen auf dem Handy direkt bei Migros.** Die APK kann im erfolgreichen Actions-Lauf als `pokemon-card-finder-android-test` heruntergeladen werden; das Artefakt bleibt 14 Tage verfügbar. Alternativ benötigt ein lokaler Bau Android Studio (JDK 17, Android SDK 35): Ordner in Android Studio öffnen und **Build → Build APK(s)** wählen. Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Auf dem Android-Gerät muss die Installation von APK-Dateien erlaubt werden.

## Test

App öffnen und **Alle Filialen jetzt abfragen** antippen. Die App lädt die Migros-Produktseite einmal und sucht die Filialen nacheinander im Verfügbarkeitsdialog. Erfasste Ergebnisse werden lokal gespeichert und erscheinen fortlaufend in der Liste. Unlesbare oder fehlende Antworten stehen ausdrücklich als „Nicht eindeutig geprüft“ und werden nicht als „Nicht auf Lager“ ausgegeben. Bei einer Migros-Wartungs- oder Sperrseite wird ein Fehler angezeigt.

Dies ist ein Machbarkeitstest für ein Produkt. Nach erfolgreichem Lauf kann derselbe Prozess auf die sechs übrigen Produkte erweitert und in die vollständige Karten-/Listenansicht eingebunden werden. Android kann Hintergrundaufgaben verzögern; die Schaltfläche startet die Abfrage verlässlich auf Wunsch. Bei 37 Filialen kann ein Lauf mehrere Minuten dauern.
