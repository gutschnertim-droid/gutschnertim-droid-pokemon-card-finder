# Pokémon Card Finder – lokaler Android-Live-Test

Diese native Test-App fragt die **sieben Pokémon-TCG-Artikel im vorhandenen Migros-Katalog** in allen Filialen aus `app/src/main/assets/branches.json` direkt auf dem Android-Gerät ab. Die aktuelle Filialliste umfasst 37 Fribourger und Berner Standorte. Damit umfasst ein vollständiger Lauf bis zu 259 Artikel-Filial-Kombinationen. Anfrage und letzte Anzeige laufen lokal; die App benötigt weder GitHub-Feed noch eigenen Server oder Migros-Login. Der Katalog ist der erfasste Stand für PLZ 1700 und keine schweizweite Sortimentserhebung.

## APK herunterladen oder selbst bauen

Der Repository-Workflow baut nach Änderungen am Ordner `android-local/` eine Debug-APK. Nach erfolgreichem Build steht sie direkt zum Download bereit: https://github.com/gutschnertim-droid/gutschnertim-droid-pokemon-card-finder/releases/download/android-test-latest/app-debug.apk . **Bestandsanfragen und Ergebnisanzeige laufen auf dem Handy direkt bei Migros.** Alternativ benötigt ein lokaler Bau Android Studio (JDK 17, Android SDK 35): Ordner in Android Studio öffnen und **Build → Build APK(s)** wählen. Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Auf dem Android-Gerät muss die Installation von APK-Dateien erlaubt werden.

## Test

App öffnen, gewünschten Artikel im Auswahlfeld wählen und **Alle Artikel in allen Filialen abfragen** antippen. Die App fragt alle sieben Produktseiten nacheinander ab und sucht die Filialen jeweils im Verfügbarkeitsdialog. Erfasste Ergebnisse werden lokal gespeichert und erscheinen fortlaufend in der Liste. Unlesbare oder fehlende Antworten stehen ausdrücklich als „Nicht eindeutig geprüft“ und werden nicht als „Nicht auf Lager“ ausgegeben. Bei einer Migros-Wartungs- oder Sperrseite wird der Lauf gestoppt; bereits erfasste Ergebnisse bleiben lokal gespeichert.

Die Filialliste kann nach Filialname oder Ort durchsucht und nach Verfügbarkeit, Nichtverfügbarkeit, unklarem Status oder noch nicht erfolgter Prüfung gefiltert werden. Eine Übersicht zählt die vier Statusgruppen für den ausgewählten Artikel. Weitere geplante Erweiterungen stehen in [TODO.md](TODO.md).

Verfügbare Filialen werden mit einem grünen Punkt hervorgehoben. „Höchster Bestand“ sortiert verfügbare Filialen nach der ausgelesenen Ca.-Menge. Nach jedem erneuten Live-Lauf markiert die App Restocks sowie erkannte Mengenanstiege/-rückgänge. Preise werden während des Produktseitenaufrufs aus sichtbaren oder strukturierten Preisfeldern gelesen und mit dem vorherigen lokalen Preis verglichen; ein tieferer Preis wird als möglicher Rabatt markiert. Preis- und Bestandsvergleiche beginnen mit dem nächsten Live-Lauf nach der Installation. Preisalarme funktionieren nur, wenn Migros den aktuellen Preis auf der Produktseite lesbar ausgibt.

Die Produktbilder werden von der Migros-Bildquelle geladen. Android kann Hintergrundaufgaben verzögern; die Schaltfläche startet die Abfrage verlässlich auf Wunsch. Bei 259 Kombinationen kann ein Lauf länger dauern. Die App sollte dabei geöffnet bleiben.

Die automatische Aktualisierung ist standardmässig eingeschaltet und kann in der App ausgeschaltet werden. Beim ersten Öffnen startet sie selbstständig; danach startet sie alle vier Stunden, solange die App geöffnet ist. Wenn die App währenddessen geschlossen oder im Hintergrund war, startet eine fällige Abfrage beim nächsten Öffnen. Die aktuelle Migros-Erfassung nutzt die App-WebView und läuft deshalb nicht zuverlässig bei vollständig geschlossener App.
