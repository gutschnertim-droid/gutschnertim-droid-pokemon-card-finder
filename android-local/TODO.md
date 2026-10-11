# Pokémon Card Finder – To-do

## Erledigt

- [x] Live-Abfrage direkt auf dem Android-Gerät; kein eigener Server und kein Migros-Login nötig.
- [x] Pokémon-TCG-Katalog mit Produktfotos und Preisen in der App anzeigen.
- [x] Landesweiten Bestand mit 669 normalen M/MM/MMM-Supermärkten laden (VOI und weitere Formate ausschliessen).
- [x] Fortschritt während der Abfrage anzeigen und Ergebnisse lokal speichern.
- [x] Unbekannten Bestand getrennt von „Nicht auf Lager“ darstellen.
- [x] Filialen nach Name oder Ort suchen, Bestände filtern und Statussummen anzeigen.
- [x] Verfügbare Filialen mit grünem Punkt kennzeichnen.
- [x] Nach Filialen mit dem höchsten erfassten Bestand filtern und sortieren.
- [x] Bei erkanntem Restock sowie steigendem oder sinkendem Bestand ein Symbol anzeigen.
- [x] Produktseiten nach Livepreisen und sichtbaren Rabattpreisen auslesen; Preisänderungen markieren.
- [x] Benachrichtigungen einzeln für Restock, steigenden Bestand, sinkenden/steigenden Preis und Aktionen einstellbar machen.
- [x] Eigenes Pokémon-Card-Finder-Launcher-Symbol statt Android-Standardsymbol einbauen.
- [x] Bei jeder Aktualisierung die Migros-Pokémon-Produktseite nach neuen Karten-/TCG-Artikeln durchsuchen, lokal ergänzen und im selben Lauf mitprüfen.
- [x] Nach erfolgreichem Build einen stabilen Direktlink zur APK und zur ZIP-Datei mit der APK bereitstellen.
- [x] Bestände automatisch alle vier Stunden neu abfragen, wenn die App geöffnet ist; eine fällige Prüfung beim Zurückkehren starten.
- [x] Zwölf Gebiete auswählen und nur die darin enthaltenen Filialen abfragen; die Auswahl auf dem Gerät merken.
- [x] Fribourg auf 30 Filialen anhand der offiziellen Migros-Filialliste und PLZ erweitern.
- [x] Regionstitel für Bern, Fribourg, Zürich, Ostschweiz/St. Gallen, Waadt/Lausanne, Basel, Genf, Neuenburg, Aare, Zentralschweiz, Tessin und Wallis anzeigen.
- [x] Alte Auswahl „Weitere Schweiz“ beim Start in alle neuen übrigen Gebiete übernehmen.

## Als Nächstes

1. [ ] Abfrage für ein einzelnes Produkt und eine ausgewählte Filiale ermöglichen, damit gezielte Aktualisierungen schneller gehen.
2. [ ] Unterbrochene Abfragen nach erneutem Öffnen der App fortsetzen können.
3. [ ] Offizielle Migros-Filialliste der normalen M/ MM/ MMM-Supermärkte gegen den lokalen Bestand abgleichen und fehlende Filialen ergänzen; VOI und andere Formate ausschliessen.
4. [ ] Pokémon-TCG-Sortiment systematisch in allen ausgewählten Regionen erfassen und neue Produkte nachvollziehbar in den Katalog aufnehmen.
5. [ ] Kartenansicht mit Filialstatus, Bestandsfilter und Wegbeschreibung ergänzen.
6. [ ] Produktentdeckung über weitere Migros-Kategorie- und Suchseiten ergänzen; die aktuelle Suche erfasst nur Produktlinks, die auf der öffentlichen Pokémon-Seite gerendert werden.
7. [ ] Tägliche Hintergrundabfrage mit Android WorkManager ergänzen; lokal auf dem Handy, gefiltert nach den gewählten Regionen/Artikeln. Android kann den Lauf wegen Akku-/Netzplanung verschieben.
8. [ ] Arbeit in kleine Pakete aufteilen und nach Schliessen/Neustart fortsetzen; Bestände, Preise und Zeitstempel lokal speichern.
9. [ ] Wartungs-/Fehlerantworten nicht als aktuelle Daten übernehmen; letzte erfolgreiche Werte sichtbar als veraltet markieren.
10. [ ] Favoriten, manuelle Kaufnotizen und CSV-Verlauf ergänzen.
11. [ ] Zeitstempel und Fehlermeldungen pro Filiale deutlicher machen, damit alte und nicht gelesene Daten leicht erkennbar sind.
12. [ ] Signierte Android-Version und einen verlässlichen Aktualisierungsweg für Installationen einrichten.

## Umfangshinweis

Der aktuelle Katalog enthält sieben im bisherigen Datensatz erfasste Artikel und ist keine vollständige schweizweite Migros-Sortimentserhebung. Die offizielle Migros-Liste enthält 669 normale M/MM/MMM-Supermärkte. Die App stellt dafür zwölf auswählbare Gebiete bereit; Fribourg umfasst derzeit 30 anhand der Postleitzahl zugeordnete Filialen. Die tägliche Prüfung im Hintergrund ist noch offen und muss auf einem Galaxy-Gerät getestet werden. Migros weist im Geschäftsbericht 2025 insgesamt 659 M-, MM- und MMM-Supermärkte aus. Andere Verkaufsformate, darunter VOI, sind nicht Teil dieser Zahl.
