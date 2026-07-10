# AccessScope — Manuale utente e tecnico

**Versione documento:** 1.0.8 (plugin IDE) · app AccessScope 1.3.0  
**Repository:** [github.com/xdrake96p/AccessScope](https://github.com/xdrake96p/AccessScope)  
**Ultimo aggiornamento:** 10 luglio 2026

---

## Indice

1. [Panoramica](#1-panoramica)
2. [A chi serve](#2-a-chi-serve)
3. [I tre componenti](#3-i-tre-componenti)
4. [L'app AccessScope (sul telefono)](#4-lapp-accessscope-sul-telefono)
5. [I plugin IDE (Android Studio e VS Code)](#5-i-plugin-ide-android-studio-e-vs-code)
6. [Cosa fa e cosa non fa il sistema](#6-cosa-fa-e-cosa-non-fa-il-sistema)
7. [Requisiti](#7-requisiti)
8. [Installazione](#8-installazione)
9. [Primo utilizzo (setup device)](#9-primo-utilizzo-setup-device)
10. [Flusso di lavoro quotidiano](#10-flusso-di-lavoro-quotidiano)
11. [Aggiornamenti](#11-aggiornamenti)
12. [Risoluzione problemi](#12-risoluzione-problemi)
13. [Riferimento tecnico](#13-riferimento-tecnico)

---

## 1. Panoramica

**AccessScope** è un sistema per verificare l'**accessibilità** delle app Android (conformità **WCAG**): contrasto colori, etichette per screen reader, dimensioni touch, titoli, e altro.

Il sistema è composto da:

| Componente | Dove gira | Ruolo |
|------------|-----------|--------|
| **App AccessScope** | Telefono o emulatore Android | Esegue la scansione reale sull'app da testare |
| **Plugin Android Studio** | PC/Mac, dentro Android Studio | Collega l'IDE al telefono: installa app, apre, scarica risultati |
| **Estensione VS Code / Cursor** | PC/Mac, dentro l'editor | Stesse funzioni del plugin Android Studio |

I due plugin (**Android Studio** e **VS Code**) sono **gemelli**: stessa versione, stessi comandi, stesso motore interno. Scegli l'IDE che usi di solito.

---

## 2. A chi serve

| Profilo | Uso tipico |
|---------|------------|
| **Sviluppatore Android** | Scansione durante lo sviluppo di una feature, prima del merge |
| **QA / Test** | Verifica accessibilità su build di test |
| **Accessibility champion** | Report strutturato (JSON/PDF) da condividere col team |
| **DevOps / CI** | Integrazione futura via CLI (`access-scope-cli`) |

Non serve essere esperti di accessibilità per **avviare** una scansione; il report indica cosa correggere. Per **interpretare** le violazioni WCAG può servire supporto del team a11y.

---

## 3. I tre componenti

```
┌─────────────────┐     adb / USB      ┌──────────────────┐
│  IDE (plugin)   │ ◄────────────────► │  Device Android  │
│  AS o VS Code   │                    │  + App AccessScope│
└────────┬────────┘                    └────────┬─────────┘
         │                                    │
         │  install / launch / fetch          │  scansione live
         ▼                                    ▼
   access-scope-cli                    App da testare
   (motore condiviso)                  (es. it.nexi.bff)
```

- Il **plugin** non scansiona da solo: prepara il terreno e **recupera i risultati**.
- La **scansione** la controlli **tu** nell'app AccessScope sul telefono (navigazione manuale nelle schermate).

---

## 4. L'app AccessScope (sul telefono)

### Cosa fa

1. Chiede quali app analizzare (es. la tua app Nexi in debug/release).
2. Mostra un **overlay** durante la scansione.
3. Analizza ogni schermata mentre ci navighi (contrasto, label, touch target, heading…).
4. Alla fine genera un **report** (punteggio, violazioni per gravità, PDF opzionale).
5. Espone i risultati ai plugin IDE tramite un **bridge** interno (ContentProvider).

### Cosa devi fare tu sull'app

1. Apri AccessScope sul device.
2. Seleziona l'app target (o usa quella già rilevata dal plugin).
3. Tocca **Avvia scansione**.
4. **Naviga** nell'app da testare (apri menu, form, dialoghi…).
5. Tocca **STOP** nell'overlay quando hai finito.

### Permessi obbligatori (una tantum)

| Permesso | Dove si abilita |
|----------|-----------------|
| **Accessibilità** | Impostazioni → Accessibilità → AccessScope → ON |
| **Overlay** | Impostazioni → App → AccessScope → “Mostra sopra altre app” |

Senza questi due permessi la scansione non parte. Il plugin può verificarli con **Setup Check**.

---

## 5. I plugin IDE (Android Studio e VS Code)

### Cosa fanno

| Azione | Descrizione semplice |
|--------|----------------------|
| **Refresh / Select Device** | Mostra telefoni ed emulatori collegati al PC |
| **Install / Update** | Scarica l'ultima app AccessScope da GitHub e la installa sul device |
| **Launch** | Apre AccessScope sul device selezionato |
| **Fetch Results** | Scarica l'ultimo report JSON della scansione |
| **Setup Check** | Controlla: app installata, accessibilità ON, overlay ON |
| **Clear log** | Pulisce l'area messaggi del pannello |
| **Plugin update** | Cerca una versione più nuova del plugin e la installa |

Il plugin rileva automaticamente il **package** dell'app che stai sviluppando (es. `it.nexi.bff`) dal file Gradle del progetto aperto.

### Dove trovare il pannello

**Android Studio**

- Icona **AccessScope** nella toolbar (in alto)
- **Tools → AccessScope**
- **Run → Open AccessScope on Device** (scorciatoia install + launch)

**VS Code / Cursor**

- Icona **AccessScope** nella **barra laterale sinistra** (Activity Bar)
- Command Palette (`⇧⌘P` / `Ctrl+Shift+P`) → `AccessScope: Open Panel`

### Cosa non fanno

- Non navigano nell'app al posto tuo
- Non premono “Avvia scansione” automaticamente
- Non sostituiscono Maestro o altri tool di UI test (possono convivere)

---

## 6. Cosa fa e cosa non fa il sistema

| ✅ Sì | ❌ No |
|------|------|
| Trova problemi WCAG su device reale | Sostituisce un audit manuale completo con TalkBack |
| Report JSON strutturato per il team | Garantisce conformità legale senza revisione umana |
| Aggiornamento APK AccessScope da GitHub | Modifica il codice della tua app |
| Reinstall se firma APK diversa (debug vs release) | Funziona senza cavo USB / senza `adb` |

---

## 7. Requisiti

### Hardware e software

- **PC o Mac** con Android Studio **oppure** VS Code/Cursor
- **Telefono Android** con debug USB **oppure** **emulatore** avviato
- **Android SDK** con `platform-tools` (`adb`) — di solito già installato con Android Studio
- **Java 17+** (incluso nel JBR di Android Studio)

### Sul device

- Debug USB abilitato (telefono fisico)
- AccessScope installata (il plugin lo fa per te)
- Accessibilità + overlay abilitati per AccessScope

### Rete

- Connessione internet per scaricare APK e aggiornamenti plugin (GitHub)

---

## 8. Installazione

### Release attuali (GitHub `develop`)

| Componente | File | Link diretto |
|------------|------|--------------|
| Plugin Android Studio 1.0.8 | `AccessScope-1.0.8.zip` | [Scarica ZIP](https://github.com/xdrake96p/AccessScope/raw/develop/access-scope-plugin/releases/1.0.8/AccessScope-1.0.8.zip) |
| Estensione VS Code 1.0.8 | `AccessScope-1.0.8.vsix` | [Scarica VSIX](https://github.com/xdrake96p/AccessScope/raw/develop/access-scope-plugin/releases/1.0.8/AccessScope-1.0.8.vsix) |
| App AccessScope | (via plugin **Install / Update**) | Scaricata automaticamente dall'ultima release GitHub |

Cartella release: [access-scope-plugin/releases/1.0.8](https://github.com/xdrake96p/AccessScope/tree/develop/access-scope-plugin/releases/1.0.8)

### Android Studio — installare il plugin

1. **Settings** (macOS: **Android Studio → Settings**)
2. **Plugins** → ingranaggio → **Install Plugin from Disk…**
3. Seleziona `AccessScope-1.0.8.zip`
4. **Riavvia** Android Studio
5. Apri un progetto Android e clicca l'icona AccessScope

**Compatibilità:** Android Studio 2022.2 (Flamingo) e versioni successive.

### VS Code / Cursor — installare l'estensione

1. Pannello **Extensions** (`⇧⌘X`)
2. Menu **⋯** → **Install from VSIX…**
3. Seleziona `AccessScope-1.0.8.vsix`
4. **Developer: Reload Window**
5. Clicca l'icona AccessScope nella barra sinistra

**Compatibilità:** VS Code 1.85+ / Cursor recente.

---

## 9. Primo utilizzo (setup device)

Segui questi passi **una volta** per ogni device nuovo:

1. Collega il telefono (o avvia l'emulatore).
2. Sul telefono: accetta **Consenti debug USB** se richiesto.
3. Nel plugin: **Refresh / Select Device** → scegli il device.
4. **Install / Update** → attendi “installazione completata”.
5. **Launch** → si apre AccessScope sul telefono.
6. Sul telefono:
   - Abilita **AccessScope** in **Impostazioni → Accessibilità**
   - Concedi **permesso overlay**
7. Nel plugin: **Setup Check** → deve risultare tutto OK.

Se **Setup Check** fallisce, leggi il suggerimento (`hint`) nel messaggio o nell'output log.

---

## 10. Flusso di lavoro quotidiano

### Passi consigliati

```
1. Refresh Devices
2. Install / Update          ← allinea APK AccessScope
3. Launch                    ← apre app sul device
4. [Sul telefono] Avvia scansione e naviga nell'app
5. [Sul telefono] STOP overlay
6. Fetch Results             ← JSON nel plugin / webview VS Code
```

### Dove vedere i risultati

- **Android Studio:** area testo nel pannello AccessScope (JSON) + eventuali dialog
- **VS Code:** pannello **AccessScope Results** (tabella HTML) + canale **Output → AccessScope**

### Target package

Il plugin legge `applicationId` da `app/build.gradle` del progetto aperto.  
Esempio Nexi: `it.nexi.bff` (con eventuali suffix Gradle).

In VS Code puoi forzare un package in Settings → `accessScope.targetPackage`.

---

## 11. Aggiornamenti

### App AccessScope (sul telefono)

- Pulsante **Install / Update** nel plugin: scarica l'ultima release da GitHub se più nuova.

### Plugin IDE (Android Studio / VS Code)

| IDE | Come aggiornare |
|-----|-----------------|
| **Android Studio** | **Plugin update** nel pannello → riavvio IDE |
| **VS Code** | **Plugin update** nel pannello **oppure** reinstalla VSIX da GitHub |

Le versioni **Android Studio** e **VS Code** restano **allineate** (es. entrambe 1.0.8).

### Dopo reinstallazione app con firma diversa

Se passi da build debug a release (o viceversa), il plugin **disinstalla e reinstalla** AccessScope.  
**Riabilita** accessibilità e overlay sul device.

---

## 12. Risoluzione problemi

| Messaggio / sintomo | Causa probabile | Cosa fare |
|---------------------|-----------------|-----------|
| `adb not found` | SDK non trovato | Apri progetto Android (per `local.properties`) o imposta `ANDROID_SDK_ROOT` |
| `Device unauthorized` | Debug USB non accettato | Sblocca telefono, accetta prompt “Consenti debug USB” |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Firma APK diversa | **Install / Update** di nuovo (reinstall automatico in v1.0.8+) |
| `Fetch Results` errore JSON | Nessuna scansione completata | Esegui scansione nell'app e premi STOP prima di fetch |
| Setup Check: accessibilità OFF | Permesso non dato | Impostazioni → Accessibilità → AccessScope ON |
| Setup Check: overlay OFF | Permesso non dato | Impostazioni → App → AccessScope → overlay |
| Plugin update: rate limit GitHub | Troppe richieste API | Riprova dopo qualche minuto o imposta `GITHUB_TOKEN` |
| VS Code: nessuna icona | Estensione non ricaricata | Reinstalla VSIX e **Reload Window** |

### Log utili

- **Android Studio:** pannello AccessScope (in basso) + *Help → Show Log in Finder*
- **VS Code:** **View → Output** → canale **AccessScope**
- **Device:** `adb logcat -s AccessScopeBridge`

---

## 13. Riferimento tecnico

### Architettura plugin

```
Plugin UI (AS o VS Code)
    └── access-scope-cli.jar
            └── adb → device
                    └── App AccessScope (dev.accessscope.scanner)
                            └── ContentProvider risultati
```

Codice: `access-scope-plugin/cli/`, `android-studio-plugin/`, `vscode-extension/`.

### CLI — comandi manuali

```bash
export ACCESS_SCOPE_SDK_ROOT=~/Library/Android/sdk   # se adb non in PATH

java -jar AccessScope-cli.jar devices list
java -jar AccessScope-cli.jar install --device <SERIAL>
java -jar AccessScope-cli.jar launch --device <SERIAL>
java -jar AccessScope-cli.jar setup-check --device <SERIAL>
java -jar AccessScope-cli.jar fetch-results --device <SERIAL> --package it.nexi.bff
```

Variabili ambiente:

| Variabile | Uso |
|-----------|-----|
| `ACCESS_SCOPE_SDK_ROOT` | Percorso Android SDK |
| `ACCESS_SCOPE_DEVICE` | Serial device default |
| `ACCESS_SCOPE_ADB` | Percorso esplicito di `adb` |
| `GITHUB_TOKEN` | Evita rate limit API GitHub |

### Bridge API (app → plugin)

| URI | Contenuto |
|-----|-----------|
| `content://dev.accessscope.scanner.results/status` | Stato app, scanning, permessi |
| `content://dev.accessscope.scanner.results/latest?package={pkg}` | Ultima sessione JSON |
| `content://dev.accessscope.scanner.results/session/{id}` | Sessione per ID |

Test rapido:

```bash
adb shell content query --uri content://dev.accessscope.scanner.results/status
```

Broadcast a fine scansione: `dev.accessscope.scanner.SCAN_COMPLETE`  
Logcat: tag `AccessScopeBridge`

### Build da sorgente (sviluppatori)

```bash
# App Android
./gradlew assembleRelease

# Plugin
cd access-scope-plugin
./gradlew :cli:fatJar :android-studio-plugin:buildPlugin

# VS Code
cp cli/build/libs/cli-1.0.0-all.jar vscode-extension/bin/AccessScope-cli.jar
cd vscode-extension && npm install && npm run package
```

### Regole di sviluppo (team)

| Regola Cursor | Contenuto |
|---------------|-----------|
| `plugin-parity.mdc` | Parità obbligatoria AS ↔ VS Code |
| `plugin-release-workflow.mdc` | Release allineate, whats-new, build |
| `git-release-workflow.mdc` | develop → main → tag |
| `project-maintenance.mdc` | PROJECT.md, KDoc, manuale |

### Documentazione correlata

- `docs/PROJECT.md` — architettura app, cronologia tecnica
- `docs/PLUGIN_BRIDGE.md` — dettaglio bridge API
- `access-scope-plugin/README.md` — README sviluppatori plugin

---

## Glossario rapido

| Termine | Significato |
|---------|-------------|
| **WCAG** | Linee guida internazionali per accessibilità web/digital |
| **adb** | Android Debug Bridge — collegamento PC ↔ device |
| **APK** | Pacchetto installabile app Android |
| **Package / applicationId** | Identificativo univoco app (es. `it.nexi.bff`) |
| **Violazione** | Problema di accessibilità rilevato (con gravità) |
| **Sessione** | Una scansione completa (avvio → stop) |

---

*Per segnalazioni e release: [GitHub Issues](https://github.com/xdrake96p/AccessScope/issues)*
