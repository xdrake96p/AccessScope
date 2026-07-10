# AccessScope — Documento di progetto (living doc)

> **Aggiornare questo file** ad ogni iterazione significativa: nuove feature, fix di precisione, cambi UI, metriche benchmark, commit rilevanti.

**Repository:** [github.com/xdrake96p/AccessScope](https://github.com/xdrake96p/AccessScope)  
**Package:** `dev.accessscope.scanner`  
**Branch principale sviluppo:** `develop`  
**Branch release stabile:** `main`  
**Ultimo aggiornamento:** 10 luglio 2026 (v1.3.1 app · v1.0.8 plugin IDE)

**Manuale utente e tecnico:** [`docs/MANUALE_UTENTE.md`](MANUALE_UTENTE.md) — installazione, uso plugin AS/VS Code, troubleshooting.

---

## Convenzioni di sviluppo

A **fine di ogni task**, obbligatorio (regola Cursor: `.cursor/rules/project-maintenance.mdc`):

1. **Aggiornare questo file** (`docs/PROJECT.md`) — cronologia, architettura, benchmark, changelog.
2. **Aggiornare la KDoc** (JavaDoc Kotlin) su tutti i file `.kt` modificati o creati: blocco file, tipi pubblici, funzioni con `@param` / `@return`, descrizioni in italiano.

### Git e release

Flusso obbligatorio (regola Cursor: `.cursor/rules/git-release-workflow.mdc`):

1. Sviluppo su `develop`
2. Merge `develop` → `main`
3. Tag `v*` su `main` (es. `v1.3.0` per `versionName` 1.3.0)
4. Push `main` + tag → GitHub Release
5. Nuovo sviluppo sempre da `develop`

---

## TODO

Epic **v1.3.0** (completato):

- [x] **Dedupe rigorosa** — `ViolationDedupeRules`: chiavi viewId-first, bounds quantizzati 32dp; `CheckCollector.merge` usa max
- [x] **Cronologia sessioni** — `ScanHistoryStore` JSON, max 20 sessioni per app
- [x] **UI confronto** — `SessionComparisonCard`, pulsante «Cronologia scansioni», `ScanHistoryScreen`

---

## Cos'è AccessScope

AccessScope è un'app Android che monitora l'accessibilità di altre app mentre l'utente le usa. Analizza l'albero di accessibilità in tempo reale, misura contrasto WCAG via screenshot, simula TalkBack e genera un report PDF con problemi e controlli superati.

**Principio architetturale:** le regole del motore (`PrecisionRules`) sono **generiche** e valgono per qualsiasi app. I profili opzionali (`AppPrecisionProfiles`) aggiungono solo marker noti quando il codice sorgente è disponibile (es. benchmark Nexi).

---

## Architettura

```
Utente → HomeScreen (selezione app) → Avvia scan
    → AccessScopeAccessibilityService (eventi a11y + screenshot)
    → NodeAccessibilityAnalyzer + PrecisionRules + ScreenTitleResolver
    → ScanSessionRepository (violazioni dedupe via ViolationDedupeRules, check OK, schermate)
    → Stop → ScanHistoryStore (archivio) + PdfReportExporter + ReportScreen
    → ScanHistoryScreen (cronologia per app, max 20 sessioni)
```

| Modulo | Ruolo |
|--------|--------|
| `AccessScopeAccessibilityService` | Cattura eventi, debounce scan, multi-finestra, overlay |
| `NodeAccessibilityAnalyzer` | 37 tipi di violazione WCAG, contrasto, touch, label |
| `PrecisionRules` | Euristiche generiche anti-FP (drawer, home widget, carousel…) |
| `AppPrecisionProfiles` | Marker opzionali per app note (Nexi) |
| `ScreenTitleResolver` | Titolo schermata da topbar, heading, viewId |
| `CheckCollector` | Registra controlli superati per report |
| `ViolationDedupeRules` | Chiavi dedupe stabili in scroll (viewId, bounds quantizzati) |
| `ScanHistoryStore` | Persistenza JSON cronologia sessioni (20 per package) |
| `SessionComparisonHelper` | Confronto +N/−M risolti tra sessioni |
| `PdfReportExporter` | PDF con problemi dettagliati + sezione OK |
| `ScanSessionRepository` | Stato sessione live, dedupe in-memory, fingerprint schermate |

---

## Ambiti di scansione (`ScanScope`)

| Area | Contenuto |
|------|-----------|
| LABELS | Etichette, alt text, link |
| TOUCH | Target 48dp, spaziatura |
| COLOR | Contrasto testo/UI |
| TEXT | Dimensione, troncamento |
| FORMS | Input, errori, obbligatori |
| STRUCTURE | Heading, focus, tabelle |
| SCREEN_READER | TalkBack, live region |
| MEDIA_WEB | WebView |

---

## Cronologia sviluppo

### v1.3.1 — Fix API 34, firma release, CI unificata, feedback (10 luglio 2026)

- **Fix crash API 34:** permesso `OPEN_ACCESSIBILITY_DETAILS_SETTINGS` + `safeStartSettingsIntent` con fallback
- **Firma release:** `signingConfigs.release` da `keystore.properties`; CI decodifica secrets GitHub
- **CI unificata:** `release.yml` builda app + CLI + plugin AS/VSIX in pipeline; `minPluginVersion` dinamico nel manifest
- **PluginVersionChecker:** CLI blocca install/setup se plugin IDE troppo vecchio (`ACCESS_SCOPE_PLUGIN_VERSION`)
- **Feedback:** schermata Impostazioni → GitHub Issues precompilato (template `feedback.yml`)
- Test Robolectric: `PermissionHelperTest` (intent accessibilità API 32/33/34)

### v1.3.0 — Plugin IDE + bridge API (10 luglio 2026)

- `ScanResultProvider`: ContentProvider `dev.accessscope.scanner.results` per integrazione plugin
- Broadcast `SCAN_COMPLETE` + logcat `AccessScopeBridge`
- `access-scope-plugin`: CLI condiviso, estensione VS Code, plugin Android Studio
- GitHub Release workflow: APK + `release-manifest.json` + artefatti plugin
- Regola Git: `develop` → merge in `main` → tag su `main`

### v1.3.0 — Dedupe rigorosa + cronologia sessioni (6 luglio 2026)

- `ViolationDedupeRules`: dedupe viewId-first (ignora fingerprint in scroll), bounds quantizzati 32dp
- `CheckCollector.merge`: `passedCount` = max per chiave (non somma)
- `ScanHistoryStore`: archivio JSON `filesDir/scan_history/`, FIFO 20 sessioni per package
- UI: `SessionComparisonCard` (+N nuovi, −M risolti, delta punteggio), `ScanHistoryScreen`
- Test JVM: `ViolationDedupeRulesTest` (7 casi)

### v1.0.0 — Release iniziale (`b7273d5`)

- Motore accessibilità WCAG su `AccessibilityService`
- Export PDF per ambito
- UI Compose Material 3

### v1.1 — Affidabilità scan (`df9b07b` – `f1538d6`)

- Overlay STOP in scansione
- Auto-launch prima app selezionata
- Report in-app con raggruppamento per schermata
- App preferite, tutte le app installate
- Fix permessi e nav bar

### v1.2 — Precisione Nexi (`6ffcc90` – `247a6b2`)

- `ScreenTitleResolver` con titoli Nexi noti
- `PrecisionRules`: riduzione rumore scroll, custom action, drawer
- Benchmark su `it.nexi.bff` (7 flussi manuali)
- Baseline report: **150238** → 136 problemi (~32% precisione)

### v1.3 — Iterazione 5 (`8b0916b`)

- Fingerprint schermata, cache titolo
- Skip contrasto grafico home, heading carousel
- Recall `txt_data_*` con bounds espansi
- Target: bucket «Schermata» = 0

### v1.4 — Iterazioni 6–7 (`d9b344a`)

- Fix attribuzione «Ultimi insoluti» su home
- Skip `nav_*`, drawer-only root
- Skip widget home (`entrate_home`, chart)
- Fix UI pulsante «Avvia»

### v1.5 — Iterazione 8 (`6bb2798`)

- `AppPrecisionProfiles`: regole generiche multi-app
- Profilo Nexi opzionale isolato
- Skip off-screen, bounds anomali, list row overlap

### v1.2.2 — Prompt AI per fix accessibilità (6 luglio 2026)

- **`AiPromptBuilder`:** genera prompt markdown strutturato (contesto, WCAG, problemi per schermata, TalkBack, istruzioni output)
- **`CopyAiPromptButton`:** copia negli appunti sotto «Vedi report completo» (Home) e nel report dettagliato
- **`ClipboardHelper`:** utility clipboard Android

| Commit | Descrizione |
|--------|-------------|
| *pending* | v1.2.2 prompt AI |

### v1.2.1 — Polish UI pre-presentazione (6 luglio 2026)

- **Scroll:** `appListUiState` / `scanDashboardUiState` isolano lista e dashboard dal tick live della scansione
- **AppListRow:** callback stabili (`viewModel::toggleApp`), meno recomposition in scroll
- **AppIconAsync:** `produceState` + `AppIconCache.peek` per hit istantaneo in cache
- **Motion:** transizioni navigazione con spring Material (`navSpring`)
- **ScanDashboard:** `AccessScopeCard`, pulse più morbido (FastOutSlowIn)

| Commit | Descrizione |
|--------|-------------|
| *pending* | v1.2.1 polish UI presentazione |

### v1.2.3 — Scroll fluido e overlay trascinabile (6 luglio 2026)

- **Scroll home:** icone caricate in async (`AppIconAsync`), righe ottimizzate (`AppListRow`), debounce ricerca 120ms, `animateItem`, preload icone a batch
- **Overlay scansione:** card semi-trasparente con handle «Trascina»; LIVE/STOP spostabili; posizione persistita in `ScanSettingsStore`

| Commit | Descrizione |
|--------|-------------|
| *pending* | v1.2.3 scroll + overlay drag |

### v1.2.2 — Restyling UI premium Material You (6 luglio 2026)

**Design system rivisto (ciano / lavanda / viola, WCAG AAA):**
- Palette scura: sfondo `#1A1D24`, card `#252830`, gradiente header ciano → viola
- Palette chiara: sfondo `#F8F9FA`, card bianche sollevate, gradiente header ciano soft → lavanda
- `AccessScopeCard`: elevazione light / bordo luminoso dark
- `Shape.kt`: `CardShape`, `HeroShape`, `ControlShape`, `ChipShape`
- `AppSearchField`: barra ricerca Material 3 filled
- `HeroHeader` + `FeatureHighlights`: icone vettoriali, chip stato, layout bilanciato
- `PermissionsCard`: badge semaforo, progress bar, raggruppamento permessi con icone stato
- `HomeScreen`: layout adattivo orizzontale (≥720dp) — sinistra header+feature, centro permessi, destra selezione app; colonna singola su telefono
- `ScanActionBar`: CTA «Avvia scansione» prominente (56dp, titleMedium bold)

| Commit | Descrizione |
|--------|-------------|
| *pending* | v1.2.2 restyling UI premium |

### v1.2.1 — Scroll, auto-launch, debug live (6 luglio 2026)

- Lista app: LazyColumn dedicata, righe leggere (Switch al posto di FilterChip)
- Auto-launch: massimo **1** app (sostituzione automatica al cambio selezione)
- Banner Material `AppSelectionInfoBanner` (selezione libera vs limite auto-launch)
- Debug live: pannello in Impostazioni, icona occhio in Home, pulsante LIVE nell'overlay
- `LiveScanSnapshot` aggiornato ad ogni analisi del servizio a11y


**Design system WCAG AAA:**
- Palette chiaro: sfondo `#F8F9FA`, card `#FFFFFF`, testo `#111827`, brand ciano `#0891B2` / viola `#7C3AED`
- Palette scuro: sfondo `#1A1D24`, card `#252830`, testo `#F9FAFB`, accenti ciano `#22D3EE` / lavanda `#A78BFA`
- Semantici: errore `#DC2626`, warning `#D97706`, successo `#059669`
- Tipografia base 16sp, line-height 1.5, monospace per dati tecnici

**Impostazioni tema:**
- `AppThemeMode`: Chiaro / Scuro / Sistema
- `ThemePreferencesStore` (SharedPreferences) per persistenza
- `ThemeModeSelector` in Impostazioni con focus ring ad alto contrasto
- `AccessScopeTheme(themeMode)` applicato da `MainActivity`

| Commit | Descrizione |
|--------|-------------|
| *pending* | v1.2.0 tema chiaro/scuro + selettore impostazioni |

### v1.1.0 — Iterazioni 9–11 UI (6 luglio 2026)

**Motore (iter. 9–10):**
- `CheckCollector` + report dettagliato con controlli OK
- Precisione Nexi al 100% su 7 flussi (report `170543`)

**UI/UX (iter. 11):**
- Cache icone LRU (`AppIconCache`) — scroll lista app fluido
- Transizioni navigazione slide+fade
- Barra azioni animata (Avvia/Stop + indicatore Live)
- Panoramica schermate con OK e schermate pulite in verde
- `docs/PROJECT.md` — documento living del progetto

| Commit | Descrizione |
|--------|-------------|
| *questo commit* | Iter 9–10 motore + v1.1.0 UI fluida |

---

## Benchmark Nexi (`docs/reliability-benchmark.md`)

App di riferimento: **Nexi** (`it.nexi.bff`) — abbiamo il sorgente per ground truth N01–N20.

### 7 flussi manuali scope

1. Home  
2. Autorizza distinte  
3. Paga effetti  
4. Disposizioni online  
5. Disposizioni istantanee  
6. Rubrica  
7. Nuovo pagamento (+ PIN se richiesto)

### Evoluzione metriche (stessi 7 flussi)

| Report | Iter | Problemi | Score | Precisione utile | Note |
|--------|------|----------|-------|------------------|------|
| 150238 | 1 | 136 | 21 | ~32% | Tutto sotto AZIENDA 1 |
| 154839 | 2 | 24 | 83 | ~57% | Miglior baseline utile |
| 162358 | 7 | 13 | 81 | ~70–88% | Miglior equilibrio pre-iter8 |
| 163623 | 8 | 8 | 89 | ~50% | FP home/insoluti |
| 165111 | 9 | 8 | 89 | ~86% | FP ridotti |
| **170543** | **10** | **6** | **92** | **100%** | Solo TP, 180 OK |

### Ground truth attuale (iter. 10)

| Caso | Atteso | Stato iter. 10 |
|------|--------|----------------|
| N02 `causale` | VIOLATION | ✅ |
| N02 `txt_data_*` | VIOLATION | ❌ FN |
| N16 `vop_info` | VIOLATION | ✅ |
| N17 topbar | VIOLATION se no CD | ✅ OK (CD runtime) |
| N18 PIN | Copertura | ❌ non testato |
| N19 home chart | Non segnalare | ✅ AZIENDA 1 = 0 |
| Home (`AZIENDA 1`) | 0 problemi attesi | ✅ + check OK in PDF |

---

## Report PDF (iter. 10+)

1. **Copertina** — problemi, controlli OK, punteggio  
2. **Panoramica per schermata** — conteggio problemi (0 = verde in lista pulite)  
3. **Copertura controlli per ambito** — OK vs problemi per area WCAG  
4. **Per schermata** — prima ✅ controlli superati (campioni), poi ⚠️ problemi dettagliati  
5. Ogni problema: elemento, misura, WCAG, suggerimento, posizione `@id`  
6. Glossario

---

## Prossimi passi noti

- [ ] Recall `txt_data_creazione` / `txt_data_esecuzione` (iter. 11)
- [ ] Test flusso PIN (N18)
- [ ] Panoramica home con «N OK / 0 problemi» più visibile
- [ ] Profili opzionali per altre app oltre Nexi

---

## Build e install

```bash
cd AccessScope
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build release firmato (locale)

1. Genera keystore: `keytool -genkey -v -keystore app/release.keystore -alias access-scope -keyalg RSA -keysize 2048 -validity 10000`
2. Copia `keystore.properties.example` → `keystore.properties` e compila i valori
3. `./gradlew assembleRelease`

### Secrets GitHub (CI release)

| Secret | Uso |
|--------|-----|
| `RELEASE_KEYSTORE_BASE64` | Keystore codificato base64 |
| `RELEASE_KEYSTORE_PASSWORD` | Password keystore |
| `RELEASE_KEY_ALIAS` | Alias chiave |
| `RELEASE_KEY_PASSWORD` | Password chiave |

Report PDF: `/storage/emulated/0/Download/AccessScope_*.pdf`

---

## Changelog commit (develop)

| Commit | Descrizione |
|--------|-------------|
| `b7273d5` | Initial release |
| `f1538d6` | Engine chirurgico + PDF per ambito |
| `e827570` | Permessi, lista app complete |
| `3ce7c3e` | Report in-app, preferiti, grouping |
| `df9b07b` | Overlay STOP, auto-launch |
| `6ffcc90` | Precisione Nexi, ScreenTitleResolver |
| `247a6b2` | Carousel dup ID, home scroll |
| `8b0916b` | Iter 5: titoli, home chart, recall campi |
| `d9b344a` | Iter 6–7: drawer, home widget, UI Avvia |
| `6bb2798` | Iter 8: generiche multi-app + profili |
| *v1.1.0* | Iter 9–10 motore, check OK, UI fluida, PROJECT.md |
| *pending* | KDoc completa su tutti i sorgenti + regola Cursor manutenzione doc |

---

*Documento mantenuto dal team di sviluppo. Per dettagli tecnici sui casi Nexi vedere `reliability-benchmark.md`.*
