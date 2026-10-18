## Radiuskarte: Direktzugang und zuverlässiger Umkreisfilter

Die Karte wurde im Android-Code nicht angezeigt, weil der Dialog nie mit `show()` geöffnet wurde. Jetzt öffnet die neue grosse Schaltfläche **„🗺 Radiuskarte öffnen · Filialen im Umkreis wählen“** auf der Migros-Hauptseite die Karte direkt. Noch fehlende Koordinaten (im Ausgangsbestand 659 der 669 Filialen) werden für bis zu 80 ausgewählte Standorte je Kartenöffnung im Hintergrund gesucht. Eine Anzeige nennt die Koordinatenabdeckung. Filialen ohne Koordinaten werden **nicht** automatisch als Teil des gewählten Radius behandelt; der Nutzer erhält eine Warnung bei unvollständiger Abdeckung. Für grosse Gebiete ist vor dem Öffnen ein Kantonsfilter sinnvoll. Es stehen 2/3/5/10/15/20/30/50 km zur Auswahl; die zuletzt verwendete Kartenposition wird gespeichert.

## Backup und Wiederherstellung (ab dem nächsten Android-Build)

Unterhalb von **„7-Tage-Statistik & Änderungsverlauf“** befinden sich **„Backup herunterladen · Migros + Coop“** und **„Backup hochladen / wiederherstellen“**. Der Android-Dateidialog exportiert eine lokale JSON-Datei, die beide Händler enthält. Gesichert werden alle App-Einstellungen und lokalen Datensätze einschliesslich Produkt-/Filialbestände, Zeitstempel, Preise, Messpunkte, Änderungsereignisse, entdeckte Artikel, eigene Regionen und Filialauswahl. Der Import prüft Format und Werte vor dem Speichern, führt vorhandene Bestands- und Verlaufsdaten zusammen und vermeidet doppelte Messpunkte. Für beide Händler bleiben neuere bereits vorhandene Filialdaten vorrangig. Die 7-Tage-Grafik und die 7-Tage-Liste zeigen weiter nur die jüngsten sieben Tage; ältere erfasste Migros-Messungen werden ab dieser Version nicht mehr automatisch gelöscht und bleiben im Backup.

**Wichtig:** Vor dem Entfernen einer alten APK die neue Version als Android-Update mit unveränderter Signatur installieren und zuerst ein Backup exportieren. Daten, die bereits durch Deinstallation der vorherigen APK gelöscht wurden, können nicht nachträglich gesichert werden. Die Funktion benötigt keinen Server und sendet keine Backup-Daten an GitHub. Nach einem Import werden die Daten in der App neu geladen.

## Coop-Erweiterung (ab dem nächsten Android-Build nach v1.2.57)

Die Migros-Hauptansicht enthält jetzt eine Taste **„Coop Pokémon-Karten und Filialverfügbarkeit“**. Diese öffnet den unabhängigen `CoopActivity`-Bereich. Dort sind drei mit Coop-Produktlinks belegte Pokémon-Artikel vorerfasst. Weitere Produkte lassen sich über die (inoffizielle und nicht garantierte) öffentliche Coop-App-Schnittstelle suchen oder mit Artikelnummer und Link hinzufügen.

Die Abfrage sucht Coop-Filialen innerhalb eines gewählten Radius (2 bis 100 km) um einen auswählbaren Mittelpunkt. Voreingestellte Orte für alle 26 Kantone sind **Suchmittelpunkte, keine vollständigen Kantons-Filiallisten**. Der Mittelpunkt lässt sich auf der Radiuskarte versetzen. Nach der ersten erfolgreichen Abfrage können einzelne erkannte Filialen angehakt werden. Eigene Produktauswahl, letzter Datenstand und Filialauswahl werden lokal auf dem Gerät gespeichert, getrennt von Migros.

Die Coop-Schnittstelle liefert laut öffentlich dokumentierten Drittanbieter-Adaptern eine Liste der Filialen, die einen Artikel als verfügbar führen. Deshalb zeigt die App **„Verfügbar gemeldet“** oder **„Keine positive Meldung / unklar“**, niemals erfundene Stückzahlen oder eine aus fehlenden Treffern abgeleitete definitive Ausverkauft-Meldung. Benachrichtigungen werden nur bei einem späteren Übergang von „nicht gemeldet“ zu „verfügbar gemeldet“ ausgelöst; dies ist eine **neu erkannte positive Verfügbarkeitsmeldung**, kein gesicherter Mengen-Restock. Die automatische Coop-Abfrage kann optional alle vier Stunden nur bei geöffneter Coop-Ansicht laufen; Hintergrund-Push bei geschlossener App ist damit **nicht** umgesetzt.

Die Abfrage nutzt read-only GET auf `https://www.coop.ch/rest/v2/coopathome`. Diese Schnittstelle ist nicht als stabile, offiziell autorisierte öffentliche API bestätigt und kann mit HTTP 403/429/503 blockieren. Bei Problemen bleiben gespeicherte Daten erhalten; es werden keine Falschbestände erzeugt. Vor einer intensiven oder produktiven Abfrage sind Coops Nutzungsvorgaben und Zugriffserlaubnisse zu klären. Kein Umgehen von Captchas oder Zugangssperren.

# Pokémon Card Finder – lokaler Android-Live-Test

Diese native App fragt Pokémon-TCG-Artikel auf den Migros-Produktseiten direkt auf dem Android-Gerät ab. Sie durchsucht bei jeder Aktualisierung zusätzlich die öffentliche Pokémon-Produktseite nach neuen Karten-/TCG-Artikeln, ergänzt erkannte Artikel lokal zum Katalog und prüft sie im selben Lauf in den Filialen aus `app/src/main/assets/branches.json`. Die Android-Version enthält derzeit 669 Filialen aus 12 Migros-Regionen; die Filialauswahl ist nach einzelnen Kantonen möglich. Die Kantonszuordnung basiert auf der primären PLZ-Ortschaft und kann bei Postleitzahlen an Kantonsgrenzen abweichen. Anfrage und letzte Anzeige laufen lokal; die App benötigt weder GitHub-Feed noch Migros-Login. Die automatische Erkennung kann Produkte übersehen, die Migros auf der Pokémon-Seite nicht öffentlich rendert.

Direkt unter der Regionsauswahl auf der Hauptseite befindet sich **„Filialen auswählen / Radiuskarte“**. Dort lassen sich einzelne Migros-Filialen anhaken oder der Mittelpunkt und Umkreis auf der Karte bestimmen. Die Abfrage lässt sich zusätzlich auf einzelne Filialen innerhalb der gewählten Kantone begrenzen. Über „Abfrage-Filialen“ können Filialen angehakt oder auf einer Karte ein Mittelpunkt mit Radius von 2, 3, 5, 10, 15, 20, 30 oder 50 km gewählt werden. Fehlende Koordinaten werden auf dem Gerät gesucht und gespeichert; Filialen ohne auffindbare Koordinaten bleiben vorsichtshalber in der Abfrage. Produkt- und Filialauswahl gelten auch für automatische Abfragen. Eigene Regionen lassen sich aus frei gewählten Filialen zusammenstellen. Bestandsänderungen zeigen für 24 Stunden die Differenz in Stück, Steigerungen blau und Rückgänge rot. Die App speichert ab jetzt Bestands-Messpunkte der letzten sieben Tage und zeigt sie als Grafik sowie die erkannten Änderungen als Verlauf. Diese Verlaufsdaten bleiben lokal auf dem Gerät; frühere Messungen lassen sich nicht nachträglich ergänzen.

## Versionsnummern und fehlende Pokémon-Produkte

Jeder erfolgreiche GitHub-Actions-APK-Build erhält automatisch eine steigende Nummer im Format `v1.2.<GitHub-Buildnummer>`. Die angezeigte **App-Version** steht direkt unter dem Titel der App. Im GitHub-Release sind sowohl die aktuellste APK unter `android-test-latest` als auch die einzelnen nummerierten APKs (`v1.2.N`) verfügbar. Androids interner `versionCode` steigt analog, damit neuere Builds als Update erkennbar sind.

Der Startkatalog enthält nun zusätzlich den Pokémon Checklane Blister (Migros 744649500000) und den Premium Checklane Blister (748680400000). Diese Artikel haben noch keinen geprüften Live-Filialbestand. Für künftig fehlende Produkte gibt es den Button **„Fehlendes Pokémon-Produkt hinzufügen“**, wo ein Migros-Produktlink oder eine 12-stellige Artikelnummer und optional ein Name eingetragen werden können. Die automatische Produkterkennung auf Migros wurde um weitere Kartenprodukttypen erweitert.

## Neu erfasste Produkte (17.10.2026)

Zum bereits vorhandenen Katalog wurden vier zusätzliche Produkte mit geprüfter Migros-Artikelnummer ergänzt:

- Three Pack: `746646500000`
- Collectors Album: `748639000000`
- Spring Summer Fall ex Tin: `748417100000`
- ex Box: `748414000000`

Zusammen mit den bisherigen neun Artikeln enthält der Startkatalog jetzt **13 eindeutig nummerierte Produkte**. Bereits lokal erkannte Produkte werden über dieselbe Artikelnummer abgeglichen und nicht ein zweites Mal eingefügt. Ein GitHub-Actions-Test verweigert den APK-Build, falls der Katalog doppelte IDs oder falsche Produktlinks enthält. Die Bestände der neuen Produkte sind bis zur Live-Abfrage **ungeprüft**, nicht „nicht auf Lager“.

**Battle Deck** bleibt vorerst ausgenommen: Ein allgemeiner Migros-Ratgeber erklärt Battle Decks, aber für ein konkretes Migros-Filialprodukt mit dieser Bezeichnung konnte keine zugehörige Artikelnummer verifiziert werden. Keine erfundene Produktnummer aufnehmen.

## Updates und Signaturschlüssel

Damit Android eine neue APK als Update annimmt und die lokalen App-Daten behält, müssen alle APKs mit demselben privaten Signaturschlüssel signiert sein. Für GitHub Actions sind dafür die Repository-Secrets `ANDROID_KEYSTORE_BASE64` (Base64 des privaten PKCS12-Keystores) und `ANDROID_KEYSTORE_PASSWORD` einzurichten. Der Keystore darf nicht ins Repository eingecheckt werden. Ohne diese Secrets baut der Workflow weiterhin eine Debug-APK; diese kann wegen der wechselnden Runner-Signatur eine Neuinstallation erfordern. Nach Einrichtung baut der Workflow eine stabile Release-APK. Beim Wechsel von einer bereits installierten APK mit anderer Signatur ist einmalig eine Neuinstallation nötig; danach sind Updates mit Erhalt der lokalen Daten möglich.

## APK herunterladen oder selbst bauen

Der Repository-Workflow baut nach Änderungen am Ordner `android-local/` eine Debug-APK und ein ZIP mit dieser APK. Downloads: [ZIP für Android](https://github.com/gutschnertim-droid/gutschnertim-droid-pokemon-card-finder/releases/download/android-test-latest/app-debug.zip) · [APK direkt](https://github.com/gutschnertim-droid/gutschnertim-droid-pokemon-card-finder/releases/download/android-test-latest/app-debug.apk). **Bestandsanfragen und Ergebnisanzeige laufen auf dem Handy direkt bei Migros.** Alternativ benötigt ein lokaler Bau Android Studio (JDK 17, Android SDK 35): Ordner in Android Studio öffnen und **Build → Build APK(s)** wählen. Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Auf dem Android-Gerät muss die Installation von APK-Dateien erlaubt werden.

## Test

App öffnen, gewünschten Artikel im Auswahlfeld wählen und **Alle Artikel in allen Filialen abfragen** antippen. Die App sucht neue Artikel und fragt dann alle Katalogprodukte nacheinander ab, jeweils mit den Filialen im Verfügbarkeitsdialog. Erfasste Ergebnisse werden lokal gespeichert und erscheinen fortlaufend in der Liste. Unlesbare oder fehlende Antworten stehen ausdrücklich als „Nicht eindeutig geprüft“ und werden nicht als „Nicht auf Lager“ ausgegeben. Bei einer Migros-Wartungs- oder Sperrseite wird der Lauf gestoppt; bereits erfasste Ergebnisse bleiben lokal gespeichert.

Die Filialliste kann nach Filialname oder Ort durchsucht und nach Verfügbarkeit, Nichtverfügbarkeit, unklarem Status oder noch nicht erfolgter Prüfung gefiltert werden. Eine Übersicht zählt die vier Statusgruppen für den ausgewählten Artikel. Weitere geplante Erweiterungen stehen in [TODO.md](TODO.md).

Verfügbare Filialen werden mit einem grünen Punkt hervorgehoben. „Höchster Bestand“ sortiert verfügbare Filialen nach der ausgelesenen Ca.-Menge. Nach jedem erneuten Live-Lauf markiert die App Restocks sowie erkannte Mengenanstiege/-rückgänge. Preise werden während des Produktseitenaufrufs aus sichtbaren oder strukturierten Preisfeldern gelesen und mit dem vorherigen lokalen Preis verglichen; ein tieferer Preis wird als möglicher Rabatt markiert. Preis- und Bestandsvergleiche beginnen mit dem nächsten Live-Lauf nach der Installation. Preisalarme funktionieren nur, wenn Migros den aktuellen Preis auf der Produktseite lesbar ausgibt.

Die Produktbilder werden von der Migros-Bildquelle geladen. Android kann Hintergrundaufgaben verzögern; die Schaltfläche startet die Abfrage verlässlich auf Wunsch. Bei 259 Kombinationen kann ein Lauf länger dauern. Die App sollte dabei geöffnet bleiben.

Die automatische Aktualisierung ist standardmässig eingeschaltet und kann in der App ausgeschaltet werden. Beim ersten Öffnen startet sie selbstständig; danach startet sie alle vier Stunden, solange die App geöffnet ist. Wenn die App währenddessen geschlossen oder im Hintergrund war, startet eine fällige Abfrage beim nächsten Öffnen. Die Migros-Erfassung nutzt die App-WebView und läuft deshalb nicht zuverlässig bei vollständig geschlossener App.

Unter **Benachrichtigungen einstellen** lassen sich Wiederverfügbarkeit, steigender oder sinkender Filialbestand sowie Preisänderungen getrennt aktivieren. Bestandsmeldungen können auf einzelne Produkte begrenzt werden. Die Testschaltfläche prüft eine lokale Android-Benachrichtigung. Mehrere Produkt-/Filialänderungen erhalten getrennte Notification-Tags und überschreiben einander nicht. Beim Antippen einer Bestandsmeldung öffnet sich das Produkt in der App. Android fragt um System-Benachrichtigungserlaubnis. Der Test löst keinen FCM-Push aus; eine Überwachung bei geschlossener App erfordert weiterhin einen Server, eine zuverlässige Migros-Datenquelle und eine Push-Konfiguration.
