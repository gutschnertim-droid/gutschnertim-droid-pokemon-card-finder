# Projektplan — Pokémon Card Finder

## Ziel

Ein mobil nutzbarer Finder, der Produkte von Pokémon TCG bei physischen Händlern in der Schweiz nach Filiale auffindbar macht. Die Karte zeigt Bestand oder Verfügbarkeitsstatus, Händlerquelle und den Zeitpunkt der letzten Prüfung. Käufe können zur eigenen Orientierung erfasst werden.

## Startumfang (MVP)

1. **Pilotgebiet:** Fribourg (1700) sowie Bern-Stadt und nahe Agglomeration.
2. **Händler:** Migros und Coop.
3. **Sortiment:** Pokémon Sammelkarten und versiegelte Produkte; zuerst Booster Blister und Mini Tin.
4. **Ansichten:** Karte und Liste, Suche und Filter nach Händler, Produkt und Status.
5. **Datenqualität:** Quelle, letzte Prüfung und Verfügbarkeitsart je Produkt/Filiale.
6. **Eigene Notizen:** Kaufdatum und Menge, klar getrennt vom Händlerbestand.

## Grundregeln für Daten

- „Live“ heisst zunächst: regelmässig aktualisiert und mit sichtbarem Prüfzeitpunkt, nicht garantiert sekundengenau.
- Händlerangaben dürfen nicht präziser dargestellt werden als die Quelle. Bei einem blossen Verfügbarkeitsindikator gibt es keine erfundene Stückzahl.
- Fehlende Angaben sind „unbekannt“, nie automatisch „ausverkauft“.
- Jede Beobachtung speichert Händler, Filiale, Produkt-ID/URL, Rohstatus, erkannte Menge (falls vorhanden), Abrufzeit, Quelle und Abrufmethode.
- Käufe der Nutzerin/des Nutzers sind private Nutzungsdaten und werden nicht als Händlerbestand interpretiert.
- Zuerst offizielle Schnittstellen oder ausdrücklich erlaubte Datenzugriffe prüfen; Aktualisierungsrate und Nutzungsbedingungen respektieren.

## Standortabgleich (11.10.2026)

- Die offizielle Migros-Filialsuche mit Supermarktfilter und Suchort „Bern“ meldete 27 Standorte in der Ergebnisliste. Der unfiltrierte Suchradius zeigte 50 Standorte insgesamt. Das ist Stadt und nahe Agglomeration, kein vollständiges Kantonsverzeichnis. Die App führt die 27 offiziellen Migros-Supermärkte als Bern-Region. Bestände für alle sieben gelisteten Produkte wurden am 11.10.2026 in fünf öffentlichen Suchabfragen je Produkt erfasst.
- Die offizielle Suche mit Suchort „Fribourg“ meldete 14 Standorte; aktuell sind zehn davon Teil der App-Filialliste. Die hier bestätigte Vollabdeckung umfasst sämtliche 37 in der App eingetragenen Filialen, nicht weitere noch nicht eingetragene Standorte.
- [x] Vollständiger Bestandsdurchlauf: alle 37 Filialen, die aktuell im System erfasst sind, × sieben Produkte = 259/259 geprüfte Kombinationen (11.10.2026). „Nicht auf Lager“ ist ein positiver Prüfstatus, kein fehlender Wert.

## Erste Quellenprüfung (11.10.2026)

- **Direkt bestätigt:** Auf der normalen, nicht angemeldeten Migros-Produktseite öffnet „Verfügbarkeit in Filialen prüfen“ eine Standortsuche. Nach Eingabe von „1700 Fribourg“ zeigt der Dialog zehn Filialen und Angaben wie „Ca. 8 auf Lager“ oder „Nicht auf Lager“.
- **Migros-Produktdialog:** Filialverfügbarkeit wird einmal täglich aktualisiert und ohne Gewähr angezeigt; deshalb sind die erfassten Bestände Momentaufnahmen und „Ca.“-Mengen Schätzwerte.
- **Offizielle Search API geprüft:** Die öffentlich dokumentierte Migros Search API bietet Produkt- und Filialsuche. Die dokumentierten Endpunkte verlangen die Parameter `key` und `apikey`; die Produktantwort dokumentiert Preis- und Produktinfos, aber keinen Bestand je Filiale. Sie löst den Bestandsimport daher nicht ohne Weiteres.
- **Ergebnis für den Pilot:** Für die Bestände nehmen wir den öffentlichen Verfügbarkeitsdialog auf der Produktseite als Quelle. Ein erster Durchlauf funktioniert im normalen Browser ohne Login. Das Projekt braucht dafür keine persönlichen Migros-Daten. Die API kann später optional für Produkt- und Filialstammdaten dienen, sofern ein zulässiger Zugang verfügbar ist.
- Migros weist ausserdem darauf hin, dass das gesamte Online-Sortiment nicht zwingend in jeder Filiale erhältlich ist.
- Die erste Momentaufnahme für das Booster Blister und die erweiterte Momentaufnahme mit sieben Produkten liegen in [`data/migros_booster_blister_1700_20261011.json`](data/migros_booster_blister_1700_20261011.json) und [`data/migros_pokemon_stock_fribourg_20261011.json`](data/migros_pokemon_stock_fribourg_20261011.json). Mengen sind ausdrücklich Näherungswerte der Migros-Seite.
- **Nächster technischer Weg:** Die sichtbare öffentliche Filialabfrage automatisiert nachbilden (Produktseite öffnen, PLZ/Ort eingeben, Status/Mengen aus den Ergebnissen lesen). Vor einem wiederkehrenden Abruf Nutzungsbedingungen und erlaubte Frequenz prüfen; zunächst maximal einmal täglich pro Produkt.
- Quellen: [Migros Search API-Dokumentation](https://search-api.migros.ch/doc), [Migros zur Filialverfügbarkeit](https://support.migros.ch/hc/de/articles/11484223470354-Kann-ich-mich-auf-die-Angaben-zur-Verf%C3%BCgbarkeit-in-den-Filialen-verlassen), [Migros zum Sortiment in Filialen](https://support.migros.ch/hc/de/articles/360017247580-Finde-ich-das-ganze-Online-Sortiment-auch-in-meiner-Migros-Filiale), [Migros Pokémon Booster Blister](https://www.migros.ch/de/product/746658000000).

### Migros-TCG-Katalog für Fribourg (11.10.2026)

- Die öffentliche Migros-Seite wurde für das Filialsortiment in der Region **Neuenburg-Freiburg** und danach für Online-Bestellungen mit PLZ **1700 Fribourg** geprüft.
- In der TCG-Kategorie zeigte Migros sieben Artikel im Filialsortiment: Booster Blister, 4-Pocket Portfolios, Deck Protector Sleeves, Deck Boxen, Mini Tin, 30th Anniversary Tech Sticker Collection und 30th Anniversary Poster C.
- Mit aktiviertem Online-Sortiment und bestätigter PLZ 1700 meldete Migros: „Wir konnten im Online-Sortiment keine Produkte finden.“ Daher zeigt die App für diesen regionsbezogenen Abruf **0 reine Online-Artikel**. Das ist keine Aussage für sämtliche Migros-Regionen der Schweiz.
- Die Produktbilder sind direkt von Migros' Bildserver übernommen; die Produktlinks führen jeweils auf die offizielle Migros-Seite. Die Preise entsprechen der gelisteten Ansicht beim Abruf.
- Die Regionalseite weist Produkte als Teil des Filialsortiments aus, garantiert aber keinen Bestand jeder einzelnen Filiale. Am 11.10.2026 wurden für alle sieben Artikel die sichtbaren Bestände in den zehn konkreten Fribourger Filialen erfasst. „Ca.“-Mengen bleiben als ungefähre Werte erkennbar; „Nicht auf Lager“ wird getrennt von „unbekannt“ geführt.
- Momentaufnahme: [`data/migros_pokemon_catalog_fribourg_20261011.json`](data/migros_pokemon_catalog_fribourg_20261011.json). Migros-Katalog: [Pokémon-Marke](https://www.migros.ch/de/brand/pokemon); [Kategorie Trading Card Games](https://www.migros.ch/en/category/games-hobbies-gifting/games-puzzles/board-card-games/trading-card-games).

## Feed-Schema für neue Filialen

`data/branches.json` ist die verbindliche Filialliste. Der Collector liest alle Produktartikel mit `store: true` aus dem Migros-Katalog und prüft pro Artikel jede Filiale der Liste. Nach erfolgreicher Prüfung schreibt er die Statuswerte regional unter `regions.<region>.products.<produktId>.branches.<filialId>`. Er ersetzt den Feed atomar und nur, wenn alle Kombinationen gelesen wurden. Eine neue Filiale wird mit ID, Name, Adresse und Region in `data/branches.json` eingetragen und geht damit automatisch in jede Abfragerunde ein. Der tägliche GitHub-Actions-Workflow ist im Projekt vorbereitet; er wird aktiv, sobald das Projekt in ein GitHub-Repository übertragen wurde.


## Etappen

### A — Prototyp (in Arbeit)

- [x] Projektgerüst und MVP-Abgrenzung
- [x] Zoombare Google-Maps-Karte und gemeinsame Liste aller 37 erfassten Fribourg-/Bern-Filialen; Regionsfilter optional
- [ ] Kartenradius und Filialliste synchronisieren; aktuelle Google-Maps-Einbettung zoomt selbst, aber die App kann deren Kartenfenster nicht auslesen
- [x] Filter, Suche, Filialdetails und lokale Kaufnotiz
- [x] Hinweis auf Momentaufnahme und Datenqualität
- [x] Manueller Feed-Check mit Ladeanzeige; neue Feed-Filialen werden dynamisch ergänzt und fehlende Bestände bleiben unbekannt
- [ ] Produkt- und Filialkatalog aus den aktuellen Händlerseiten verifizieren

### B — Datenquellen prüfen

- [x] Migros: Produktseiten und offizieller Aktualisierungshinweis dokumentieren
- [x] Migros: öffentliche Filialabfrage ohne Login mit PLZ 1700 getestet
- [x] Migros: ungefähre Bestandszahlen für alle sieben Produkte und zehn Fribourger Filialen als Rohmomentaufnahme erfasst
- [x] Migros: aktuellen TCG-Katalog in Region Neuenburg-Freiburg eingesehen; 7 Artikel mit offiziellen Bildern und Preisen erfasst
- [x] Migros: Online-Sortiment für PLZ 1700 geprüft; keine Pokémon-Produkte angezeigt
- [ ] Migros: gesamtschweizerische Abdeckung mit weiteren Regionen prüfen
- [x] Migros: öffentlichen Abruf als Playwright-Collector vorbereiten; bei Abfragefehlern bleibt die letzte vollständige Momentaufnahme erhalten
- [x] Migros: offizielle Search API-Dokumentation prüfen (kein Filialbestand im beschriebenen Produktmodell)
- [x] Frontend: hosted App prüft `data/stock-latest.json` beim Laden und dann alle 15 Minuten mit Cache-Umgehung
- [x] Feed-Writer und täglichen GitHub-Zeitplan ergänzen; Ausführung startet, sobald der Quellcode im verbundenen Repository liegt
- [x] Migros: wiederkehrenden Abruf des öffentlichen Verfügbarkeitsdialogs implementieren; vor öffentlichem Dauerbetrieb Nutzungsbedingungen prüfen
- [ ] Coop: direkte Produktseiten und verfügbare Filialdaten dokumentieren
- [ ] Prüfen, ob Mengen wirklich veröffentlicht werden oder nur Verfügbarkeit
- [ ] Nutzungsbedingungen, robots.txt, Rate Limits und mögliche Partnerfreigaben prüfen
- [ ] Bei fehlender stabiler Quelle: manuelle Community-Meldungen mit Zeitstempel als getrennten Datentyp entwerfen

### C — MVP-Datenmodell und Backend

- [ ] Datenmodell für Händler, Filialen, Produkte, Verfügbarkeitsbeobachtungen und private Käufe festlegen
- [ ] Adressen/Koordinaten für Pilotfilialen verifizieren
- [ ] Datenquelle mit nachvollziehbarer Abrufhistorie anbinden
- [ ] Aktualisierungsjob mit Fehlerprotokoll und Quellenstatus aufsetzen
- [ ] API/Backend mit „unbekannt“, „nicht verfügbar“ und „verfügbar“ sauber abbilden
- [ ] Veraltete Daten sichtbar markieren

### D — Test und Ausbau

- [ ] Fribourg mit echten Nutzenden und Filialbesuchen gegenprüfen
- [ ] Mobile Nutzung und Barrierefreiheit prüfen
- [ ] Benachrichtigungen für gewünschte Produkte/Filialen bewerten
- [ ] Erst nach stabiler Pilotphase weitere Schweizer Händler und passende Sortimente ergänzen

## Offene Architekturentscheidung

Die gehostete Oberfläche kann einen bereitgestellten Feed abfragen, aber eine statische HTML-Datei erzeugt selbst keine neuen Migros-Daten. Der Collector und der tägliche GitHub-Actions-Job sind im Projekt angelegt. Der automatische Lauf ist noch nicht aktiv, weil für dieses lokale Projekt kein Git-Remote eingerichtet ist. Die Datei im Download enthält deshalb die vollständige Momentaufnahme vom 11.10.2026; nach dem ersten Lauf im Repository wird sie täglich ersetzt. Der Migros-Produktdialog sagt, die Bestandsangaben werden einmal täglich aktualisiert; ein täglicher eigener Abruf wäre daher der geplante Höchstwert. Rechtliche Informationen von Migros behalten die Nutzung des Portals für öffentliche oder kommerzielle Zwecke ohne schriftliche Zustimmung vor; vor einer öffentlichen Bereitstellung eines automatisierten Datenabrufs ist die konkrete Nutzung zu klären.
