# GitHub-Veröffentlichung

Das Projekt wurde im vom Nutzer bereitgestellten öffentlichen Repository veröffentlicht:

- Repository: https://github.com/gutschnertim-droid/gutschnertim-droid-pokemon-card-finder
- Branch: `main`
- Projekt-Commit: `2d4959caf1d475acef1294c71387ab701a264956`

Der ChatGPT Codex Connector ist auf dem GitHub-Konto installiert und hat Zugriff auf das Repository. Die Dateien, der Migros-Bestandsfeed und der Workflow sind über die GitHub-API überprüft.

## Automatische Erfassung

`.github/workflows/collect-migros-stock.yml` ist täglich auf 01:15 UTC eingestellt und kann in GitHub Actions manuell gestartet werden. Der Collector fragt alle Filialen in `data/branches.json` für jeden Filialartikel ab und schreibt den Feed nur bei vollständiger Erfassung.

Der erste erfolgreiche Actions-Lauf muss noch geprüft werden. Ebenfalls offen ist, ob neue Feeds automatisch in die bisherige private App-Veröffentlichung übernommen werden. Die App kann den Feed aus dem GitHub-Repository nicht automatisch abrufen, solange diese Verbindung nicht eingerichtet ist.
