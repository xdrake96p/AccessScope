# AccessScope — Documento di progetto (living doc)

> **Aggiornare questo file** ad ogni iterazione significativa: nuove feature, fix di precisione, cambi UI, metriche benchmark, commit rilevanti.

**Repository:** [github.com/xdrake96p/AccessScope](https://github.com/xdrake96p/AccessScope)  
**Package:** `dev.accessscope.scanner`  
**Branch principale sviluppo:** `develop`  
**Branch release stabile:** `main`  
**Ultimo aggiornamento:** 31 luglio 2026 (Git Flow canonico in `docs/GIT_FLOW.md`: branch naming, delete post-merge, release app+plugin, bump develop post-tag)

### Settimana 2 — Maestro "CLI-truth" (31 luglio 2026)

Verificato con Maestro CLI 2.6.1 installato in locale (`maestro check-syntax`, nessun device
richiesto) che lo YAML esportato **falliva davvero** il parsing su alcuni comandi generati
attivamente dalla pipeline. Corretto, verificato di nuovo con la stessa CLI:

- **`scrollUntilVisible`**: richiede id/text annidati sotto `element:` — senza, `maestro
  check-syntax` dava `Config Field Required: element`. Fix in `formatScrollUntilVisible`.
- **`eraseText` con `viewId`**: `eraseText: {id: ...}` non è sintassi valida (`Unknown
  Property: id`). Ora due comandi: `tapOn: {id}` + `eraseText` bare — sia in export
  (`MaestroYamlExporter`) sia in import (nuovo merge in `MaestroYamlImporter`, simmetrico
  a quello già esistente per `tapOn`+`inputText`).
- **Percentuali `swipe.start/end`**: `"50.0%,80.0%"` (decimale) dava `Parsing Failed`;
  `"50%,80%"` (intero) è `OK`. `fmtPercent` ora arrotonda a intero (era `fmt` con `%.1f`).
- **Selettori come regex**: Maestro tratta `text:`/`id:` come regex full-match — un'etichetta
  con parentesi (`"Accedi (Beta)"`) falliva il match **a runtime senza errore di parsing**,
  la rottura più subdola per chi riusa lo YAML. Nuovo `escapeMaestroText` in export (tutti i
  testi/id usati come selettore, mai su `inputText` che è letterale) + `unescapeMaestroText`
  simmetrico in import, per non rompere il round-trip.
  Test: `MaestroYamlRoundTripTest.regexMetacharsInLabel_surviveExportImportExport`.
- **`pressKey`** validato contro l'enum reale (Enter/Backspace/Back/Home/Lock/Volume
  Up/Volume Down/Recent Apps/Power/Tab, tutti verificati con `check-syntax`); chiave
  sconosciuta → fallback `Enter` + commento invece di un valore non validato.
- **Placeholder `"unknown"` eliminato**: uno step senza selettore (id/text/cd/point) produceva
  `- comando: "unknown"`, un letterale che fallisce a runtime in modo confuso. Ora diventa un
  commento inerte (`# SKIPPED ...`) — mai un comando eseguibile rotto.
- **Header `env:`**: se il flusso usa `${PIN}`/`${PASSWORD}`, l'header ora dichiara `env:` con
  valori d'esempio + promemoria `maestro test -e PIN=... -e PASSWORD=...`.
- **Fix collaterale**: `extendedWaitUntil` esportava l'id completo (`package:id/name`) invece
  del solo nome corto come tutti gli altri comandi — con l'escape regex diventava illeggibile
  (punti nel package escapati). Ora usa `shortViewId` come il resto dell'exporter.
- **`docs/MAESTRO_COMPAT.md`** corretto: la voce "import raw YAML: sì (frammento)" era falsa
  (l'importer fallisce su comandi non riconosciuti, nessun passthrough); documentato anche che
  `when:`/`conditionVisibleId`/`conditionVisibleText` non sono ancora esportati (richiede
  wrapping `runFlow` + supporto import per blocchi annidati — rimandato).
- **Non fatto in questo giro** (per rischio/ampiezza, documentato come backlog): export
  `when:`/`runFlow` dai campi `conditionVisible*` già presenti nel modello ma mai usati;
  unificazione completa `optimize()`/`sanitizeForPlay()` in un'unica pipeline.

Verifica: 254 test JVM verdi (nuovi test su escaping/element/eraseText/env/skip-comment);
YAML generato da un flusso complesso (18 step, tutti i tipi di comando) validato con
`maestro check-syntax` reale — `OK`; reinstallato su Galaxy S10.

### P0 Stabilità (Settimana 1 piano pre-mortem, 31 luglio 2026)

### P0 Stabilità (Settimana 1 piano pre-mortem, 31 luglio 2026)

Fix dei "demo-killer" individuati dal pre-mortem (4 audit: threading app, TalkBack/report, Maestro, plugin):

- **Scan off-main-thread**: `AccessibilityScreenshotCapture` riceve l'executor di scansione
  (non più `mainExecutor`) — camminata albero, contrasto e JPEG evidenze non girano più sul
  main thread (prima: jank/ANR garantiti su schermate dense, sempre su API 30+).
- **Leak `HardwareBuffer` chiuso**: il buffer nativo dello screenshot non veniva mai
  rilasciato → screenshot degradanti nelle sessioni lunghe. Ora `close()` su tutti i path,
  e il bitmap HARDWARE intermedio viene riciclato dopo la copia.
- **B3 retry screenshot**: fallimenti transitori e bitmap neri ritentati 1 volta con backoff
  (300ms) prima di marcare la schermata protetta; FLAG_SECURE resta senza retry (deterministico).
- **Data race**: `seenFingerprintsThisSession` → `ConcurrentHashMap.newKeySet`.
- **OOM report dinamico**: decode screenshot con `inSampleSize` (lato max = schermo),
  cache thumbnail con eviction FIFO (max 6), `remember` keyed su `sessionId`;
  `ScreenIssueCanvas` ora scala i marker sulla larghezza schermo reale (non `bitmap.width`),
  così restano allineati anche con bitmap downsampled.
- **Frame build fuori dal tick live**: sessioni archiviate via `produceState`+IO (una volta
  per sessionId); live keyed sui soli dati che contano, non sull'intero `scanState`.
- **Overlay**: `removeView` protetto in `ScanOverlayService`; overlay errore playback con
  auto-chiusura 12s; rollback scan+overlay se `startScanWithFlow` fallisce all'avvio.
- **I/O fuori composizione**: `FlowsScreen` (listFlows/readYaml/deleteFlow su IO),
  `ScanHistoryScreen` (parse cronologia via `produceState`+IO).
- **Settings**: rimosso lo switch "Analizza tutto" one-way (spegnerlo non faceva nulla);
  il preset «Completa» copre lo stesso caso.
- **Plugin — deadlock pipe buffer**: `CliExecutor` (AS) e `Adb` (CLI) chiamavano `waitFor()`
  prima di drenare stdout: output > 64KB (fetch-results ricco) bloccava il figlio per sempre
  → IDE congelato fino a timeout (10 min). Ora stdout drenato in concorrenza.
- **Plugin — crash su zero sessioni**: `fetch-results` con `{"error":"not_found"}` (exit 0)
  crashava VS Code su `result.violations.length`; ora messaggio chiaro "nessun risultato".
- **Plugin — README**: rimosso il package cliente `it.nexi.bff` (→ `com.example.targetapp`).

Verifica: app 250 test JVM + assembleDebug verdi; `:cli:compileKotlin` e
`:android-studio-plugin:compileKotlin` verdi; `tsc --noEmit` estensione VS Code pulito;
reinstallato su Galaxy S10.

### Motore di precisione: eliminate le euristiche specifiche Nexi/AXA (31 luglio 2026)

Nexi e AXA erano le app usate per validare il motore in fase di sviluppo iniziale; il prodotto
è pensato per scansionare **qualunque app**, quindi ogni euristica ancorata a id/testi/titoli di
quelle due app è stata rimossa (non solo isolata in un profilo) da `analyzer/precision/*`:

- `AppPrecisionProfiles`: eliminato `isNexi()` e tutti i set privati Nexi (home markers, chart
  text/container ids, carousel widget ids, large-text ids, primary CTA ids, PIN keys Nexi); le
  funzioni pubbliche restano solo per le convenzioni di naming realmente generiche (CTA `show_more`/
  `see_all`/…, `nav_`/`menu_`/`drawer_`, `tv_tab`).
- `PrecisionHome`: rimossa l'intera logica home/widget/CTA brandizzata specifica Nexi
  (`isHomeScreenContext`, `shouldSkipHomeWidgetAnalysis`, `isHomeEffettiCarouselNode`,
  `hasTvCustomDescendant`, `isBrandedCtaText`, `isHomeChartDecorativeText`, …) — priva di
  equivalente generico, quindi era completamente inerte per ogni app diversa da Nexi.
- `PrecisionContrast`: rimossi gli hardcode `vop_info`, `causale`, `tv_title_second_section`;
  il cluster "calendario Material" (gated su titolo schermata `COMUNICAZIONI` + id Nexi) è stato
  **generalizzato** in rilevamento puramente strutturale (conteggio nodi con `collectionRow`/
  `collectionColumn` valorizzati — API standard Android, non un id di un'app) invece di essere
  cancellato, perché il problema che risolve (rumore su celle ripetute di griglia) è reale per
  qualunque app con griglie dense.
- `PrecisionNavigation`: `isTopBarControl` ora riconosce per substring (`topbar`/`toolbar`/
  `app_bar`/`action_bar`) invece di un elenco id esatti Nexi; rimossa `shouldReportMissingTopBarLabel`
  (pattern nidificato specifico Nexi, nessun generico equivalente).
- `PrecisionHeadingSkips`/`PrecisionDecorativeLabels`/`PrecisionLabels`/`PrecisionStructural`:
  rimossi gli id letterali residui (`causale`, `vop_info`, `dot_filter`, `multiple_slection`,
  `recycler_distinte`/`recycler_effetti`, `card_effetti`/`tab_home`/`card_home`).
- Test: cancellato `MpsVerificationRegressionTest` (asseriva esplicitamente il trattamento
  speciale di id Nexi); le sole asserzioni realmente generiche sono confluite in
  `WcagContrastRegressionTest`.
**Risoluzione titolo/fingerprint** (`analyzer/title/*`), completata nello stesso giro:

- Cancellato interamente `NexiTitleHeuristics.kt` (titoli sezione Nexi hardcoded, marker home/nav/content
  Nexi, widget «Ultimi insoluti») — nessun equivalente generico possibile, solo dead-for-non-Nexi.
- `TitleCandidateLogic.inferTitleFromContentMarkers`/`isToolbarConsistentWithContent` rimossi: erano
  interamente basati su marker AXA (`titlehello`, `policyname`, `productslist`, …) e Nexi
  (`labelcontacts`, `edt_ragione_sociale`, …); la candidatura "content_markers" (peso massimo, 100)
  è sparita dalla catena di risoluzione titolo.
- `ScreenFingerprint.fingerprintTitle` semplificato: usa direttamente il titolo già risolto da
  `ScreenTitleResolver`, senza più il tentativo di sostituirlo con un marker di contenuto Nexi/AXA.
- `TitleScreenDetection`/`TitleCache`/`TitleSectionWalker`/`TitleTopBarWalker`: rimossi tutti i
  riferimenti a `NexiTitleHeuristics`; `isDrawerOnlyRoot`/`isTransientOverlay` ora usano un
  rilevamento generico di "chrome" strutturale (id contenenti `topbar`/`toolbar`/`tab_`/
  `bottom_nav`/`action_bar`/`appbar`) invece di liste di id Nexi.
- Test: riscritti `ScreenTitleResolverTest` e `ScreenTitleFingerprintRegressionTest` per testare
  solo il comportamento generico della catena di candidati (pesi, priorità sorgente, filtro titoli
  generici), rimossa ogni asserzione legata a marker Nexi/AXA.

### Fix critico: frammentazione schermate con titolo "ViewGroup"/classe generica (31 luglio 2026)

Test su device reale dopo la pulizia Nexi/AXA: troppe "schermate" nel report, molte con nome
tipo `ViewGroup`/classe Android generica.

- **Causa (bug preesistente, smascherato dalla rimozione dei fallback Nexi/AXA):**
  `AccessibilityTreeScanner.scanRoot` chiama `ScreenTitleResolver.resolve` sia su
  `TYPE_WINDOW_STATE_CHANGED` sia su `TYPE_WINDOW_CONTENT_CHANGED`. Il candidato titolo
  "activity" usava `event.className` incondizionatamente — ma su `TYPE_WINDOW_CONTENT_CHANGED`
  quel campo è la classe del **nodo sorgente cambiato** (spesso un `ViewGroup`/`RecyclerView`
  qualunque durante lo scroll), non l'Activity. Prima, il candidato "content_markers" (peso 100,
  ora rimosso perché specifico AXA/Nexi) o i fallback `NexiTitleHeuristics` mascheravano quasi
  sempre questo titolo spurio; una volta tolti, ogni scroll poteva produrre un titolo/fingerprint
  diverso → "schermate" fasulle a raffica.
- **Fix:** `ScreenTitleResolver.resolve` considera il candidato "activity" solo se
  `event.eventType == TYPE_WINDOW_STATE_CHANGED`; aggiunta `TitleCandidateLogic
  .isAndroidFrameworkViewClassName` (denylist generica ViewGroup/RecyclerView/FrameLayout/…)
  come difesa aggiuntiva anche in quel caso.
- Test: `ScreenTitleResolverTest.isAndroidFrameworkViewClassName_rejectsGenericContainers` (unità)
  e `ScreenTitleFingerprintRegressionTest.contentChanged_neverUsesSourceNodeClassNameAsTitle`
  (end-to-end: due `TYPE_WINDOW_CONTENT_CHANGED` con `className` diverso sullo stesso root devono
  risolvere allo stesso titolo, mai al nome della classe sorgente) — riproduce esattamente lo
  scenario del bug per impedire che si ripresenti silenziosamente in refactor futuri.
- Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde; reinstallato su device
  fisico (Galaxy S10) per riverifica manuale.

### Chiusura Fase 1: contrasto e rilevamento errori form (31 luglio 2026)

- **Contrasto:** `WcagContrastSampling.resolveEffectiveForeground` compositava il testo
  semi-trasparente sempre su bianco fisso, anche su sfondi scuri — un testo scuro semi-trasparente
  su sfondo scuro risultava artificialmente "schiarito" e quindi ad alto contrasto (falso negativo).
  Ora compositi sullo sfondo realmente campionato (`resolveEffectiveBackground`, calcolato prima).
  Test: `WcagContrastRegressionTest.semiTransparentDarkText_onDarkBackground_compositesOverRealBackground`.
- **Errori form:** `NodeFormsSingleChecker` cercava solo la parola inglese "error" nel testo del
  campo — non intercettava mai un messaggio italiano ("Errore: campo obbligatorio"), ironico per
  un'app italiana. Nuovo `NodeSingleNodeCheckSupport.looksLikeVisualErrorText` con keyword IT/EN.
  Test: `NodeSingleNodeCheckSupportTest`.

### Fase 2 (Maestro zero-edit): stato reale del backlog M2/M3 (31 luglio 2026)

Verifica del backlog `docs/PIANO_MAESTRO_E_SCANSIONE.md` prima di aggiungere lavoro nuovo:

- **A9 (riordino step)** e **A11 (learning-loop fail-rate)** erano **già implementati** (pulsanti
  su/giù per riordino in `FlowEditScreen`/`StepRow`; `SelectorFailRateStore`/`SelectorChainHealer`
  promuovono già il ramo alternativo della catena dopo 2 fallimenti). I badge inline per-step delle
  issue ZeroEdit (⛔/⚠/ℹ) erano **già presenti** in `StepRow` — non serviva nessun lavoro UX aggiuntivo lì.
- **Rafforzato** il contratto ZeroEdit come da piano: `ZeroEditGate` ora promuove a **Error**
  (bloccante) i lint `STRUCTURAL_SELECTOR`/`NOISE_SELECTOR` quando lo step, dopo l'heal statico,
  non ha nessun fallback testo/content-description — prima restavano solo `WARNING` e il flusso
  poteva comunque essere salvato con un selettore fragile come unico riferimento.
  Test: `ZeroEditGateTest.structuralIdWithoutFallback_isBlockingError` (+ controprova
  `structuralIdWithFallbackText_staysWarningOnly`).
- **Ancora da fare (backlog reale, non iniziato):** A10 (thumbnail per step nell'editor — richiede
  wiring cattura schermo), A12 (Scan+Flusso combinato — resta "da valutare" come da piano, non un
  task di build).

**Verifica complessiva:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde (248 test JVM).
Nessuna verifica ancora su device/benchmark reale con app terze: da fare a fine lavoro come da
richiesta esplicita (i punteggi su Nexi/AXA in `reports/golden/` cambieranno rispetto alle baseline
v1.3.0/v1.3.1 — atteso, dato che quelle app non ricevono più trattamento speciale).

### Fix confidence gate: toggle "findings a bassa confidenza" non funzionava (31 luglio 2026)

- **Bug:** `ScanSessionRepository.addViolations` applicava la soglia di confidenza (`ReportHelper.filterViolations` default) **in scrittura**, scartando per sempre le violazioni sotto soglia. Il toggle Settings "Findings a bassa confidenza" (letto poi da `ReportScreen`/`DynamicReportScreen`/PDF) non aveva più nulla da recuperare: sessione live, sessione archiviata, PDF e report affidabilità mostravano sempre solo le violazioni confermate, a prescindere dal toggle.
- **Fix:** `addViolations` ora applica solo `ViolationConfidencePolicy.demoteIfNoisy` + dedupe per `dedupeKey`, senza soglia — la soglia resta responsabilità esclusiva del livello report (`ReportHelper.filterViolations`), coerente con `DynamicReportHelper` che già lo faceva correttamente.
- **Consistenza a cascata:** snapshot archiviato in `AccessScopeApp.stopScanSession` e `PdfReportExporter.export` (nuovo parametro `includeLowConfidence`) ora ricevono lo stesso toggle mostrato live, invece del default fisso `false`.
- **Contratto bridge invariato:** `ScanResultProvider.buildStatusJson` (`violationCount`) resta esplicitamente confidence-confermata-only (non segue il toggle UI), per non alterare la semantica consumata da plugin/CI.
- Test: `ScanSessionRepositoryTest.addViolations_keepsLowConfidenceFindings_forLaterReportFiltering`; fix di `PermissionHelperTest` (shadowing `resolveActivity` per il nuovo fallback OEM già introdotto in `AppHelpers.kt`).
- Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde (254 test JVM).

**Manuale utente e tecnico:** [`docs/MANUALE_UTENTE.md`](MANUALE_UTENTE.md) — installazione, uso plugin AS/VS Code, troubleshooting.

### OTP/PIN edit-slot vs pad (31 luglio 2026)

- **Schermo reale:** `edit1`…`edit6` sono EditText (OTP SMS / PIN); i tasti `uno`/`due` spesso **non** sono nell’albero a11y
- **Algoritmo:** `normalizePinOrOtpSlotInputs` — collassa N input sugli slot in un `inputText` su `edit1`; tap pad → **Optional**; Play ha fallback `inputPinDigitOnSlots` se il pad manca
- Non droppare gli inputText sugli slot (rompevano l’inserimento cifre)

### PIN pad digit-slot (31 luglio 2026)

- **Problema:** su Nexi/MPS i tap `uno`…`sei` riempiono `edit1`…`edit6` → REC esportava anche `inputText: "123456"` su ogni slot → Play falliva al tap `uno` (step ~35) e non arrivava a CONTINUA/Conferma
- **Fix algoritmo:** `isPinPadDigitSlot` / `isPinPadKey`; REC ignora TEXT_CHANGED sugli slot; `NoiseActionFilter.dropPinPadDigitSlotInputs` (+ collapse wait) in optimize e `sanitizeForPlay`; drop assert rating spurî

### Accessibilità unbound + install sicuro (31 luglio 2026)

- **Causa:** su Samsung `am force-stop` lascia AccessScope in `enabled_accessibility_services` senza bind → REC/scan senza eventi
- **App:** `AccessibilityBindState` + `isAccessibilityServiceReady` = enabled∧connected; Home/Settings con warning + CTA **Ripristina collegamento**; gate su `startScan`/`startRecordingSession`; refresh permessi su `ON_RESUME`
- **Intent:** `accessibilityServiceIntent` / `safeStartSettingsIntent` con `resolveActivity` (fallback lista generale su OEM)
- **Workflow:** `scripts/install-debug.sh` + regola `.cursor/rules/android-device-install.mdc` — vietato force-stop dopo install
- **Nota Samsung:** se `enabled` pieno e `bound` vuoto persiste dopo OFF→ON, un **reboot** ripristina il bind (stato DEAD connection AMS)

### Maestro ZeroEdit + editor (31 luglio 2026, branch `restyle`)

- **Contratto ZeroEdit:** `recorder/quality/` (`ZeroEditGate`, `StaticSelectorHealer`) — lint Error su point-only; heal id strutturali → testo; gate su `FlowStore.saveFlow`/`updateFlow`
- **Capture:** `recorder/capture/TapIdentityResolver` → `selectorChain` + `weakSelector` a REC; `TYPE_VIEW_SELECTED` solo se clickable/checkable
- **Popup optional:** preservati da REC in `OptionalStepPolicy`; ghost filter esteso a testo corto post-scroll
- **Editor:** `InsertStepDialog`/`InsertStepCatalog` — `+` apre scelta tipo (non Wait immediato); edit (cd, point, direction, optional assert); **Download YAML** (`YamlDownloadHelper`); Salva vs Ottimizza
- **Alert Nexi `alert_pop`:** `AlertOverlayResolver` cattura `id/dismiss` («OK, HO CAPITO») anche quando a11y punta all’EditText sotto (prima: `editable_skip_tap` → tap perso / fuori ordine)
- **Ordine YAML overlay:** `BlockingOverlayOrderHealer` sposta dismiss bloccanti subito dopo CONTINUA (prima degli `inputText`); `BlockingOverlayWaitPlanner` aggiunge `extendedWaitUntil` sul dismiss; attivo anche in `sanitizeForPlay`

---

## Convenzioni di sviluppo

A **fine di ogni task**, obbligatorio (regola Cursor: `.cursor/rules/project-maintenance.mdc`):

1. **Aggiornare questo file** (`docs/PROJECT.md`) — cronologia, architettura, benchmark, changelog.
2. **Aggiornare la KDoc** (JavaDoc Kotlin) su tutti i file `.kt` modificati o creati: blocco file, tipi pubblici, funzioni con `@param` / `@return`, descrizioni in italiano.

### Git e release

**Documento canonico:** [`docs/GIT_FLOW.md`](GIT_FLOW.md).  
Regole Cursor: `.cursor/rules/git-flow-gate.mdc` (gate prima di branch/commit/push) · `.cursor/rules/git-release-workflow.mdc`.

1. Branch di lavoro da `develop` con prefisso (`feature/`, `bugfix/`, `chore/`, `docs/`) → merge in `develop` → **delete** branch
2. Commit `release:` su `develop` (versioni + note app/plugin)
3. Merge `develop` → `main`
4. Tag `v*` su `main` (es. `v1.3.0` = `versionName`) → CI pubblica APK + plugin ZIP/VSIX
5. Su `develop`: **bump sempre l’app**; valuta se bumpare anche i plugin per il ciclo successivo
6. Hotfix urgenti: da `main` (`hotfix/`), poi re-merge su `develop`

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

### Piano Maestro + scansione M0/M1 (27 luglio 2026, branch `restyle`)

Documento vivo: [`docs/PIANO_MAESTRO_E_SCANSIONE.md`](PIANO_MAESTRO_E_SCANSIONE.md). Compatibilità comandi: [`docs/MAESTRO_COMPAT.md`](MAESTRO_COMPAT.md).

- **M0 anti-regressione:** `tools/compare_scan_outputs.py` (preferisce `dedupeKey`), `reports/golden/` + fixture sample, contract `SessionJsonSchemaContractTest` / `BridgeIds` / `BridgeJsonContractTest`
- **M1-A1 lint:** `FlowLinter` (selettori deboli, submit senza wait) + badge in `FlowEditScreen`
- **M1-A2:** `ScrollCoalescer` → `scrollUntilVisible` nella pipeline
- **M1-A3:** round-trip export↔import (`MaestroYamlRoundTripTest`); merge tap+inputText in importer
- **M1-A4:** `tools/verify_yaml_with_maestro_cli.sh`
- **M1-B1:** `ReportHelper.confidenceGateStats` + sezione «Confidence gate» nel reliability MD
- **Popup Maestro:** package `permissioncontroller` / installer / GMS non più droppati in REC/optimize; tap Allow/Consenti → `optional: true`; root multi-window in recording
- **Popup in-app (27 luglio):** «Non ora» anche se source è EditText sotto dialog; `AssertVisible` titolo (es. caricamento documento); scroll solo con delta reale + soppressione dopo Back/popup; Back via `onKeyEvent`; editor insert dopo selezione + checkbox Opzionale su tap

- **Overlay REC compatto (31 luglio):** barra minimizzata all’avvio (`⋮ REC` + step + `▸` + **STOP** sempre in header); PICK/PAUSE/UNDO/OPT e YAML solo dopo espansione — evita che STOP esca dallo schermo su overlay stretto

- **R1 affidabilità (27 luglio):**
  - **waitForAnimationToEnd ovunque serve:** `WaitPlanner` inserisce anim dopo ogni tap (anche same-screen), Back, InputText→Tap; `ensureAnimationWaits` anche in `sanitizeForPlay`; Play attende **UI stabile ≥650ms** (`waitForAnimationToEnd`) non solo delay fisso
  - **Quiescenza REC:** `RecordingTelemetry.onContentChanged` → `QuiescenceGap` guida timeout
  - **Soft-fail onesti:** Assert/Tap Required falliscono; popup KYC → `optional` via `OptionalStepPolicy`
  - **Vault pre-Play** se PIN/password mancanti; **fail-rate** promuove ramo catena dopo 2 fail
  - **Overlay REC:** UNDO ultimo step, OPT (ultimo tap optional)

- **M2 Track A (27 luglio):**
  - **A5 selector chain:** `SelectorCandidate` + `SelectorRanker.buildChain` / `attachChains`; Play prova la catena in ordine; YAML comment `# fallbackChain`; auto-heal `FlowStore.applySelectorWins` dopo Play
  - **A6 ghost tap:** `dropGhostTapsAfterScrollOrIme`; section header generico `isSectionHeaderLabel` (non solo etichette AXA)
  - **A7 lint autofix:** `FlowLintAutoFix` inserisce wait dopo submit e arricchisce blind wait lunghi in pipeline
  - **A8 secrets:** export `${PIN}` / `${PASSWORD}`; `CredentialVault` + editor **Vault**; Play resolve da SharedPreferences
  - **Validate:** `FlowPlayer.validate` (find-only) + bottone **Valida** in `FlowEditScreen`

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
