# To-dos

## Jetzt

- [x] Neues Projekt „Pokémon Card Finder“ angelegt
- [x] Pilotgebiet auf Fribourg (1700) gesetzt
- [x] Interaktive Vorschau mit Karten-/Listenansicht begonnen
- [x] Bestandsstatus von Kaufnotizen getrennt
- [ ] Prototyp auf Smartphone testen und Filialnamen/-standorte verifizieren
- [x] Frontend liest den neuesten Feed direkt aus GitHub; private Hosting-Datei bleibt Rückfalloption
- [ ] Aktualisiertes Frontend auf der bestehenden privaten Site veröffentlichen

## GitHub-Repository und Automatik

- [x] Lokales Git-Repository vorbereitet und vollständigen Projektstand committet (`a8ba113`)
- [x] GitHub-Verbindung aktiv und Konto geprüft (`gutschnertim-droid`)
- [x] Vom Nutzer geliefertes öffentliches Repository geprüft und als lokales `origin` eingetragen: `gutschnertim-droid/gutschnertim-droid-pokemon-card-finder`
- [x] ChatGPT Codex Connector auf dem GitHub-Konto installiert und Repository-Zugriff erteilt
- [x] Projektdateien, Bestandsdaten und Workflow auf `main` veröffentlicht (Commit `2d4959c`)
- [x] Täglichen GitHub-Actions-Workflow (01:15 UTC) und manuellen Start im Repository eingerichtet
- [x] Vollständigen Abdeckungsbericht je Region im Actions-Lauf ergänzen; Feed muss 259/259 Kombinationen enthalten
- [ ] Ersten erfolgreichen Actions-Lauf prüfen: Nach dem geplanten Lauf ist noch kein neuer Bestands-Commit sichtbar; den Lauf im Actions-Tab kontrollieren und bei Aufforderung aktivieren
- [x] Feed-URL im Frontend auf den öffentlichen GitHub-Feed gesetzt; Aktualisierung erfolgt beim Start und danach alle 15 Minuten
- [ ] Private Site mit dem aktualisierten Frontend neu veröffentlichen (Quell-Sync aktuell nicht erreichbar)
- [ ] Bei neuen Filialen Daten in `data/branches.json` ergänzen und Abdeckung im nächsten Lauf verifizieren

## Android-Installation

- [ ] PWA-Installation über HTTPS auf dem Galaxy S24 Ultra prüfen
- [ ] Native APK/TWA-Alternative und Signierung bewerten, falls PWA-Installation nicht genügt

## Datenzugriff

- [x] Migros-Produktdialog geprüft: Filialverfügbarkeit wird einmal täglich aktualisiert und ist ohne Gewähr
- [x] Migros-Produkt- und Filialverfügbarkeit aus erster Hand erfassen; öffentliche Abfrage funktioniert ohne Login
- [x] Erste Momentaufnahme mit zehn Fribourg-Filialen und ungefähren Mengen speichern
- [x] Filialbestände für alle sieben Pokémon-Artikel in zehn Filialen erfassen und je Artikel anzeigen
- [x] Sieben aktuelle Pokémon-TCG-Artikel im Filialsortiment der Migros-Region erfassen
- [x] Offizielle Migros-Produktfotos und aktuelle Katalogpreise in der App ergänzen
- [x] Migros Online für PLZ 1700 prüfen; keine Pokémon-Produkte im Online-Sortiment gefunden
- [x] App nach Filial-/Online-Sortiment filtern und unbekannte Filialbestände ausdrücklich ausweisen
- [x] Interaktive App mit Migros-Momentaufnahme, Suche, Statusfilter, Filialstandorten und lokalen Kaufnotizen bauen
- [x] Schematische Grafik durch eine echte, zoombare Google-Maps-Karte ersetzen; Karte folgt der Filiale und allen Gebieten
- [x] Button mit Ladeanzeige zum Prüfen und Laden der neuesten Feed-Datei ergänzen
- [x] Alle 37 derzeit erfassten Fribourg- und Bern-Filialen gemeinsam in einer Liste anzeigen; Region optional filtern
- [x] Feed-Verarbeitung zeigt geprüfte Verfügbarkeit und Nichtverfügbarkeit; alle derzeit gelisteten Filialen werden abgedeckt
- [ ] Kartenradius mit der App-Liste synchronisieren und Standortdaten für alle Filialen bereitstellen; Google Maps zeigt derzeit eine zoombare Migros-Suche
- [x] Original-Migros-Link für eine manuelle aktuelle Prüfung integrieren
- [x] Frontend auf Region Bern erweitern; offizielle Suche „Bern“ mit Supermarktfilter ergab 27 Treffer in Stadt und Agglomeration
- [x] Gehostete App fragt `data/stock-latest.json` beim Öffnen und alle 15 Minuten ohne Cache ab
- [ ] Abgleich der vier zusätzlichen offiziellen Fribourg-Standorte mit der App-Filialliste (Bestandsabdeckung gilt für alle 37 derzeit eingetragenen Filialen)
- [x] Bestände für sieben Pokémon-Artikel in allen 27 erfassten Berner Filialen prüfen und in den Feed übernehmen (11.10.2026).
- [x] UI-Collector erfasst alle Filialen aus `data/branches.json` für jeden Filialartikel; schreibt den Feed nur bei vollständiger Abdeckung (Playwright, ohne Login)
- [x] Täglichen GitHub-Actions-Ablauf und manuellen Start im veröffentlichten Repository eingerichtet; erster Lauf noch zu prüfen
- [ ] Sortiment schweizweit gegen weitere Migros-Regionen prüfen; aktueller Katalogstand gilt für Region Neuenburg-Freiburg / PLZ 1700
- [x] Filialbestand je Produkt erfassen, soweit die Migros-Produktseite dies öffentlich ausgibt
- [x] Öffentliche Migros Search API-Dokumentation prüfen: Produkt- und Filialsuche vorhanden, aber kein Filialbestand; Doku nennt `key` und `apikey`
- [x] Produktseiten-Dialog als Datenquelle automatisieren; keine Migros-Zugangsdaten voraussetzen
- [ ] Prüfen, welche Coop-Produktseiten Filialverfügbarkeit zeigen
- [ ] Ermitteln, ob Händler Stückzahlen oder nur „verfügbar/nicht verfügbar“ ausgeben
- [ ] Zulässige Abrufhäufigkeit und Nutzungsbedingungen für automatisches Auslesen klären

## Produkt

- [ ] Datenmodell und Datenbank entwerfen
- [x] „Zuletzt geprüft“-Zeitpunkt pro Produkt in der App anzeigen
- [x] Gehostete App mit Feed-Poller und Statusanzeige ausgestattet (15-Minuten-Abstand)
- [x] Collector und täglicher Job implementiert. Der Collector befragt die öffentliche Produktseite ohne Login und schreibt bei Erfolg `regions.<region>.branches` sowie `regions.<region>.products.<id>.branches.<branchId>`. Der Workflow ist auf GitHub veröffentlicht; erfolgreicher Lauf noch zu verifizieren.
- [ ] Alte Daten bei erfolgreichem/fehlgeschlagenem Feed klar als veraltet kennzeichnen
- [ ] Fehlermeldungen und alte Daten sichtbar kennzeichnen
- [ ] Private Käufe optional mit Datum, Menge und Filiale erfassen
- [ ] Weitere Händler erst nach funktionierendem Pilot ergänzen
