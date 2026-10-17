# Pokémon Card Finder

Ein lokal ausführbarer Prototyp für die Filialverfügbarkeit von Pokémon-Sammelkarten in Fribourg, Bern und künftig weiteren erfassten Gebieten.

## Aktuelle Android-Version

Die native Android-App liegt unter `android-local/` und enthält 669 Migros-Filialen. Sie bietet einzelne Kantonsfilter, eigene Filialgruppen, Bestandsdifferenzen mit 24-Stunden-Anzeige, eine 7-Tage-Grafik und einen Änderungsverlauf. Die browserbasierte `dist/index.html` ist ein separater, älterer Prototyp und enthält weiterhin nur den dort dokumentierten Datenstand.

Der Android-Build wird über den Workflow `.github/workflows/build-android-test.yml` erzeugt. Weitere Informationen stehen in [android-local/README.md](android-local/README.md).

## Stand

- Die App zeigt standardmässig alle 37 bisher eingetragenen Filialen aus Fribourg und Bern in einer gemeinsamen Liste. Ein Gebietsfilter ist optional. Die echte, zoombare Google-Maps-Karte fokussiert auf die ausgewählte Filiale; für weitere Gebiete ergänzt ein Regions-Feed künftig neue Einträge.
- Die App enthält sieben Pokémon-TCG-Katalogartikel im Migros-Filialsortiment der Region Neuenburg-Freiburg, mit aktuellen Migros-Bildern und Preisen (11.10.2026).
- Für PLZ 1700 zeigte Migros Online keine Pokémon-Produkte. Die App kennzeichnet diesen Regionsbefund statt eine schweizweite Aussage zu machen.
- Die in der Quelle mit „Ca.“ angezeigten Bestände bleiben als ungefähre Werte markiert.
- Filialbestände für sieben Artikel in allen 37 eingetragenen Fribourger und Berner Filialen erfasst: 259/259 Produkt-Filial-Kombinationen geprüft (11.10.2026). „Ca.“-Mengen sind Migros-Schätzwerte; „Nicht auf Lager“ ist ebenfalls ein geprüfter Status.
- Die offizielle Fribourg-Suche meldete 14 Standorte; zehn davon sind derzeit in der App-Filialliste erfasst. Die 259/259-Abdeckung bezieht sich auf alle 37 Filialen, die aktuell im System stehen.
- Kaufnotizen werden lokal im Browser dieses Geräts gespeichert.
- Der Button „Daten neu abfragen“ lädt die neueste Feed-Datei. Der Playwright-Collector fragt im öffentlichen Migros-Produktdialog alle in `data/branches.json` eingetragenen Filialen für jeden Filialartikel ab. Er veröffentlicht keine Teilergebnisse: Erst wenn alle Produkt-Filial-Kombinationen erfolgreich gelesen wurden, ersetzt er den Feed. Der GitHub-Actions-Workflow läuft täglich um 01:15 UTC und kann manuell gestartet werden. Zwei Actions-Läufe erhielten am 11.10.2026 von Migros statt der Produktseite eine Wartungsseite (`maintenance`); deshalb ist noch kein automatischer Feed-Commit erfolgt. Eine Fehlerdiagnose protokolliert diesen Seitentext, und der vorhandene Feed bleibt erhalten. Die App lädt den GitHub-Feed beim Start und prüft ihn alle 15 Minuten; der Hosting-Feed dient als Rückfalloption. Migros aktualisiert die angezeigten Filialbestände selbst und ohne Gewähr. Ein direkt geöffneter HTML-Download kann den Feed wegen `file://`-Beschränkungen nicht laden.

- Das vollständige Projekt liegt lokal unter Git und ist im öffentlichen GitHub-Repository auf `main` veröffentlicht. Die automatische Erfassung ist konfiguriert; ein erfolgreicher Lauf vom GitHub-Runner muss noch verifiziert werden. Der gespeicherte Feed bleibt bis dahin die letzte vollständige Momentaufnahme.

## Projektidee

Pokémon Card Finder sammelt öffentlich zugängliche Produkt- und Filialverfügbarkeiten physischer Händler und stellt sie mit Quelle und Prüfzeitpunkt dar. Für eine belastbare Aussage wird zwischen „verfügbar“, „nicht verfügbar“ und „keine Angabe“ unterschieden. Eine exakte Stückzahl wird nur gezeigt, wenn der Händler sie tatsächlich liefert; sonst bleibt sie unbekannt.

## Lokal starten

`dist/index.html` ist eine buildlose App. Öffne sie in einem Browser mit Internetzugang, damit die Migros-Produktfotos geladen werden. Kaufnotizen bleiben im Local Storage. Bestandsmomentaufnahme und offizieller Filialabgleich liegen unter `data/`; Schema und Grenzen der Aktualisierung stehen in [ROADMAP.md](ROADMAP.md). Der Erfassungsablauf ist in `scripts/collect_migros_stock.py` dokumentiert.

## Nächste Schritte

Siehe [ROADMAP.md](ROADMAP.md) und [TODO.md](TODO.md).

## Historische Android-Daten sichern und migrieren

Ab dem nächsten erfolgreich **dauerhaft signierten** Android-Build bietet die native App auf der Migros-Hauptseite zwei Tasten: **„Backup herunterladen · Migros + Coop“** und **„Backup hochladen / wiederherstellen“**. Export/Import nutzen JSON über den Android-Dateidialog. Der Import erhält bestehende Messpunkte und führt sie mit dem Backup zusammen; neue Messpunkte werden nicht länger nach sieben Tagen gelöscht (die sichtbare Grafik bleibt bei sieben Tagen).

**Sonderfall: Erstumstieg von v1.2.61 oder früher.** Diese alten APKs haben temporäre Debug-Signaturen; sie können nicht als Update mit dem neuen festen Schlüssel überschrieben werden. Alte Daten **vor jeder Deinstallation** sichern! In der alten APK gibt es noch keinen Export-Button. Die alten Debug-APKs sind jedoch debuggable; über Android Platform Tools (ADB) und USB-Debugging lassen sich die lokalen App-Daten meist ohne Root exportieren. Auf einem Computer mit `adb` und Python 3 bei angeschlossenem/entsperrtem Android-Gerät im Projektordner ausführen:

```bash
python3 scripts/migrate_legacy_android_prefs.py --adb
```

Die lokale Datei `pokemon-card-finder-legacy-backup.json` sicher aufbewahren und aufs Smartphone kopieren. Erst danach die bisherige APK entfernen, die neue **erfolgreich dauerhaft signierte** Version installieren und über **„Backup hochladen / wiederherstellen“** die Datei importieren. Der ADB-Zugriff funktioniert nur bei der alten debuggable APK und aktiviertem USB-Debugging; ohne einen Export dieser alten App-Daten sind sie nach der Deinstallation in der Regel nicht wiederherstellbar.

Neue signierte APKs werden erst veröffentlicht, wenn die beiden GitHub-Secrets aus [SIGNING_SETUP.md](android-local/SIGNING_SETUP.md) eingerichtet sind. Der Build prüft den Java-Code bereits vor dem Signieren; bei fehlenden Schlüsseln bleibt die vorherige Release-APK unverändert.
