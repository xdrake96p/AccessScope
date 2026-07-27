# Piano — Creazione test Maestro + Scansione (senza regressioni vs v1.3.0)

> **Stato:** in corso · **M0+M1 chiusi in codice** · **Base:** commit `4048056` (branch `restyle`) · **Baseline anti-regressione:** tag `v1.3.0`
> **Regole vincolanti:** `.cursor/rules/maestro-algorithm-learning.mdc`, `project-maintenance.mdc`.

### Progresso

| Milestone | Stato | Note |
|-----------|-------|------|
| **M0** R1–R3 | fatto (codice) | `tools/compare_scan_outputs.py`, `reports/golden/` procedura + fixture, contract schema sessione + bridge IDs/status |
| **M0** R2 cattura JSON Nexi | pending device | richiede APK v1.3.0 + 7 flussi manuali |
| **M0** R4 feature flag | parziale | confidence gate già toggle Settings; altri flag al bisogno |
| **M1** A1 lint | fatto | `FlowLinter` + badge editor |
| **M1** A2 scroll coalesce | fatto | `ScrollCoalescer` in pipeline |
| **M1** A3 round-trip + compat | fatto | `MaestroYamlRoundTripTest`, `docs/MAESTRO_COMPAT.md` |
| **M1** A4 Maestro CLI | fatto (script) | `tools/verify_yaml_with_maestro_cli.sh` (manuale) |
| **M1** B1 telemetria gate | fatto | `ReportHelper.confidenceGateStats` + sezione reliability MD |
| **M2+** | pending | fallback chain, wait adattivi, editor drag, … |

---

## 1. Contesto (stato attuale)

**Maestro Beta** (`4048056`): recorder azioni via a11y (`ActionRecorder`), pipeline di ottimizzazione (`FlowOptimizationPipeline` + `optimization/`: PopupClassifier, NoiseActionFilter, SelectorNormalizer/Ranker, TransitionTimingAnalyzer, WaitPlanner), player in-app (`FlowPlayer`, 1066 righe) con step opzionali e cold launch, editor step (`FlowEditScreen`) con insert/duplica, lista flussi (`FlowsScreen`), export/import **YAML compatibile Maestro** (`MaestroYamlExporter/Importer`), overlay REC/Play, mutex scan↔recorder (`ScanRecorderMutexPolicy`), intelligence da cronologia scan (`ScanIntelligenceProvider`). Test JVM: `ActionRecorderTest`, `FlowOptimizerTest`, exporter/importer, mutex, optimization.

**Scansione** (dopo `a3ef3f0`): confidence gate (`ViolationConfidencePolicy`, toggle in Settings), attribuzione fingerprint per TalkBack/passed nel report dinamico. Benchmark Nexi iter. 10: **6 TP, score 92, 180 OK** (report `170543`).

**Baseline v1.3.0:** tag git `v1.3.0` — comportamento di scansione e contratti (bridge API, schema sessioni, PDF) da non regredire.

## 2. Obiettivi

1. **Track A — Creazione test Maestro:** flussi più affidabili al primo colpo (meno editing manuale), selettori più robusti, editor più veloce, export YAML verificato contro Maestro CLI reale.
2. **Track B — Scansione:** meno rumore residuo, più stabilità screenshot/copertura, nessuna variazione di output non misurata.
3. **Vincolo trasversale:** ogni cambiamento è **dimostrabilmente equivalente o migliore** rispetto a v1.3.0 su benchmark e contratti.

### Non obiettivi
- Navigazione automatica dell'app target durante la scansione WCAG (la scansione resta **manuale**; il player Maestro è feature separata).
- Cambi di schema su bridge JSON o `ArchivedScanSession` (solo additivi e retrocompatibili).

## 3. Framework anti-regressione (M0 — prerequisito di tutto)

| # | Strumento | Dettaglio |
|---|-----------|-----------|
| R1 | **Golden master Nexi** | Script `tools/compare_scan_outputs.py`: confronta due export JSON (o reliability MD) di una stessa scansione-benchmark — uguaglianza insiemi `dedupeKey`, `score` identico, stesse schermate (`fingerprint`), stesso set di controlli OK per area. Tolleranza zero di default; deriva documentata solo se approvata. |
| R2 | **Baseline v1.3.0 catturata** | Build APK dal tag `v1.3.0`, 7 flussi manuali Nexi (come da `docs/reliability-benchmark.md`), output archiviati in `reports/golden/v1.3.0/` come riferimento fisso. |
| R3 | **Contract test bridge** | Test JVM su `ScanResultProvider` (status/latest/session: campi obbligatori, UUID validation, SecurityException per caller non autorizzati) + test schema JSON `ArchivedScanSession` (campi v1.3.0 sempre presenti). |
| R4 | **Feature flag** | Ogni variazione di comportamento (gate, euristiche, player) dietro toggle con **default = comportamento v1.3.0**, esposto in Settings solo se utile all'utente (pattern già usato per il confidence gate). |
| R5 | **Maestro come harness** | Registrate le 7 flussi Nexi con il recorder: replay su APK v1.3.0 e su APK corrente → confronto output con R1. Dogfooding del player come motore di regressione (prerequisito: A2/A3 stabili). |
| R6 | **CI** | Job esistente (`release.yml`) mantiene JVM tests + assembleDebug; aggiungere step opzionale (manuale/nightly) con emulatore per smoke Maestro self-test sull'app AccessScope. |

**Definition of done per ogni task successivo:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde (≥ test attuali + nuovi), golden master R1 senza diff non approvati, PROJECT.md + KDoc aggiornati.

## 4. Track A — Creazione test Maestro

### M1 — Quick win (editor e YAML)
- **A1. Lint fragilità in editor** (`FlowEditScreen`): badge warning su step con selettore debole (solo `pointPercent`, solo testo generico, `isStructuralContainerViewId`, id in `NOISE_ID_SUBSTRINGS`) e su tap "submit-like" senza wait successivo. Regole in `optimization/lint/FlowLinter.kt` + test JVM. *(Regola Cursor: generalizzare, non hardcode su singolo flusso.)*
- **A2. Coalescenza scroll → `scrollUntilVisible`:** in pipeline, N scroll consecutivi stessa direzione seguiti da tap su elemento non visibile all'inizio → un solo `ScrollUntilVisible` (con cap 20s). Test JVM su casi AXA/Nexi esistenti.
- **A3. Round-trip YAML garantito:** test JVM export→import→export idempotente su tutti i tipi `RecordedAction` (incl. optional/conditional, `conditionVisible*`); matrice di compatibilità comandi in `docs/MAESTRO_COMPAT.md` (tapOn, doubleTapOn, longPressOn, inputText, eraseText, scroll, swipe, pressKey, back, assertVisible/NotVisible, waitForAnimationToEnd, launchApp/stopApp, openLink, extendedWaitUntil).
- **A4. Verifica con Maestro CLI reale:** script `tools/verify_yaml_with_maestro_cli.sh` (emulatore + app demo controllata, es. app AccessScope stessa) che esegue `maestro test` sul YAML esportato e riporta esiti; esecuzione manuale/nightly, non bloccante.

### M2 — Robustezza selettori e registrazione
- **A5. Scoring selettori con fallback chain** (`SelectorRanker` + exporter): ogni step esporta una **catena ordinata** (id univoco stabile → contentDescription → testo esatto → testo in antenato cliccabile → pointPercent come ultima risorsa con warning lint). Il player la consuma in ordine con telemetria del ramo usato. Penalità per id volatili (regex: suffissi numerici lunghi, UUID, `compose_` hash) e bonus per id visti stabili in `ScanIntelligenceProvider` (viewId presenti su più schermate/sessioni).
- **A6. Dedup tap fantasma post-scroll/keyboard** in `NoiseActionFilter`: tap su stesse bounds ±8dp entro 300ms da evento scroll o hideKeyboard → scartato; tap su chrome sistema già coperto, estendere a gesture nav bar (swipe-home) se rilevata.
- **A7. Wait adattivi** (`WaitPlanner` + `TransitionTimingAnalyzer`): oltre alla regola submit-like esistente, usare la **quiescenza** (`TYPE_WINDOW_CONTENT_CHANGED` che si ferma per ≥700ms) per collassare wait ridondanti e inserirne di mancanti dopo loader (`isNoiseViewId` che scompare). Test JVM su tracce eventi registrate.
- **A8. Input sicuri:** masking opzionale dei valori `inputText` su campi `isPassword` nell'YAML esportato (`${PIN}`/`${PASSWORD}` placeholder + nota in testa al file), coerente col bridge ridotto già in v1.3.1.

### M3 — Editor avanzato e telemetria
- **A9. Reorder drag & batch ops:** riordino step via drag (lista già LazyColumn), selezione multipla per delete/duplica/set-optional; undo singolo livello.
- **A10. Preview visiva per step:** thumbnail della schermata associata allo step (da `Scan+Flusso`/evidence se disponibile, altrimenti capture al Play) affiancata nel dettaglio step dell'editor.
- **A11. Learning loop telemetria:** `FlowTelemetry` già persiste esiti per step → aggregare in `FlowStore` tasso di successo per selettore; se uno step fallisce ≥2 volte con lo stesso ramo, l'editor propone il ramo alternativo (suggerimento non automatico). Test JVM su aggregatore.
- **A12. Scan+Flusso combinato (valutazione):** oggi `ScanRecorderMutexPolicy` serializza scan e recorder; studiare modalità "Registra mentre scansioni" (stesso a11y service, eventi in fan-out) con UI chiara dello stato combinato. Output: violazioni annotate con lo step corrente. Se fattibile senza toccare l'engine → M4 separato; altrimenti documentare il perché resta mutex.

## 5. Track B — Scansione

### M1–M2 (in parallelo ad A)
- **B1. Telemetria confidence gate:** nel reliability MD aggiungere contatori per tipo/gravità dei findings **demotati** dal gate (cosa avremmo perso se il gate fosse attivo), così da misurare FP evitati vs FN introdotti sul benchmark Nexi. Default gate: come da v1.3.0 finché i dati non giustificano il cambio (R4).
- **B2. Tuning attribuzione fingerprint:** casi residui in `DynamicReportHelper` (TalkBack/passed assegnati al frame sbagliato in scroll veloce); test JVM su finestre di attribuzione; confronto golden master obbligatorio.
- **B3. Stabilità screenshot:** retry con backoff su `ERROR_TAKE_SCREENSHOT` transitori, rilevamento bitmap nero/blur (già `SCREENSHOT_BLOCKED`) con secondo tentativo prima di marcare protetta, edge case API 34/35 documentati.
- **B4. Performance e batteria:** analisi incrementale per fingerprint (skip analisi se la schermata è invariata rispetto all'ultimo frame — già parzialmente coperta da fingerprint/dedupe, verificare hit-rate), budget eventi (coalescing `TYPE_WINDOW_CONTENT_CHANGED` bursty), log ridotto in release.
- **B5. Suggeritore di copertura (UI-only):** nella dashboard live, elenco delle sezioni visitate raramente/da poco (da `visitedScreens` count) per guidare la navigazione manuale — nessuna automazione, solo suggerimento.

## 6. Milestones e stime

| Milestone | Contenuto | Stima | Verifica |
|---|---|---|---|
| **M0** | R1–R4: golden master, baseline v1.3.0, contract test, flag framework | 2g | Script R1 verde su v1.3.0 vs v1.3.0 |
| **M1** | A1 lint, A2 scroll coalescing, A3 round-trip + compat doc; B1 telemetria gate | 3g | Test JVM nuovi ≥8, golden master pulito |
| **M2** | A5 fallback chain, A6 dedup, A7 wait adattivi, A8 masking; B2 fingerprint, B3 screenshot retry | 4g | Replay flussi Nexi (R5) con success rate ≥ 90%, golden master pulito |
| **M3** | A9–A11 editor/telemetria, A4 CLI check; B4 performance, B5 coverage hint | 3g | Replay stabile, nessun peggioramento tempi scan su Nexi |
| **M4 (valutazione)** | A12 Scan+Flusso combinato | da stimare | Solo se M0–M3 chiuse |

## 7. Metriche di successo

- **Maestro:** success rate replay flussi Nexi ≥ 90% senza editing post-registrazione; step con selettore "forte" (id/cd) ≥ 80%; YAML esportati eseguiti da Maestro CLI senza errori di parsing 100%.
- **Scansione:** golden master Nexi invariato rispetto a v1.3.0 (6 TP, score 92) salvo miglioramenti approvati e documentati; demozioni del gate con rapporto FP:FN ≥ 4:1 su benchmark.
- **Regressione zero:** tutti i contract test verdi, nessun diff non approvato in R1, 134+ test JVM sempre verdi.

## 8. Rischi e mitigazioni

| Rischio | Mitigazione |
|---|---|
| Cambi al player rompono flussi salvati dagli utenti | Versioning formato `*.actions.json` (campo `formatVersion`), migrazione in lettura, test round-trip su file v1 |
| Selettori troppo aggressivi → tap su elemento sbagliato | Fallback chain con telemetria + optional step per rami deboli; lint prima del Play |
| Golden master troppo rigido blocca miglioramenti legittimi | Procedura di "approvazione deriva": diff allegato al task + aggiornamento baseline solo via step esplicito |
| Telemetria/feature nuove pesano su batteria durante scan | Misura tempo/analisi prima-dopo (logcat timing su Nexi), budget: nessun peggioramento >5% |
