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
- [ ] GitHub-Datenlauf verifizieren: Der Runner erhielt auch beim erneuten Versuch am 11.10.2026 um 21:00 CEST die Migros-Wartungsseite. Der Feed bleibt ein Snapshot vom 11.10.2026, 01:23 CEST (37 Filialen × 7 Artikel = 259 Kombinationen). Die normale Migros-Seite und der Verfügbarkeitsdialog waren im Browser erreichbar; wahrscheinlich netzwerkabhängige Antwort für den GitHub-Runner.
- [x] Feed-URL im Frontend auf den öffentlichen GitHub-Feed gesetzt; Aktualisierung erfolgt beim Start und danach alle 15 Minuten
- [ ] Private Site mit dem aktualisierten Frontend neu veröffentlichen (Quell-Sync aktuell nicht erreichbar)
- [x] Landesweite Migros-Liste wird aus dem offiziellen Filialfinder aufgebaut; Regionen werden beim Einlesen automatisch zugewiesen und neue Filialen erscheinen ohne manuelle Regionsliste

## Regelmässige Aktualisierung auf dem Handy (hohe Priorität)

Für die persönliche, lokale App muss der tägliche Abruf nicht über GitHub laufen. Der nächste Umsetzungsschritt ist Android WorkManager: Die App plant auf dem Galaxy einen wiederkehrenden lokalen Auftrag, der die gewählten Regionen/Artikel in kleinen, fortsetzbaren Paketen direkt bei Migros prüft und Ergebnisse samt Zeitstempel lokal speichert. Android führt solche Aufträge im Hintergrund nach eigener Akku- und Netzplanung aus; die Ausführung kann deshalb später als exakt 24 Stunden erfolgen.

- [ ] Täglichen WorkManager-Auftrag mit Netzwerkbedingung und lokaler Regions-/Artikelauswahl einrichten
- [ ] Abfragen paketweise fortsetzen und Fortschritt pro Artikel/Filiale dauerhaft speichern, auch nach App-Neustart
- [ ] Preise, Rabattkennzeichen, neue Produkte und Bestände im selben Auftrag aktualisieren; neue Artikel vor Aufnahme validieren
- [ ] Bei Wartungsseite, blockierter Anfrage oder Netzfehler alte Werte behalten und deutlich als veraltet markieren; nicht als aktuelle Daten ausgeben
- [ ] Benachrichtigung nach erfolgreichem Lauf oder bei längerem Fehlschlag anbieten
- [ ] Auf dem Galaxy S24 Ultra mit geschlossener App, Neustart, Energiesparmodus und wechselnder Verbindung prüfen

ChatGPT selbst kann die lokale App-Datenbank eines ausgeschalteten/geschlossenen Geräts nicht täglich befüllen. GitHub Actions kann einen zentralen Feed pflegen und den APK-Build verteilen, der Migros-Runner erhielt zuletzt jedoch wieder eine Wartungsseite. Für diesen Nur-ein-Gerät-Anwendungsfall ist daher der lokale Android-Auftrag der bevorzugte Weg; GitHub bleibt für Quellcode, APK und optionalen öffentlichen Feed.

## Android-Installation

- [ ] PWA-Installation über HTTPS auf dem Galaxy S24 Ultra prüfen
- [ ] Native APK/TWA-Alternative und Signierung bewerten, falls PWA-Installation nicht genügt
- [x] Lokale Android-App um Filialsuche, Bestandsfilter und Statussummen erweitern
- [x] Verfügbarkeit grün markieren und Filialen nach höchstem ausgelesenem Bestand sortieren
- [x] Restocks/Bestandsanstiege sowie geänderte Livepreise und mögliche Aktionen markieren
- [x] Direktlink zur aktuellen APK einrichten: https://github.com/gutschnertim-droid/gutschnertim-droid-pokemon-card-finder/releases/download/android-test-latest/app-debug.apk
- [x] Automatische Vier-Stunden-Aktualisierung ohne Button für geöffnete App ergänzen
- [x] Benachrichtigungen in der Android-App einzeln für Restock, steigenden Bestand, Preisänderungen und neue Rabattaktionen einstellbar machen
- [x] Eigenes Launcher-Symbol für die App hinzufügen
- [x] Bei Aktualisierungen neue passende Produkte auf Migros' öffentlicher Pokémon-Seite suchen, lokal in den Katalog aufnehmen und im selben Durchlauf prüfen
- [x] Android-Build erfolgreich; aktueller Testbuild als APK und ZIP im GitHub-Release veröffentlicht
- [x] Produktsuche bei Aktualisierung auf Migros-Pokémon-Markenübersicht und Kategorie Sammelkartenspiele ausweiten; neue Treffer lokal aufnehmen und mit den ausgewählten Filialen prüfen
- [ ] Tägliche Bestands-/Preisprüfung bei geschlossener App mit WorkManager umsetzen; der aktuelle Vier-Stunden-Lauf funktioniert nur, solange die App geöffnet ist
- [ ] Gezielte Abfrage für einen ausgewählten Artikel und eine Filiale ermöglichen
- [ ] Unterbrochene lokale Abfragen fortsetzen können
- [x] Offizielle Liste mit 669 normalen M/MM/MMM-Supermärkten einlesen und in 12 auswählbare Gebiete gruppieren (VOI und andere Formate ausgeschlossen)
- [x] Fribourg aus dem Schweizer Filialbestand vervollständigen: 28 passende Filialadressen statt zuvor 10; PLZ-Prüfung grenzt Waadt-Filialen wie Oron korrekt aus
- [x] Regionsfilter zeigt Bern, Fribourg, Zürich, Ostschweiz/St. Gallen, Waadt/Lausanne, Basel, Genf, Neuenburg, Aare, Zentralschweiz, Tessin und Wallis
- [ ] Kartenansicht eingebaut (OpenStreetMap, Bestandsfarben/-filter und Google-Wegbeschreibung); Koordinaten für alle 669 Filialen noch vervollständigen und Geräteansicht testen. Migros-Koordinaten werden bei Live-Abfragen lokal zwischengespeichert.
- [ ] Favoriten, Kaufnotizen und CSV-Verlauf ergänzen
- [ ] Signierte Android-Version mit verlässlichem Aktualisierungsweg bereitstellen
- [ ] Lokale Hintergrundabfrage nach Akkuoptimierung, App-Neustart und längerer Nichtnutzung auf dem Galaxy verifizieren (Android plant Zeitpunkt best effort)

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
- [x] Alle erfassten Schweizer Filialen in einer gemeinsamen Karte/Liste anzeigen; 12 Gebiete optional einzeln oder mehrfach filtern
- [x] Feed-Verarbeitung zeigt geprüfte Verfügbarkeit und Nichtverfügbarkeit; alle derzeit gelisteten Filialen werden abgedeckt
- [ ] Kartenradius mit der App-Liste synchronisieren und Standortdaten für alle Filialen bereitstellen; Google Maps zeigt derzeit eine zoombare Migros-Suche
- [x] Original-Migros-Link für eine manuelle aktuelle Prüfung integrieren
- [x] Frontend auf Region Bern erweitern; offizielle Suche „Bern“ mit Supermarktfilter ergab 27 Treffer in Stadt und Agglomeration
- [x] Gehostete App fragt `data/stock-latest.json` beim Öffnen und alle 15 Minuten ohne Cache ab
- [x] Fribourg-Filialen im landesweiten M/MM/MMM-Roster anhand Adresse/PLZ neu abgeglichen; Abfrage kann ausgewähltes Gebiet übernehmen
- [x] Bestände für sieben Pokémon-Artikel in allen 27 erfassten Berner Filialen prüfen und in den Feed übernehmen (11.10.2026).
- [x] UI-Collector erfasst alle Filialen aus `data/branches.json` für jeden Filialartikel; schreibt den Feed nur bei vollständiger Abdeckung (Playwright, ohne Login)
- [x] Täglichen GitHub-Actions-Ablauf und manuellen Start im veröffentlichten Repository eingerichtet
- [x] Fehlerdiagnose für den Collector ergänzt; GitHub-Runner protokolliert Migros-Seitentitel und sichtbaren Text bei Ladefehlern
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
- [x] Collector und täglicher Job implementiert. Der Collector befragt die öffentliche Produktseite ohne Login und schreibt bei Erfolg `regions.<region>.branches` sowie `regions.<region>.products.<id>.branches.<branchId>`.
- [ ] GitHub-Runner kann Migros zurzeit nicht zuverlässig erfassen: Zwei Läufe erhielten den Seitentitel `maintenance`; prüfen, ob ein neuer vollständiger Lauf wieder möglich ist. Den Feed bis dahin als veraltet kennzeichnen.
- [ ] Alte Daten bei erfolgreichem/fehlgeschlagenem Feed klar als veraltet kennzeichnen
- [ ] Fehlermeldungen und alte Daten sichtbar kennzeichnen
- [ ] Private Käufe optional mit Datum, Menge und Filiale erfassen
- [ ] Weitere Händler erst nach funktionierendem Pilot ergänzen
