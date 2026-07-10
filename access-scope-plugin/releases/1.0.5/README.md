# Release plugin Android Studio v1.0.5

## Installazione

Scarica e installa **solo** `AccessScope-1.0.5.zip`:

**Settings → Plugins → Install Plugin from Disk…** → riavvia Android Studio.

## Fix v1.0.5

- Risolto errore `Cannot run program "adb": No such file or directory`
- Il plugin usa ora il percorso completo di `adb` (da SDK Android / `local.properties`)
- Non serve avere `adb` nel PATH di sistema

## Requisiti

- Android SDK con **platform-tools** installato (di solito già presente con Android Studio)
- Nel progetto Android, `local.properties` con `sdk.dir=...` (creato automaticamente da Android Studio)

## VS Code / Cursor

Usa `releases/1.0.1/AccessScope-1.0.1.vsix`.
