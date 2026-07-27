# AccessScope — Documento di progetto (living doc)

> **Aggiornare questo file** ad ogni iterazione significativa: nuove feature, fix di precisione, cambi UI, metriche benchmark, commit rilevanti.

**Repository:** [github.com/xdrake96p/AccessScope](https://github.com/xdrake96p/AccessScope)  
**Package:** `dev.accessscope.scanner`  
**Branch principale sviluppo:** `develop`  
**Branch release stabile:** `main`  
**Ultimo aggiornamento:** 27 luglio 2026 (Maestro editor UX: insert/duplica, overlay one-line, password vs PIN, pausa REC, Play clean)

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
| `AccessScopeAccessibilityService` | Lifecycle Android; delega a `service/scan/*` (scheduler, root, screenshot, tree) |
| `NodeAccessibilityAnalyzer` | Orchestratore analisi; logica in `analyzer/node/*` |
| `PrecisionRules` | Facade euristiche anti-FP; implementazione in `analyzer/precision/*` |
| `AppPrecisionProfiles` | Marker opzionali per app note (Nexi) |
| `ScreenTitleResolver` | Facade titolo schermata; logica in `analyzer/title/*` |
| `CheckCollector` | Registra controlli superati per report |
| `ViolationDedupeRules` | Chiavi dedupe stabili in scroll (viewId, bounds quantizzati) |
| `ScanHistoryStore` | Persistenza JSON cronologia sessioni (20 per package) |
| `SessionComparisonHelper` | Confronto +N/−M risolti tra sessioni |
| `PdfReportExporter` | Facade export PDF; rendering in `export/pdf/*` |
| `ScanSessionRepository` | Stato sessione live, dedupe in-memory, fingerprint schermate; persistenza in `ScanSessionPersistence` |
| `SecureScreenDetector` | Valuta `SecureScreenAssessment` (FLAG_SECURE, PIN/password, screenshot bloccato) |
| `ScreenProtectionReason` | Motivo protezione su `VisitedScreen` (report dinamico, filmstrip) |
| `ScanViewModel` | StateFlow UI; controller in `ScanAppListController`, `ScanSessionController`, `ScanHistoryController` |

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

### Maestro (Beta) — recorder / Play / editor YAML (27 luglio 2026, branch `restyle`)

- Tab MAIN **Maestro**: registra tap/testo/scroll via AccessibilityService → YAML Maestro
- Package `recorder/`: capture (`ActionRecorder`, `RecordingSessionController`), `FlowOptimizationPipeline` (noise, `WaitPlanner`, `SelectorRanker`, `PopupClassifier`), `MaestroYamlExporter`/`Importer`, `FlowStore` (`{id}.yaml`, `{id}.actions.json`, `{id}.telemetry.json`), `FlowPlayer`, overlay Play/Rec
- **Nuovi comandi editor/Play/export:** `DoubleTap`, `EraseText`, `ScrollUntilVisible`, `Swipe`, `PressKey`, `AssertVisible`, `AssertNotVisible`, `OpenLink`, `StopApp`, `RawMaestroYaml` (Play salta raw con log); codec JSON + importer subset + menu `+` raggruppato
- **Intelligence (senza scan in parallelo durante record):** `RecordingTelemetry` (fingerprint + transizioni) + `ScanIntelligenceProvider` (ultima sessione archiviata o live Scan+Flusso) → timeout adattivi, id-first, step `optional: true` su popup
- **Regressione scan:** `AccessibilityEventRouter` (record blocca path scan); `ScanRecorderMutexPolicy`; gate `./gradlew :app:testDebugUnitTest` (175 test)
- **Optimizer:** coalesce `inputText`, dedupe tap, drop noise; `sanitizeForPlay` su legacy; preview YAML scrollabile completo
- **Wait submit-like:** dopo CONTINUA/CONFERMA/accedi/… sempre `waitForAnimationToEnd` + `extendedWaitUntil` (loader)
- **PIN re-entry:** non coalesce due input uguali con gap ≥1.5s; flush pending su focus editabile / `WINDOW_STATE_CHANGED` / cambio campo
- **Play rispetta editor:** `sanitizeForPlay` non rimuove più hideKeyboard / tap su campi / wait aggiunti con `+`; wait timed senza selettore usa il timeout intero
- **Play PIN stretto:** con `viewId` esplicito non fallback al primo editable (evita SET_TEXT su `username` cercando `pincode`); wait ciechi arricchiti con `visibleId` del prossimo input; id strutturali (`drawer_layout`) → preferenza testo
- **PIN×2 eccezione:** `isPinLikeField` non coalesce inserimenti completi; recorder flush `pin_reentry` (≥800ms); password login **non** è più pin-like → un solo `****`
- **Overlay REC:** riepilogo one-line (`id=` / testo / `@%`) + contatore + PICK + **PAUSE/RESUME**
- **Editor UX:** selezione step, `+` inserisce dopo selezione, duplica / inserisci sotto
- **Play clean:** long-press Play → stopApp + tentativo clearState + cold launch
- **Drawer Maestro:** Segnala bug / Suggerisci miglioramento → GitHub `[Maestro]`
- **Editor `+`:** catalogo comandi Maestro esteso (assert, swipe, erase, pressKey, raw YAML, …)
- **Regola Cursor:** `.cursor/rules/maestro-algorithm-learning.mdc` — ogni fix Maestro aggiorna l’algoritmo + test
- Overlay **STOP REC · Beta** / **STOP PLAY · Beta**; mutex record↔scan; play+scan ammessi insieme (Scan+Flusso)
- Drawer dedicato su route `maestro` (`MaestroDrawerContent`): **Importa YAML**, **Nuovo flusso YAML**, bug/suggerimento GitHub
- Card flusso: Play, matita (editor step `maestro/edit/{id}`), logo AccessScope (Scan+Flusso), YAML/share/delete
- Onboarding: pagina «Maestro» (Beta); password mascherate `****` (skip in Play)
- **Fix cattura step:** fallback root/focus; FOCUSED/SELECTED; toast se 0 step / a11y non bound dopo update APK
- Fase 2 futura: `maestro run` via plugin/CLI sul PC (export YAML già pronto per CI)

### Precisione anti-rumore + report dinamico fingerprint (27 luglio 2026, branch `restyle`)

- `ViolationConfidencePolicy`: demota overlap/custom action su shell strutturali (`content`, `container`, scroll…) sotto soglia report
- `ReportHelper`: soglie più alte per overlap/custom/scroll; filtro con demotion; Settings «Findings a bassa confidenza»
- `ScreenReaderFinding` / `CheckAreaSummary`: `screenFingerprint`; TalkBack/passed attribuiti fingerprint-first in `DynamicReportHelper`
- Skip overlap su ID strutturali in `PrecisionStructural`
- Test: `ReportHelperConfidenceFilterTest`, estensione `DynamicReportHelperTest`

### Home search-only (27 luglio 2026, branch `restyle`)

- Home non mostra più l’elenco completo delle app installate: solo campo ricerca + risultati mentre digiti
- A riposo resta visibile al più l’app già selezionata (per deselezionare) e l’hint «digita nome/package»
- Rimosso «Prima visibile» dal pannello selezione; preferiti restano nel tab dedicato
- **Preferiti:** solo app con stella (rimossa sezione «Altre App»); ricerca filtra tra i preferiti; aggiunta nuove preferite dalla stella in Home
- **A11y dogfooding:** `FavoriteAccent` adattivo (ambra scura in light ≥5:1); CTA «Avvia scansione» disabled con surface/onSurfaceVariant (≥4.5:1, prima ~1.5:1)

### Restyle "Scanner & HUD" — F1–F6 completato (23 luglio 2026, branch `restyle`)

Completato il restyle UI dai materiali Stitch (`docs/restyle/`, piano in `PIANO_RESTYLE.md`). Zero modifiche ad analyzer/service/data/bridge: **134 test JVM verdi**.

**F1 — Splash + Onboarding (nuove):**
- `onboarding/SplashScreen.kt`: gradiente teal animato, scanline HUD, corner accents, entrance logo, versione da `BuildConfig` (abilitato `buildConfig` nel gradle)
- `onboarding/OnboardingScreen.kt`: `HorizontalPager` 6 pagine (Benvenuto, Problema, Ecosistema, Come funziona, 8 aree WCAG, Risultati benchmark) con dots, "Salta tutto", checkbox "non mostrare più"
- `util/OnboardingStore.kt` (SharedPreferences); startDestination `splash` → onboarding solo al primo avvio
- Copy corretto rispetto ai mockup: navigazione **manuale** (non bot), **on-device** (non cloud), GitHub (non GitLab), 40 tipi di violazione

**F2 — Navigazione a due zone + Home:**
- Bottom bar contestuali (`AccessScopeBottomBar.kt`): zona principale `Home/Preferiti/Settings`, zona sessione `Scansione/Dettagli/Report/Storico` (switch su route)
- `ModalNavigationDrawer` (`AccessScopeDrawer.kt`): brand + Cronologia/Ultima sessione/Suggerimenti
- Home riscritta: `HomeHeroCard` (griglia HUD + CTA pill), `HomeLastSessionCard` (donut + PROBLEMI/CONTROLLI OK), CTA "Vedi report dinamico"
- `FavoritesScreen` nuova (tab): card preferiti + altre app con stelle; pensionati `HeroHeader`, `HomeScanActionBar`, `ScanHistoryEntryButton`

**F3 — Report:** donut `ReportDonutOverview` (score + TOTALE/OK/KO) al posto di `ReportSummaryCard`; `ViolationCard`/`ScreenIssueList` con barra laterale gravità + `SeverityChip`; score card in `DynamicReportScreen`; `ViolationDetailScreen` riscritto (hero + chip + ID, before/after contrasto, azioni correttive numerate, Pro Tip, bento tecnico, bottom bar "Condividi report")

**F4 — Storico:** righe sessione con icona app, data mono, trend arrow ±N da `scoreDelta`, chip `NN/100` (soglia 70)

**F5 — Settings + Feedback:** sezioni `SettingsAccordion` (Permessi 2/2, Ambiti, Categorie, Preferenze, Diagnostica, Legali, Suggerimenti) + **Danger Zone** "Elimina cronologia" (nuovo `ScanHistoryStore.clearAll()` + passthrough controller/VM); Feedback con segmented control tipo e CTA pill "INVIA SU GITHUB"

**F6 — Polish:** overlay STOP con nuovi token (`#0D1518`/`#BA1A1A`); **dogfooding WCAG** sulla palette (script contrasto): tutto AA PASS tranne `outline` light (4.26:1) → `severityColor(MINOR)` spostato su `onSurfaceVariant`

Nota build: Gradle richiede JDK ≤ 21 (JBR Android Studio) — il JDK 25 di sistema non è supportato da Gradle 8.9.

### Restyle "Scanner & HUD" — F0 design tokens (23 luglio 2026, branch `restyle`)

Avvio restyle UI dai materiali Stitch (`docs/restyle/`, piano in `PIANO_RESTYLE.md`). **F0 — solo token, zero cambi comportamentali:**

- **Palette:** nuovo schema Electric Teal (light: primary `#006875`, container `#00E5FF`; dark: surface `#0D1518`, primary `#C3F5FF`) con tier `surfaceContainer*` completi in `Theme.kt`
- **Compat layer in `Color.kt`:** i nomi storici (`BrandPrimary`, `Danger`, `Success`, `Warning`, `BrandDark`…) restano come getter `@Composable` delegati allo `colorScheme` → nessuna modifica ai componenti
- **severityColor()** ora `@Composable`: CRITICAL/SERIOUS → error, MODERATE → ambra (`tertiary` light / `tertiaryFixedDim` dark), MINOR → outline
- **Tipografia tri-font:** Hanken Grotesk (display), Inter (body), JetBrains Mono (label) via downloadable fonts Google (`ui-text-google-fonts` + `res/values/font_certs.xml`), fallback font di sistema
- **Shape:** aggiunta `PillShape` (CTA); **XML:** `colors.xml`/`themes.xml` allineati ai nuovi token
- Successo semantico: verde → teal `primary` (come da DESIGN.md Stitch)

Nota build: Gradle richiede JDK ≤ 21 (JBR Android Studio) — il JDK 25 di sistema non è supportato da Gradle 8.9.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` (**134** test JVM verdi).

### Precisione anti-FP (touch, titoli, report) — 10 luglio 2026

Miglioramenti senza regressione sul benchmark Nexi v1.3.0 (`MpsVerificationRegressionTest`, `PlatformPatternsRegressionTest`).

**Touch (`SMALL_TOUCH_TARGET`):**
- `shouldSkipTouchTargetCheck` allineato a overlap: `isClickableLayoutShell`, phantom/anomalous bounds, `isEmptyClickableHitArea`, WebView senza label
- Nuovi test: `TouchTargetPrecisionTest` (shell, vop_info, bottoni reali, phantom, empty TextView)

**Titoli e fingerprint:**
- `ScreenFingerprint.fingerprintTitle` preferisce content markers; tab label nel chrome (`tab:…`)
- `event_text` solo senza fonti stabili; `TitleCache` non riusa titolo in scroll senza match topbar/markers
- Test: `ScreenTitleFingerprintRegressionTest`

**Secure screen:**
- Gate multi-segnale in `hasSecureNodes` (no protezione da singolo `isPassword` su login)
- `GENERIC_PIN_KEYS` ristretto (rimossi `key`/`digit` generici)

**Report dinamico:**
- `visitedScreens` persistiti in archivio JSON (`ScanHistoryStore`)
- TalkBack/passed con titolo duplicato assegnati al frame con violazioni
- Badge filmstrip per reason (PIN / FLAG_SECURE / Screenshot); scroll automatico filmstrip; wireframe su canvas protetto

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` (**127** test JVM).

### Schermate sensibili + refactor strutturale P1/P2 (10 luglio 2026)

**Rilevamento schermate protette (comportamento invariato: l'albero a11y resta analizzato):**

- `ScreenProtectionReason` su `VisitedScreen` (`NONE`, `FLAG_SECURE`, `PIN_OR_PASSWORD`, `SCREENSHOT_BLOCKED`)
- `SecureScreenAssessment` in `SecureScreenDetector`: separa FLAG_SECURE, PIN/password (keyword strict `\bpin\b`, `\botp\b`, …), bitmap nero
- `SCREENSHOT_BLOCKED` non forza modalità secure completa su alberi normali (meno FP su dark theme / loading)
- Pipeline: `AccessibilityTreeScanner` passa `protectionReason` a `registerUniqueScreen`; contrasto disabilitato solo se `!allowContrast`
- UI report dinamico: badge «Protetta» nel filmstrip; messaggi differenziati in `ScreenIssueCanvas`
- Test: `SecureScreenDetectorTest`, aggiornamento `ScanSessionRepositoryTest`

**Split move-only (zero cambio logica analisi):**

| Area | Package / file | Contenuto estratto |
|------|----------------|-------------------|
| Analyzer | `analyzer/contrast/` | `WcagContrastMath`, `WcagContrastSampling`, `WcagContrastMeasurement`, `WcagContrastPolicy`, `WcagContrastTypes`; facade `WcagContrast.kt` |
| Analyzer | `analyzer/node/` | `NodeLabelsSingleChecker`, `NodeTouchSingleChecker`, `NodeFormsSingleChecker`, `NodeTextSingleChecker`, `NodeStructureSingleChecker`, `NodeScreenReaderSingleChecker`, `NodeMediaSingleChecker`, `NodeSingleNodeCheckSupport`; orchestrator `NodeSingleNodeChecker.kt` |
| Analyzer | `NodeSnapshotFactory.kt` | `AccessibilityNodeInfo.toSnapshot` + helper heading/expanded |
| Analyzer | `analyzer/precision/` | `PrecisionLabelHierarchy`, `PrecisionHeadingSkips`, `PrecisionDecorativeLabels`; facade `PrecisionLabels.kt` |
| Analyzer | `analyzer/title/` | `TitlePinWalker`, `TitleTopBarWalker`, `TitleSectionWalker`; facade `TitleTreeWalker.kt` |
| Data | `ScanSessionPersistence.kt` | SharedPreferences scan interrotta; facade `ScanSessionRepository.kt` |

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` (105+ test JVM).

### Refactor strutturale — spezzettamento file grandi (10 luglio 2026)

Refactoring **move-only** dei sorgenti più grandi (>500 righe), senza cambio di comportamento né bump versione. API pubbliche invariate tramite facade/delegation; moduli estratti `internal` dove possibile.

| Area | Package / file | Contenuto estratto |
|------|----------------|-------------------|
| Data | `data/` | `ViolationTypes`, `ViolationModels`, `SessionModels`, `AppModels` (eliminato `Models.kt`) |
| Analyzer | `analyzer/precision/` | `PrecisionGeometry`, `PrecisionStructural`, `PrecisionNavigation`, `PrecisionHome`, `PrecisionContrast`, `PrecisionTouch`, `PrecisionLabels`, `PrecisionRulesPlatform`, `PrecisionExtensions`; facade `PrecisionRules.kt` |
| Analyzer | `analyzer/node/` | collector, checker singolo/contrasto/cross-node/strutturale, `ViolationBuilder`; orchestrator `NodeAccessibilityAnalyzer.kt` |
| Analyzer | `analyzer/title/` | candidati, tree walk, euristiche Nexi, cache; facade `ScreenTitleResolver.kt` |
| Export | `export/pdf/` | costanti, contesto disegno, renderer copertina/violazioni/sezioni ausiliarie |
| Service | `service/scan/` | scheduler, root selector, screenshot, tree scanner |
| ViewModel | `ui/viewmodel/` | `ScanViewModelUiState`, controller app-list/session/history |
| UI | `ui/screen/report/`, `ui/screen/home/` | composable estratti da `ReportScreen` e `HomeScreen` |

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` (99 test JVM, inclusi `PlatformPatternsRegressionTest` e `MpsVerificationRegressionTest`).

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
| *pending* | Refactor strutturale: spezzettamento file grandi per responsabilità (nessun cambio comportamento) |

---

*Documento mantenuto dal team di sviluppo. Per dettagli tecnici sui casi Nexi vedere `reliability-benchmark.md`.*
