# AccessScope IDE Plugins

Wrapper IDE per [AccessScope](https://github.com/xdrake96p/AccessScope): installa/aggiorna l'APK sul device scelto, apre l'app e recupera i risultati JSON a fine scansione.

## Componenti

| Modulo | Descrizione |
|--------|-------------|
| `cli/` | Core condiviso (`access-scope-cli`) — device, install, launch, fetch-results |
| `vscode-extension/` | Estensione VS Code / Cursor |
| `android-studio-plugin/` | Plugin IntelliJ/Android Studio (Tool Window) |

## Prerequisiti

1. **Android SDK platform-tools** (`adb` in PATH o in `~/Library/Android/sdk/platform-tools`)
2. **Java 17+** (consigliato: JBR di Android Studio)
3. Device/emulatore Android con debug USB abilitato
4. **Setup una tantum su device:**
   - Servizio accessibilità AccessScope abilitato
   - Permesso overlay concesso

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd access-scope-plugin
./gradlew :cli:fatJar
./gradlew :android-studio-plugin:buildPlugin
```

Output:
- CLI: `cli/build/libs/cli-1.0.0-all.jar`
- Plugin AS: `android-studio-plugin/build/distributions/AccessScope-1.0.1.zip`

### VS Code extension

```bash
cd vscode-extension
npm install
npm run compile
```

Installare da VS Code: **Extensions > Install from VSIX** oppure aprire la cartella `vscode-extension` in Development Host (`F5`).

## CLI — comandi

```bash
java -jar cli/build/libs/cli-1.0.0-all.jar devices list
java -jar cli/build/libs/cli-1.0.0-all.jar install --device <SERIAL>
java -jar cli/build/libs/cli-1.0.0-all.jar launch --device <SERIAL>
java -jar cli/build/libs/cli-1.0.0-all.jar setup-check --device <SERIAL>
java -jar cli/build/libs/cli-1.0.0-all.jar fetch-results --device <SERIAL> --package com.example.targetapp
java -jar cli/build/libs/cli-1.0.0-all.jar fetch-results --device <SERIAL> --package com.example.targetapp --wait --timeout 30m
```

Variabile ambiente opzionale: `ACCESS_SCOPE_DEVICE`, `GITHUB_TOKEN` (rate limit GitHub API).

## Aggiornamenti trasparenti

Il CLI scarica l'ultima release da GitHub (`xdrake96p/AccessScope`):
1. Legge `release-manifest.json` (versionCode, sha256)
2. Confronta con APK installato sul device
3. Installa automaticamente se esiste una versione più recente

Le regole WCAG sono incluse nell'APK: un aggiornamento regole = nuova release APK.

## Bridge API (app AccessScope)

Provider esposto per integrazione plugin:

| URI | Descrizione |
|-----|-------------|
| `content://dev.accessscope.scanner.results/status` | Stato app/scansione |
| `content://dev.accessscope.scanner.results/latest?package={pkg}` | Ultima sessione JSON |
| `content://dev.accessscope.scanner.results/session/{id}` | Sessione per ID |

Test manuale:

```bash
adb shell content query --uri content://dev.accessscope.scanner.results/status
```

Broadcast locale a fine scansione: `dev.accessscope.scanner.SCAN_COMPLETE`

## Flusso utente

1. Seleziona device (IDE o CLI)
2. **Install and Launch** — scarica/aggiorna APK e apre AccessScope
3. Nell'app: seleziona app target → **Avvia scansione** → naviga manualmente o con Maestro
4. **STOP** nell'overlay AccessScope
5. **Fetch Results** nel plugin — JSON + report Webview (VS Code) o output Tool Window (Android Studio)

## Configurazione VS Code

```json
{
  "accessScope.githubRepo": "xdrake96p/AccessScope",
  "accessScope.defaultDevice": "emulator-5554",
  "accessScope.targetPackage": "com.example.targetapp",
  "accessScope.autoUpdate": true,
  "accessScope.javaPath": "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"
}
```

## Android Studio

1. Installa plugin da `android-studio-plugin/build/distributions/AccessScope-*.zip`
2. Apri **View > Tool Windows > AccessScope**
3. Il package target viene rilevato da `app/build.gradle` (`applicationId` + `applicationIdSuffix`)

## Release GitHub (maintainer)

Flusso Git (vedi `.cursor/rules/git-release-workflow.mdc`):

1. Completare sviluppo su `develop`
2. `git checkout main && git merge develop && git push origin main`
3. Tag su **main**: `git tag -a v1.3.0 -m "..." && git push origin v1.3.0`
4. Tornare su `develop` per il ciclo successivo

Il tag `v*` su `main` attiva `.github/workflows/release.yml` che pubblica:

- `access-scope-{version}.apk`
- `release-manifest.json` (versionCode, sha256)
- `AccessScope-cli.jar`
- `AccessScope-1.0.1.zip`
- `AccessScope-1.0.1.vsix`

Artefatti plugin in `access-scope-plugin/releases/1.0.1/`.

## Troubleshooting

| Problema | Soluzione |
|----------|-----------|
| `adb not found` | Aggiungi `platform-tools` al PATH |
| `content query` fallisce | Fallback CLI via `run-as` (APK debug); verifica app installata |
| Setup incompleto | Esegui `setup-check`, abilita a11y e overlay nelle impostazioni Android |
| Gradle/Java 25 | Usa `JAVA_HOME` del JBR di Android Studio (21) |
