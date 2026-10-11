# Pokémon Card Finder

Ein lokal ausführbarer Prototyp für die Filialverfügbarkeit von Pokémon-Sammelkarten in Fribourg, Bern und künftig weiteren erfassten Gebieten.

## Stand

- Die App zeigt standardmässig alle 37 bisher eingetragenen Filialen aus Fribourg und Bern in einer gemeinsamen Liste. Ein Gebietsfilter ist optional. Die echte, zoombare Google-Maps-Karte fokussiert auf die ausgewählte Filiale; für weitere Gebiete ergänzt ein Regions-Feed künftig neue Einträge.
- Die App enthält sieben Pokémon-TCG-Katalogartikel im Migros-Filialsortiment der Region Neuenburg-Freiburg, mit aktuellen Migros-Bildern und Preisen (11.10.2026).
- Für PLZ 1700 zeigte Migros Online keine Pokémon-Produkte. Die App kennzeichnet diesen Regionsbefund statt eine schweizweite Aussage zu machen.
- Die in der Quelle mit „Ca.“ angezeigten Bestände bleiben als ungefähre Werte markiert.
- Filialbestände für sieben Artikel in allen 37 eingetragenen Fribourger und Berner Filialen erfasst: 259/259 Produkt-Filial-Kombinationen geprüft (11.10.2026). „Ca.“-Mengen sind Migros-Schätzwerte; „Nicht auf Lager“ ist ebenfalls ein geprüfter Status.
- Die offizielle Fribourg-Suche meldete 14 Standorte; zehn davon sind derzeit in der App-Filialliste erfasst. Die 259/259-Abdeckung bezieht sich auf alle 37 Filialen, die aktuell im System stehen.
- Kaufnotizen werden lokal im Browser dieses Geräts gespeichert.
- Der Button „Daten neu abfragen“ lädt die neueste Feed-Datei. Ein Playwright-Collector fragt im öffentlichen Migros-Produktdialog alle in `data/branches.json` eingetragenen Filialen für jeden Filialartikel ab. Er veröffentlicht keine Teilergebnisse: erst wenn alle Produkt-Filial-Kombinationen erfolgreich gelesen wurden, ersetzt er den Feed. Der GitHub-Actions-Workflow ist im Repository eingerichtet: täglich um 01:15 UTC und zusätzlich manuell startbar. Der erste erfolgreiche Actions-Lauf muss noch geprüft werden. Migros zeigt Bestände einmal täglich aktualisiert und ohne Gewähr an. Ein direkt geöffneter HTML-Download kann den Feed wegen `file://`-Beschränkungen nicht laden.

- Das vollständige Projekt liegt lokal unter Git und ist im öffentlichen GitHub-Repository auf `main` veröffentlicht. Die automatische tägliche Erfassung ist konfiguriert; ihr erster erfolgreicher Lauf und die Verbindung des Feeds zur privaten App-Veröffentlichung sind noch zu prüfen.

## Projektidee

Pokémon Card Finder sammelt öffentlich zugängliche Produkt- und Filialverfügbarkeiten physischer Händler und stellt sie mit Quelle und Prüfzeitpunkt dar. Für eine belastbare Aussage wird zwischen „verfügbar“, „nicht verfügbar“ und „keine Angabe“ unterschieden. Eine exakte Stückzahl wird nur gezeigt, wenn der Händler sie tatsächlich liefert; sonst bleibt sie unbekannt.

## Lokal starten

`dist/index.html` ist eine buildlose App. Öffne sie in einem Browser mit Internetzugang, damit die Migros-Produktfotos geladen werden. Kaufnotizen bleiben im Local Storage. Bestandsmomentaufnahme und offizieller Filialabgleich liegen unter `data/`; Schema und Grenzen der Aktualisierung stehen in [ROADMAP.md](ROADMAP.md). Der Erfassungsablauf ist in `scripts/collect_migros_stock.py` dokumentiert.

## Nächste Schritte

Siehe [ROADMAP.md](ROADMAP.md) und [TODO.md](TODO.md).
