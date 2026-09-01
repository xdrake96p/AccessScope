# AccessScope — Documento di progetto (living doc)

> **Aggiornare questo file** ad ogni iterazione significativa: nuove feature, fix di precisione, cambi UI, metriche benchmark, commit rilevanti.

**Repository:** [github.com/xdrake96p/AccessScope](https://github.com/xdrake96p/AccessScope)  
**Package:** `dev.accessscope.scanner`  
**Branch principale sviluppo:** `develop`  
**Branch release stabile:** `main`  
**Ultimo aggiornamento:** 1 settembre 2026 (Maestro: picker rubrica/IBAN — sessione REC, heal pipeline, Play)

### Usabilità Maestro: card flusso e editor step più puliti (1 settembre 2026)

Piano `docs/restyle/PIANO_RESTYLE.md` (fasi F0–F6, colori/font/navigazione) già completato e mergiato
(commit `80858d6`). Da lì la pipeline Maestro è cresciuta molto (recorder, ottimizzazione, report play) e
le schermate che la espongono avevano accumulato debito UX: card flusso con 9 controlli in fila senza
etichette, un gesto nascosto (long-press = "Play pulito"), eliminazione senza conferma, editor step con
campi liberi da form di debug. Nessuna modifica a `recorder/`, `service/`, pipeline di ottimizzazione o
formato dati — solo Compose UI e stringhe (stessa regola d'oro del restyle).

- **`FlowsScreen.kt` — `FlowListCard`**: azione primaria `Play` + menu overflow (⋮, `DropdownMenu`) con
  Modifica step, Rinomina, Report test, Scan+Flusso, Play pulito (ora scopribile, con sottotitolo, non più
  solo long-press nascosto), Anteprima YAML, Scarica, Condividi, Elimina. `Elimina` ora chiede conferma
  (`AlertDialog`) prima di cancellare il flusso — prima cancellava subito, senza rete di sicurezza,
  inconsistente col resto dell'app (Settings chiede conferma per "Eliminare la cronologia?").
- Banner in cima a `FlowsScreen` ridotto da un paragrafo di 5 frasi a una riga + link "Come funziona"
  (stesso testo completo, ora a richiesta invece che sempre a video).
- **`FlowEditScreen.kt` — `StepEditDialog`**: il campo libero "direction UP/DOWN/LEFT/RIGHT" (`Scroll`,
  `ScrollUntilVisible`) è ora un selettore a chip (`DirectionSelector`, nuovo composable) — stessa stringa
  scritta a valle, zero cambi al parsing. Aggiunto `supportingText` esplicativo sui campi più criptici
  (`id`, `contentDescription`, `point x,y %`, swipe start/end).
- Verificati (nessun problema trovato, nessuna modifica) `ScanHistoryScreen.kt` e
  `ViolationDetailScreen.kt`: nessuna azione distruttiva senza conferma, nessuna riga di icone
  sovraffollata.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` — compilazione pulita; 2 fallimenti
preesistenti e non collegati in `PlayReportCodecTest` (`org.json.JSONObject` non mockato in JVM unit test,
feature "Report test" in corso d'opera, fuori scope di questo lavoro).

### Maestro — picker rubrica/IBAN in registrazione (1 settembre 2026)

Su form BONIFICO SEPA (Banca MPS / Nexi): tap su icona rubrica/IBAN e selezione voce lista
non venivano registrati (redirect icona→campo, `editable_skip`, assert orphan, Play bloccato ~step 26).

**Registrazione (`ActionRecorder` + `PickerSession`):**

- **`PickerSession`**: stato esplicito Closed → Opening → Open; abilita tap lista anche se l'assert sheet è scartato come orphan.
- **`picker_selection_rec`**: selezione picker via `TYPE_VIEW_TEXT_CHANGED` sul campo beneficiario/IBAN (Nexi non emette click lista) → tap icona sintetico + tap voce.
- **`opensSelectionPicker`**: riconosce `img_search_contact` anche su `ImageView` non clickable.
- **`isPickerBackedViewId`**: campi beneficiario/IBAN per id view.
- **`isPickerListLabel` / `looksLikePickerListItem`**: tap su beneficiario/IBAN in sheet (testo o contentDescription), bypass `editable_skip` con picker aperto.
- **`picker_field_tap_promoted_to_icon`**: tap sul label campo con icona picker → registra tap icona (viewId + point).
- **`isSelectionPickerOverlay`**: rilevamento overlay per titolo sheet + dismiss per id (`img_close`, …).

**Pipeline (`PickerFlowHealer`, `NoiseActionFilter`, `WaitPlanner`):**

- **`dropOrphanPickerAsserts`**: Play non fallisce su assert RUBRICA/SELEZIONA IBAN senza apertura.
- **`ensurePickerOpenBeforeSelect`**: prima di tap voce lista senza icona nel lookback, reinserisce tap sul campo form.
- **Wait 1200 ms** dopo tap icona picker.

**Play (`FlowPlayer`):**

- **`waitForInputTarget`**: scroll verso il basso fino a 5 volte prima di fallire (es. `importo_currency` sotto fold form SEPA).
- **`recoverFromAccidentalPickerOverlay`**: disabilitato per tap icona picker e voci lista.
- **`tryPickerIconTap`**: fallback tap label campo → icona sibling per flussi legacy.

Test: `PickerFlowHealerTest`, `FieldInputTargetResolverTest`, `FlowOptimizerTest.sanitizeForPlay_*`.

### Maestro — report esecuzione test (1 settembre 2026)

Ogni **Play** o **Validate** genera un report persistente `{id}.reports.json` con:

- metadati run (data, durata, app, cold launch);
- **dettaglio step** (descrizione, dati mascherati, OK/KO/skip optional);
- note CI / divergenze;
- condivisione testo via **Condividi** dalla lista flussi (icona report).

In lista: badge **Ultimo test: OK/KO** con conteggio step. **Rinomina** flusso dalla lista
(icona matita nome) senza aprire l’editor.

### Maestro — pad PIN: una cifra = uno step (1 settembre 2026)

Premendo 6 volte «1» sul tastierino PIN compariva un solo step (tap deduplicato + coalescenza
in un `inputText`).

- **`ActionRecorder`**: niente debounce su tap pad numerico (ogni cifra conta).
- **`dedupeTaps`**: non collassa tap pad consecutivi sullo stesso tasto.
- **`preservePinPadDigitTaps`**: run pad → N `tapOn` (**tutti Required**).
- **`dropDuplicateTapsAcrossWaits`**: non collassa tap pad identici separati da wait (bug: 6×`uno` → 1 step).
- **`WaitPlanner`**: niente wait tra cifre pad consecutive.
- **Play**: fallback append su `pincode` se slot `editN` assenti; errore se cifra pad non inserita.
- **Slot `edit1`…`edit6`**: una cifra per slot resta N step; merge solo se ogni slot ha lo
  stesso codice completo (≥4 cifre). Tap pad ridondanti eliminati solo dopo codice completo.

### Maestro — scroll spurî: filtro più aggressivo (1 settembre 2026)

Scroll isolati (layout post-tap, IME, launch, solo `fromIndex`/`toIndex` senza delta) finivano
nello YAML anche quando non servivano al replay.

- **`ActionRecorder`**: ignora scroll entro 800ms da un tap; su API 28+ richiede delta fisico
  ≥12px (niente scroll da RecyclerView bind/prefetch).
- **`NoiseActionFilter.dropNoiseScrolls`**: elimina scroll **non** in run multiplo (≥2 scroll
  stessa direzione) se preceduti da hideKeyboard/launch/back/waitForAnimation o inseriti tra due tap /
  subito dopo un tap (<900ms).
- Run multipli reali (liste lunghe AXA/Nexi) restano intatti — vedi test regressione.


Registrazioni con PIN inserito più volte (es. `1111` + conferma) mostravano un solo
`inputText` nello YAML: il recorder sovrascriveva il pending senza flush e la pipeline
collassava per gap temporale.

- **`ActionRecorder`**: flush immediato su stesso campo + stesso testo completo
  (`duplicate_complete`), senza attendere gap — password login resta coalesceda in un solo `****`.
- **`FlowOptimizationPipeline`**: coalesce **solo** digitazione incrementale (prefisso stretto);
  duplicati completi restano due step; il secondo (e successivi) marcato **`Optional`**.
- **`InputText.executionMode`**: export YAML `optional: true`; Play salta l’input se il campo
  non è più disponibile.
- Test: `FlowOptimizerTest` (PIN×2, YAML con `optional`, campo generico non merge).

### Riscrittura pipeline Maestro — Fase 3: `ZeroEditGate` impara a riconoscere step spariti (1 settembre 2026)

`ZeroEditGate.evaluate` valuta solo la lista **già ottimizzata** — nessun confronto con la lista
grezza pre-ottimizzazione — e `FlowLinter` non ispeziona affatto `RecordedAction.Scroll`. Il gate
di qualità pre-salvataggio dell'app non aveva modo di accorgersi se 4 scroll diventano 1 senza
una `ScrollUntilVisible` a spiegare la riduzione (esattamente il bug reale di stanotte).

- Nuova `FlowOptimizationPipeline.auditScrollCardinality(before, after): List<String>`
  (delegata via `FlowOptimizer.auditScrollCardinality`): confronta scroll grezzi vs scroll +
  `ScrollUntilVisible` finali. Nessun avviso se sopravvive almeno una `ScrollUntilVisible` (è
  esattamente il suo scopo, collassare un run in un solo gesto mirato — non è una perdita).
  Avviso solo quando gli scroll diminuiscono **senza** nessuna promozione a spiegarlo.
- Chiamata da `FlowStore.saveFlow` subito dopo `optimize()` — non dentro `ZeroEditGate`, che
  resta per la sua responsabilità già documentata ("azioni già passate da optimize"); solo
  `FlowStore` ha sotto mano sia la lista grezza sia quella ottimizzata. Gli avvisi confluiscono
  nello stesso `ZeroEditReport.issues` mostrato all'utente, nuovo codice
  `SCROLL_CARDINALITY_LOSS`, severità Warning (non bloccante).
- 3 test nuovi in `FlowOptimizerTest.kt`: nessun avviso con promozione a `ScrollUntilVisible`;
  avviso quando gli scroll spariscono senza promozione (stessa fixture del bug reale già usata
  nel test di regressione di `dropNoiseScrolls`); nessun avviso se non c'erano scroll da
  confrontare.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Riscrittura pipeline Maestro — Fase 2: un controllo "tap chrome" invece di quattro copie (1 settembre 2026)

Verificando il codice per consolidare `isNoiseTap`/`isSystemChromeTap`/`isForeignUiPackage`
(segnalati come "3 versioni indipendenti" dalla mappa architetturale di Fase 1), trovato che
**non erano affatto divergenti**: sono già una gerarchia ben fatta in `MaestroSelectorHeuristics`
(`isForeignUiPackage` ⊂ `isSystemChromeTap` ⊂ `isNoiseTap`), tutti i chiamanti (capture, optimize,
sanitizeForPlay) invocano la stessa funzione condivisa. Il vero problema era una **quarta copia**
non contata nella mappa iniziale: `MaestroYamlExporter.export()` aveva un proprio controllo
inline `isSystemChromeTap` (righe 45-66) — provabilmente morto, dato che l'unico chiamante di
produzione (`FlowStore.writeArtifacts`) passa sempre l'output di `sanitizeForPlay`, che ha già
rimosso qualunque tap che l'exporter avrebbe scartato di nuovo.

- Rimossa la copia morta nell'exporter.
- Nuovo test `export_trustsUpstreamFiltering_doesNotReFilterChromeTapsItself` in
  `MaestroYamlExporterTest.kt`: documenta esplicitamente il contratto (l'exporter si fida del
  filtraggio a monte) — un canary se in futuro qualcuno chiamasse `export()` bypassando la
  pipeline di sanitizzazione.
- **Rimandato**: le due implementazioni duplicate di "riempi il wait cieco col prossimo target"
  (`WaitPlanner.attachBlindWaitsToNextTarget` vs `FlowLintAutoFix.waitForNextTarget`) sono
  risultate più divergenti del previsto (soglie ed esclusioni diverse, ricostruzione del `Wait`
  diversa) — fonderle ora rischierebbe di cambiare comportamento su casi limite non coperti dai
  test esistenti. Rimandato a un giro dedicato con più tempo per verificare ogni differenza.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Riscrittura pipeline Maestro — Fase 1: idempotenza delle fasi condivise (1 settembre 2026)

Richiesta esplicita dell'utente dopo due bug reali sullo stesso pattern (`dropNoiseScrolls`,
`ScrollCoalescer`): non più fix caso per caso, riscrivere l'architettura. Mappa completa via 3
agenti di esplorazione (capture, optimize/sanitizeForPlay, export) — confermato: `optimize()` e
`sanitizeForPlay()` condividono ~10 delle loro ~20 funzioni `NoiseActionFilter`, ognuna con
un'assunzione implicita mai scritta ("sono la prima passata") mai verificata.

- **Tentativo iniziale scartato durante l'implementazione**: unificare le fasi condivise in
  un'unica lista con un ordine comune. Ha rivelato che `optimize()` e `sanitizeForPlay()`
  eseguono le stesse funzioni in **ordini reciprocamente diversi** oggi — forzare un ordine
  comune senza saperne il motivo storico avrebbe rischiato un cambio di comportamento
  silenzioso. Scartato, l'ordine di ciascuna pipeline resta quello di sempre.
- **Fatto**: entrambe le pipeline riscritte da nesting profondo a catena lineare di `val` (una
  fase per riga, leggibile dall'alto in basso), con commento `[condivisa]` su ogni fase che gira
  in entrambe — il rischio "questa gira due volte con vicini diversi" ora è visibile a chi legge,
  non va più dedotto confrontando due corpi di funzione a mano.
- **Nuovo `SharedNoiseStageIdempotencyTest.kt`**: verifica meccanicamente, su due fixture
  modellate sui flussi reali di stanotte (AXA multi-scroll+PIN+popup, foreign-UI/tastiera), che
  ciascuna delle 6 fasi condivise (`dropForeignUiActions`, `normalizePinOrOtpSlotInputs`,
  `dropSpuriousRatingAsserts`, `dropGhostTapsAfterScrollOrIme`, `dropNoiseScrolls`,
  `dropNoiseWaits`) dia lo stesso risultato eseguita una o due volte di fila. Tutte e 6 passano
  già (il fix di stanotte a `dropNoiseScrolls` era sufficiente) — il valore del test è essere una
  **guardia permanente**: avrebbe scoperto da solo entrambi i bug di stanotte prima che
  arrivassero su un flusso reale, e blocca lo stesso pattern in futuro su qualunque fase futura.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde, nessuna regressione sui
150 test esistenti (il refactor di questa fase è puramente di leggibilità/osservabilità, zero
cambi di comportamento).

**Prossime fasi pianificate** (`/Users/davidevisconti/.claude/plans/tingly-bubbling-sprout.md`):
Fase 2 (un solo controllo "tap rumoroso" invece di 3 copie), Fase 3 (ZeroEditGate impara a
riconoscere step spariti), Fase 4 (pulizia stato `ActionRecorder`), Fase 5 (precisione export
`AssertVisible`).

### Maestro: scroll multipli persi in export — il bug dietro il "ritocco a mano" (1 settembre 2026)

Segnalazione utente: la creazione dei test Maestro non è precisa, "va ritoccata parecchio a mano
invece dovrebbe copiare passo passo i passaggi fatti in registrazione". Analizzando una nuova
registrazione AXA reale (`6fe74baf`) trovato il bug più impattante di tutta questa serie: **4
scroll reali, necessari a raggiungere una polizza in fondo a una lista lunga, diventavano 1 solo
scroll nello YAML esportato** — con 4 `waitForAnimationToEnd` orfani lasciati dietro come prova
del danno. Un test così non raggiunge mai il target reale: va corretto a mano ogni volta.

Causa: `NoiseActionFilter.dropNoiseScrolls` gira **due volte** nella pipeline (in `optimize()` e
di nuovo in `sanitizeForPlay()`, quest'ultima ora eseguita anche per l'export dopo l'unificazione
pipeline della settimana scorsa). Tra le due passate, `WaitPlanner` inserisce un
`WaitForAnimation` dopo ogni scroll. Alla seconda passata, il filtro camminava indietro
tollerando solo altri `Scroll` consecutivi per trovare "cosa ha innescato" questo scroll — si
fermava sul `WaitForAnimation` appena inserito e trattava come "rumore tipico post-PIN" ogni
scroll della sequenza tranne il primo, anche quando la sequenza era una ricerca reale e
deliberata attraverso una lista lunga.

- La camminata indietro ora tollera anche `Wait`/`WaitForAnimation`/`HideKeyboard` interposti
  (riusa `isWaitLike`, già esistente per `dropDuplicateTapsAcrossWaits`), per trovare il vero
  innesco della sequenza indipendentemente da quante volte il filtro è già passato su azioni via
  via arricchite da stadi successivi della pipeline. Semplificato anche il codice: i tre rami
  `Wait`/`WaitForAnimation`/`HideKeyboard` nel controllo finale erano diventati irraggiungibili
  (il salto li assorbe già prima), rimossi.
- Nuovo test di regressione in `FlowOptimizerTest.kt` che riproduce esattamente il pattern reale
  (4 scroll con `WaitForAnimation` intreccati, target in fondo) — verifica che tutti e 4
  sopravvivano. Il test esistente sul caso stretto (scroll consecutivi subito dopo un PIN, senza
  wait in mezzo) resta verde invariato.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde. Verifica reale sul device
in corso (reinstall + nuova registrazione con scroll multipli).

### Maestro: replay che si fermava a metà — diagnosi da device reale + fix (1 settembre 2026)

Segnalazione utente: "la replica dei test Maestro salta dei passaggi". Recuperati da un flusso
AXA registrato (`45b161e4`, 48-51 step) `actions.json`, lo YAML esportato e **il logcat di 6
tentativi di Play reali**. Prova diretta, non un'ipotesi: in **5 tentativi su 6** il player
entra in un loop di `scroll_noop` (~40 volte, ogni ~0.45s) su uno `scrollUntilVisible`, poi
timeout, poi `step_fail` — un errore bloccante che interrompe l'intero flusso. Tutto ciò che
segue quello step non viene mai eseguito: è questo che si percepisce come "salta dei passaggi",
non una perdita di singoli step sparsi.

- **`FlowPlayer.scrollUntilVisible`**: la direzione salvata nello YAML (l'ultima scroll del run
  collassato da `ScrollCoalescer` in registrazione) può non essere quella che serve a raggiungere
  il target da uno stato fresco dell'app — es. l'utente aveva corretto una sovra-scrollata
  durante la registrazione, ma da `launchApp` la lista parte già in cima e quella correzione (UP)
  non porta a nulla in replay. Ora: dopo 2 `scroll_noop` consecutivi, la direzione si inverte
  automaticamente invece di martellare quella fallimentare fino al timeout. Se serve invertire,
  viene registrata una nota CI (`PlayOutcome.divergences`) — il CLI reale userebbe solo la
  direzione dello YAML e potrebbe fallire dove il Play in-app si è auto-corretto.
- **`NoiseActionFilter.dropDuplicateTapsAcrossWaits`**: rimuoveva due tap con lo stesso testo
  separati solo da wait, **senza alcun limite di tempo** — sui dati reali i doppi tap umani
  genuini erano tutti a 0.8–1.8s, ma senza un tetto la stessa regola avrebbe unito erroneamente
  due tap sullo stesso testo separati da un caricamento lento di decine di secondi. Aggiunto un
  tetto di 4s (`DUPLICATE_TAP_MAX_GAP_MS`).
- Non toccato: perché la direzione sbagliata viene registrata in origine (`ScrollCoalescer`) — il
  fix a runtime rende il problema irrilevante a prescindere; un secondo fallimento osservato una
  sola volta (`Tap non trovato text=Le mie garanzie`, tab di navigazione senza `viewId`) — dati
  insufficienti per una correzione mirata, segnalato come rischio noto.
- Test nuovi: `FlowPlayerScrollDirectionTest.kt` (funzione pura `oppositeScrollDirection`, dato
  che `FlowPlayer` richiede un `AccessibilityService` live non testabile su JVM); 2 test aggiunti
  a `NoiseActionFilterGhostTest.kt` (gap reale osservato → merge invariato; gap oltre soglia →
  entrambi i tap mantenuti).

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde. Verifica reale sul device
in corso (reinstall + replay dello stesso flusso AXA).

### Pre-demo — UI: "Play senza credenziali" (1 agosto 2026)

Scoperto in demo: il dialog credenziali prima del Play (`FlowsScreen.kt`, quando il flusso usa
`${PIN}`/`${PASSWORD}` e il vault è vuoto) era un blocco vero — "Salva e Play" o "Annulla",
nessuna via per riprodurre il flusso senza inserire credenziali reali.

Con il fix di stasera (playback onesto, blocco precedente) è ormai sicuro farlo: `FlowPlayer`
non salta più il `SET_TEXT` in silenzio, lo annota come nota CI nel messaggio finale. Aggiunto
un terzo pulsante "Play senza credenziali" nel dialog, che chiama `startFlowPlayback` senza
salvare nulla nel vault — il playback prosegue e il risultato mostra `· N note CI`.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Pre-demo — Esecuzione: il playback non mente più, verde annotato con note CI (1 agosto 2026)

`FlowPlayer` ha rami "morbidi" che il `maestro` CLI non ha: su `${PIN}`/`${PASSWORD}` senza vault
popolato, `inputText` saltava il `SET_TEXT` e **ritornava normalmente** — un flusso di login
poteva risultare interamente verde in-app senza aver mai fatto login. Stessa dinamica per il
fallback a coordinate quando il selettore non risolve, il fallback digit sul pad PIN, il
gesture-invece-di-`ACTION_CLICK`, e il `waitUntil` con selettore che in timeout logga soltanto
(soft-fail) mentre `extendedWaitUntil` nel CLI fallisce l'intero flusso. Nulla di tutto questo
arrivava al risultato mostrato all'utente.

Scelta deliberata (non un fallimento netto): il pass/fail del playback **non cambia** — la demo
resta sicura — ma ogni ramo morbido viene annotato.

- `PlayOutcome.divergences: List<String>` (default vuoto, `isSuccess` invariato) — una nota per
  ognuno dei 5 punti sopra, con indice step e rimedio CI (es. `"step 12: segreto \${PASSWORD} non
  risolto → in CI serve maestro test -e PASSWORD=..."`).
- `AccessScopeApp.kt`: il messaggio di fine playback appende `· N note CI` quando presenti,
  senza toccare il ramo di errore.
- 3 test nuovi in `PlayOutcomeTest.kt` (nuovo file, primo test sul package `recorder/model/`):
  le divergenze non alterano mai `isSuccess`, default vuoto, un errore resta un errore
  indipendentemente dalle divergenze presenti.

**Limite dichiarato:** `FlowPlayer` richiede un `AccessibilityService` live, non testabile su
JVM — la verifica dei 5 punti di divergenza è il playback reale sul device, non uno unit test.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Pre-demo — Maestro: scroll nudi promossi a `scrollUntilVisible` mirato (1 agosto 2026)

Il flusso reale su it.nexi.bff/MPS conteneva tre `- scroll` di fila senza bersaglio, fragili in
CI. `ScrollCoalescer` sapeva già produrre `scrollUntilVisible`, ma solo se il run di scroll era
seguito **immediatamente** da un `Tap` con selettore — nel flusso vero, tra uno scroll e l'altro
c'era sempre un `waitForAnimationToEnd`, che interrompeva il conteggio del run prima ancora di
arrivare a 2 scroll consecutivi: la promozione non scattava mai.

- Il conteggio del run ora tollera `Wait`/`WaitForAnimation`/`HideKeyboard` interposti tra scroll
  della stessa direzione (non li conta come scroll, non interrompono la sequenza logica).
- Il bersaglio accettato dopo il run non è più solo `Tap`: anche `AssertVisible` e `InputText`
  (tutti espongono `viewId`/testo). Gli elementi interposti diventano ridondanti quando la
  promozione avviene (`scrollUntilVisible` incorpora già l'attesa implicita) e vengono scartati;
  se **non** c'è un bersaglio promuovibile alla fine, la sotto-sequenza viene riemessa
  esattamente com'era — mai un rischio di perdere informazione quando l'esito è incerto.
- 2 test nuovi in `ScrollCoalescerTest.kt` sul pattern reale (scroll→wait→scroll→wait→scroll→tap
  → `scrollUntilVisible`; stesso pattern senza bersaglio finale → invariato). I 7 test esistenti
  restano verdi senza modifiche.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde — include la validazione
reale con `maestro check-syntax` (`MaestroCliSyntaxValidationTest`).

### Pre-demo — Maestro: l'hint del campo non finisce più in `inputText` (1 agosto 2026)

Il bug più grave del giro: il flusso reale registrato su it.nexi.bff/MPS conteneva
`- inputText: "IBAN (obbligatorio)"` su un campo **mai toccato dall'utente** — l'esatto opposto
dell'obiettivo "zero-edit". Causa: `TextView.getTextForAccessibility()` di AOSP ricade sull'hint
quando il testo è vuoto, e il focus su un campo ancora vuoto (`TYPE_VIEW_FOCUSED`) passava quel
valore ad `ActionRecorder.updatePendingText` come se fosse testo digitato — `hintText` non era
mai letto in tutto il modulo `recorder/`.

- Estratta la decisione in una funzione pura `ActionRecorder.resolveTypedText(eventText,
  nodeText, hintText)`: un testo che coincide con l'hint (dal nodo o dall'evento) è trattato come
  campo vuoto, ricadendo nel path "flush pending" già esistente. Limite noto e accettato,
  documentato in KDoc: un testo digitato che coincide *alla lettera* con l'hint viene anch'esso
  scartato — impossibile distinguere i due casi con i soli campi `text`/`hintText`, e digitare il
  placeholder alla lettera è comunque irrealistico in pratica.
- 5 test nuovi in `ActionRecorderTest.kt`, tutti sulla funzione pura (niente
  `AccessibilityNodeInfo` reale/`ShadowAccessibilityRecord.setSourceNode`: il primo tentativo con
  l'iniezione di un nodo reale ha inquinato l'ordine di esecuzione di test successivi non
  correlati tramite lo stato statico di `ShadowAccessibilityNodeInfo` — la funzione pura evita il
  problema alla radice ed è comunque la copertura più precisa per questa logica).

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Pre-demo — Report dinamico: fingerprint uniti per sottoinsieme, non differenza≤1 (1 agosto 2026)

Stesso test reale su it.nexi.bff/MPS: 23 schermate uniche per 14 titoli reali. `canonicalize`
univa due fingerprint solo con differenza di chrome-id ≤1 elemento — insufficiente: la stessa
schermata "REGISTRA NUOVA UTENZA" è stata catturata con 5, poi 3, poi 0 elementi di chrome
(ogni insieme più piccolo un sottoinsieme proprio del precedente, mai un chrome diverso — la
raccolta si ferma su `!isVisibleToUser` durante una cattura a metà transizione). La regola a
differenza≤1 univa solo la coppia più vicina, lasciando comunque 3 fingerprint invece di 1.

- `isTransientChromeVariant`: sostituita la differenza simmetrica (`≤1`) con un controllo di
  sottoinsieme in entrambe le direzioni — generalizzazione stretta della regola precedente (una
  differenza di 1 implica sempre un sottoinsieme), non tocca la regola sui `tab:` che tiene
  separati i cambi di contenuto reali.
- 2 test nuovi in `ScreenFingerprintCanonicalizeTest.kt` (catena di sottoinsiemi 5→3→0 sul caso
  reale; chrome disgiunti — caso reale "RUBRICA" — che restano correttamente non uniti). Rimosso
  il test che assumeva il comportamento precedente (`tooManyChromeDifferences...`), la cui
  premessa è esattamente quella corretta da questo fix.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Pre-demo — Scanner: heading level dedotti dal font, non da isHeading (1 agosto 2026)

Secondo test reale su it.nexi.bff/MPS (stesso giorno, dopo il fix container generici): 17
`HEADING_LEVEL_SKIP`, incluso `"Ragione sociale (Obbligatorio)"` — un'etichetta di campo —
segnalata come salto `~H1 → ~H3`. Causa: Android espone solo un booleano `isHeading`, nessun
livello; `FocusOrderAnalyzer.analyzeHeadingLevels` ammetteva come candidato anche qualunque
TextView non cliccabile ≤12 parole (`looksLikeStructuralHeading()`) e ne deduceva il livello
dall'altezza del font — due label che differiscono di pochi px producevano salti fantasma.

- `FocusOrderAnalyzer.analyzeHeadingLevels`: candidati ristretti ai soli heading **dichiarati**
  (`isHeading == true`). Se un'app non dichiara heading, zero violazioni invece di livelli
  inventati. `looksLikeStructuralHeading()` resta invariato per l'unico altro uso (`HEADING_HIERARCHY`
  in `NodeStructureSingleChecker`, dove serve proprio a rilevare un titolo NON marcato heading).
- `PrecisionHeadingSkips.shouldSkipHeadingCheck`: nuova guardia con `PrecisionLabels.
  isRequiredFieldHint` (già esistente per il contrasto, mai collegata qui) — un'etichetta di
  campo obbligatorio non è mai un heading. Protegge anche `HEADING_HIERARCHY`, che passa dallo
  stesso helper.
- 3 test nuovi in `FocusOrderHeadingLevelTest.kt`.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Settimana 4 — TalkBack: corretti i falsi positivi su container generici (1 agosto 2026)

Test su device reale su una banking app (`it.nexi.bff`, MPS) dopo il collegamento al punteggio
di Settimana 3: 20 violazioni `SCREEN_READER_ANNOUNCEMENT`, **100% su classi container generiche**
(`RelativeLayout` 6, `ScrollView` 4, `ViewGroup` 4, `RecyclerView` 2, `ViewPager` 1,
`HorizontalScrollView` 1, `FrameLayout` 1, `LinearLayout` 1), zero su widget reali — punteggio
sceso 76→49 senza alcuna regressione reale, solo rumore appena reso scoreable. Causa doppia in
`TalkBackSimulator.isFocusCandidate`/`simulate`:

- `isScrollable` da solo qualificava un container come focus stop TalkBack. Non riflette il
  comportamento reale: `AccessibilityNodeInfoUtils.isActionableForAccessibility` (usata da
  TalkBack) non include `isScrollable` — un container scorre, ma TalkBack naviga direttamente sui
  figli, non si ferma sul contenitore solo perché scrolla. Coprivano da soli `ScrollView`,
  `HorizontalScrollView`, `RecyclerView`, `ViewPager` (8/20). Fix: `isScrollable` qualifica solo se
  il nodo ha già un nome accessibile proprio (contentDescription/text/hint) — cioè è stato reso un
  target intenzionale dallo sviluppatore.
- Per i container generici rimasti candidati perché anche `clickable` (righe/card cliccabili:
  `RelativeLayout`, `FrameLayout`, `LinearLayout`, `ViewGroup`, 12/20 restanti): `buildAnnouncement`
  valuta solo il nodo, mai i discendenti — ma TalkBack reale aggrega il testo dei figli non
  focalizzabili nell'annuncio del genitore quando lo mette a fuoco (es. riga cliccabile che avvolge
  una `TextView` con l'importo). Fix: nuovo `isGenericContainerWithLabeledContent`, che riusa gli
  stessi helper già usati per l'analogo problema su `MISSING_LABEL`
  (`PrecisionRules.hasLabeledDescendant`/`hasLabeledDescendantInScroll`, limitati a
  `isLayoutContainer`/`isScrollContainer`) — un container silenzioso non genera più finding se un
  discendente porta già un'etichetta reale.

Entrambi i fix sono mirati (nessuna blacklist per nome classe fine a sé stessa) e non toccano i
widget reali: un `ImageButton`/custom `View` cliccabile senza `contentDescription` e senza figli
resta segnalato come prima. 6 test nuovi in `TalkBackSimulatorTest.kt`: riga cliccabile e
`RecyclerView` con figlio etichettato (non più segnalati), `ViewPager` solo-scroll senza figli
(non più candidato al focus), `ScrollView` con contentDescription esplicita (resta candidato),
container vuoto senza discendenti e custom `View` cliccabile senza testo/cd (restano segnalati —
verifica che il fix non mascheri difetti reali).

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde (288 test, nessuna
regressione sulla suite esistente).

### Settimana 4 — Plugin: collegato il segnale di fine scansione (push invece di solo polling) (1 agosto 2026)

Pre-mortem plugin, causa 4 del post-mortem fluidità: "polling 2s mai usato... mentre l'app
emette già il broadcast SCAN_COMPLETE che nessuno ascolta — l'architettura push esiste, non è
collegata". Verificato: `AccessScopeApp.notifyScanComplete` invia l'Intent con
`setPackage(this@AccessScopeApp.packageName)` — **intra-app per costruzione**, mai osservabile
da adb/plugin esterno, quindi non è quello il canale da collegare. Il vero segnale già pensato
per l'automazione esterna è la riga logcat che la stessa funzione emette subito dopo, con un tag
il cui commento nel modulo app dice esplicitamente "Tag logcat per **automazione plugin**"
(`BridgeConstants.BRIDGE_LOG_TAG`) — mai realmente ascoltato da nessun client.

- `Adb.streamUntil` (delega a un nuovo `streamProcessUntil` libero, testabile senza adb/device):
  avvia un processo persistente (es. `logcat`), legge lo stdout riga per riga su un thread
  dedicato con `LinkedBlockingQueue.poll(remaining, ...)` — rispetta sempre la deadline anche se
  il flusso resta silenzioso (un `readLine()` diretto nel loop principale no), e termina sempre
  il processo figlio all'uscita.
- `ResultFetcher.waitForScanComplete` (`fetch-results --wait`): ora avvia un watcher
  `adb logcat -s AccessScopeBridge:I` in background come segnale **primario** — si sblocca
  appena vede `scan_complete`, invece di aspettare fino a 2s del prossimo poll. Il polling su
  `/status` resta come rete di sicurezza (ogni 5s, non più l'unico meccanismo) per i casi in cui
  logcat non è disponibile (permessi, emulatore headless) o la riga si perde. Se la scansione
  non è già in corso alla prima chiamata, ritorna subito senza attendere.
- 2 test nuovi (`AdbStreamUntilTest`, primo test JVM del modulo `cli/`): ritorno anticipato non
  appena la riga match arriva (non aspetta la vita intera del processo), timeout quando la riga
  non arriva mai — usano `sh -c` al posto di adb/logcat reali per restare test JVM puri.

Verifica: `./gradlew :cli:test :android-studio-plugin:compileKotlin` verde.

### Settimana 4 — Maestro: CLI-check come gate pre-release (1 agosto 2026)

Settimana 2 aveva validato manualmente lo YAML esportato con `maestro check-syntax` (CLI reale
installata in locale), ma `docs/MAESTRO_COMPAT.md` segnalava onestamente "CLI-check opzionale =
nessun segnale automatico": nessuna regressione di sintassi nell'exporter sarebbe stata
intercettata senza rifare quella validazione a mano ad ogni modifica.

- Nuovo `MaestroCliSyntaxValidationTest.kt`: costruisce un flusso realistico (login con PIN,
  tap point-only spurio, testo con parentesi da escapare), lo fa passare per la pipeline di
  produzione reale (`optimize` → `sanitizeForPlay` → `export`, la stessa unificata pochi commit
  fa) e invoca il binario `maestro check-syntax` reale via `ProcessBuilder` su un file temporaneo,
  verificando `exitCode == 0` e `"OK"` nell'output. Se il binario non è installato in locale
  (`~/.maestro/bin/maestro`, o `$MAESTRO_CLI_PATH`), il test si salta con `Assume` — non rompe
  `./gradlew test` per chi non ha Maestro installato sulla propria macchina.
- `.github/workflows/release.yml`: nuovo step "Install Maestro CLI" (installer ufficiale
  `get.maestro.mobile.dev`) seguito da "Run JVM tests" (`./gradlew testDebugUnitTest`) **prima**
  della build APK/plugin — se il test di sintassi Maestro fallisce, l'intera pipeline di release
  si ferma. Questo è il gate pre-release richiesto: da questo momento una regressione di sintassi
  nell'exporter blocca la pubblicazione della release invece di essere scoperta solo a mano (o,
  peggio, dal cliente che prova `maestro test` sullo YAML esportato).
- **`when:`/`runFlow` export**: confermato backlog dichiarato, non implementato in questo giro.
  `docs/MAESTRO_COMPAT.md` §"Condizionali" documenta già onestamente perché (richiede wrapping
  `runFlow` + supporto import per blocchi annidati, che l'importer oggi non ha) — nessun
  aggiornamento necessario, la nota è ancora accurata.

Verifica: `./gradlew :app:testDebugUnitTest` verde (nuovo test eseguito e passato con la CLI
reale installata in locale); sintassi di `release.yml` validata.

### Settimana 4 — Maestro: unificata pipeline export/Play ("verde in-app" ora predice "verde CI") (1 agosto 2026)

Backlog M2 esplicitamente rimandato in Settimana 2 per rischio/ampiezza: "due pipeline diverse,
`sanitizeForPlay` esegue un flusso diverso dallo YAML esportato". `FlowStore.saveFlow`/
`updateFlow` scrivevano lo YAML direttamente dalle azioni prodotte da `FlowOptimizer.optimize()`
(+ ZeroEditGate), mentre il Play in-app (`AccessScopeApp.kt`) applicava `FlowOptimizer.
sanitizeForPlay()` **solo** alla copia in memoria usata per l'esecuzione dal vivo — mai
ripropagato nel file YAML persistito. `sanitizeForPlay` fa lavoro reale che `optimize()` da solo
non fa (riordino overlay bloccanti, wait-target ciechi, wait di animazione, ri-normalizzazione
selettori, drop di tap "point-only" con coordinate ormai obsolete): un flusso verde in-app
poteva quindi esportare uno YAML privo di quegli stessi fix e fallire nel `maestro` CLI reale —
esattamente il rischio "verde in-app non predice verde in CI" del pre-mortem.

Non è stata una fusione completa delle due pipeline (rischio/ampiezza troppo alti per
un'unica sessione: `optimize()` cura la registrazione grezza con telemetria/scan-intel,
`sanitizeForPlay` fa una pulizia più leggera e non distruttiva su un flusso già curato — sono
input strutturalmente diversi). La correzione mirata: `FlowStore.writeArtifacts` ora passa le
azioni da `sanitizeForPlay` **prima** di generarci il YAML, così l'export riflette esattamente
la stessa trasformazione del Play in-app. `actions.json` (letto da editor e ri-ottimizzazione)
resta invariato — solo il YAML persistito cambia.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde (nessuna regressione sulla
suite esistente di golden-master/round-trip/ZeroEdit); nuovo test
`FlowStoreTest.saveFlow_exportedYaml_reflectsSanitizeForPlayNotJustRawActions` (un tap
point-only sopravvive a `optimize()` ma viene scartato da `sanitizeForPlay`, quindi deve sparire
dal YAML esportato); YAML generato dalla pipeline unificata (login flow multi-step, incluso un
tap point-only spurio) validato con `maestro check-syntax` reale — `OK`.

### Settimana 4 — Pulizia API morte + canale update plugin develop→main (1 agosto 2026)

- **Canale update develop→main**: `PluginUpdateChecker.kt` (Android Studio) e `extension.ts`
  (VS Code) cercavano gli aggiornamenti del plugin su `ref=develop`/`raw/develop`, ma per
  `docs/GIT_FLOW.md` §5 le release (ZIP/VSIX inclusi) vengono pubblicate **solo su `main`** dopo
  il tag. Un plugin aggiornato via "Plugin update" avrebbe potuto scaricare una build di
  `develop` non ancora rilasciata. Ora entrambi puntano a `main` (`RELEASE_BRANCH` esplicito
  invece del letterale sparso in 3 punti in `extension.ts`).
- **Versione extension duplicata**: `EXTENSION_VERSION = '1.0.8'` in `extension.ts` era un
  letterale da tenere manualmente sincronizzato con `"version"` in `package.json` — bump di uno
  solo dei due faceva riportare uno stato aggiornato/obsoleto falso al check-update. Ora letta a
  runtime da `context.extension.packageJSON.version` (`currentExtensionVersion()`), mai duplicata.
- **Impostazioni VS Code morte**: `accessScope.githubRepo` e `accessScope.autoUpdate`, dichiarate
  in `package.json` ma mai lette da nessun punto del codice (il repo è hardcoded, l'auto-update
  prima del launch non è implementato) — rimosse. Un utente che le avesse impostate si sarebbe
  aspettato un effetto che non esisteva.
- **`@Deprecated` dead code**: `AccessScopeMotion.navSlideTween`/`navSlideExitTween`,
  `ScreenshotCapture.secureOrUnusable`, `SecureScreenDetector.isSecureContext` — nessun chiamante
  residuo in tutto il repo, rimossi.
- **Artefatti `.vsix` randagi**: 3 pacchetti locali non tracciati (`AccessScope-1.0.1.vsix`,
  `accessscope-1.0.8.vsix`, `accessscope-1.0.9.vsix`) rimossi da `vscode-extension/` — già
  esclusi da `.gitignore`, byproduct riproducibile di `npm run package`, non finiti mai nel repo
  Git ma comunque disco sporco locale.
- **Non fatto in questo giro** (decisione esplicita): i parametri `packageName` ormai inutilizzati
  in `AppPrecisionProfiles.kt` e a cascata in `PrecisionHome`/`PrecisionContrast`/
  `PrecisionNavigation`/`PrecisionStructural`/`PrecisionLabels`/`SecureScreenDetector` — già
  documentati in KDoc come "Non usato: mantenuto per compatibilità di firma" dalla rimozione
  Nexi/AXA. Rimuoverli per davvero è un refactor a cascata su ~8 file (ogni rimozione rende
  potenzialmente inutilizzato anche il parametro del chiamante) per un guadagno puramente
  cosmetico (nessun bug, i default `""` sono innocui) — rimandato a una pulizia dedicata invece
  di rischiarlo di corsa in questa sessione.

Verifica: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` verde;
`:android-studio-plugin:compileKotlin` verde; `npx tsc --noEmit` verde su `vscode-extension`.

### Settimana 4 — B2: finestre di attribuzione fingerprint (1 agosto 2026)

Backlog M2 mai chiuso (`docs/PIANO_MAESTRO_E_SCANSIONE.md`): "casi residui in
`DynamicReportHelper` (TalkBack/passed assegnati al frame sbagliato in scroll veloce)". Con lo
scan live di oggi ogni violazione/finding/summary porta quasi sempre il proprio
`screenFingerprint`, quindi il percorso "senza fingerprint" in `buildFrames` è per lo più dati
legacy — ma quando si attiva, restava un bug reale: quando **due o più** visite condividevano lo
stesso titolo e avevano **entrambe** violazioni proprie per fingerprint, il bucket TalkBack/passed
senza fingerprint veniva attribuito a **ciascuna** di esse (la condizione era solo
`titleViolations.isNotEmpty()`, vera per tutte), duplicando lo stesso conteggio su più frame.

- Nuova `attributionOwnerByTitle`: tra le visite che condividono un titolo, se **esattamente
  una** ha violazioni proprie per fingerprint resta l'unico proprietario (comportamento storico,
  già testato); se sono **zero o più di una** (ambiguo), il proprietario diventa la prima visita
  per `visitIndex` — mai più una duplicazione su frame multipli.
- Stessa regola applicata in modo uniforme a violazioni, TalkBack e passed count senza
  fingerprint (prima ognuno aveva la propria variante della stessa euristica ambigua).
- Nuovo test di regressione `buildFrames_multipleQualifyingVisitsSameTitle_doesNotDuplicateUnfingerprintedBucket`:
  3 visite stesso titolo, 2 con violazioni proprie, un finding TalkBack senza fingerprint —
  verifica che il totale attraverso i frame sia 1 (non duplicato) e attribuito deterministicamente
  alla prima visita. I test esistenti sul caso "un solo proprietario" restano verdi invariati.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Settimana 4 — Evidence/marker coerenti: screenshot solo alla prima visita (1 agosto 2026)

Pre-mortem report dinamico: `ScanEvidenceStore.saveScreenScreenshot` sovrascriveva il file
`screen_<fingerprint>.jpg` ad ogni rivisita dello stesso fingerprint (debounce di soli 2s, non
un blocco). Il crop di una singola violazione resta sempre corretto (annotato subito con la
bitmap della propria passata), ma lo sfondo "schermata intera" mostrato nel report dinamico è
condiviso da fingerprint — se veniva sovrascritto con una cattura più recente (scroll, contenuto
dinamico), i marker delle violazioni rilevate in una visita precedente finivano disegnati sul
contenuto sbagliato dell'immagine più recente.

- `saveScreenScreenshot` ora scrive il file solo se non esiste già per quel fingerprint in
  quella sessione: prima visita vince, rivisite successive riusano il file esistente invece di
  sovrascriverlo.
- Rimossa la logica di debounce time-based (`lastScreenSaveMs`, `SCREEN_DEBOUNCE_MS`) — non
  più necessaria: "esiste già" è una condizione più semplice e più corretta di "è passato
  abbastanza tempo".
- 2 test nuovi in `ScanEvidenceStoreTest.kt` (Robolectric per `Bitmap`/`Context`): seconda
  visita non sovrascrive la prima; fingerprint diversi ottengono file separati.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Settimana 4 — Fingerprint stabile: dedup chrome transitorio (1 agosto 2026)

Bug in memoria (`screen_fingerprint_instability`): confrontando i golden capture v1.3.0/v1.3.1
la stessa schermata "Home" produceva **3 fingerprint diversi** nella stessa sessione, a seconda
di quali elementi di chrome strutturale (collapsing toolbar, bottom nav) erano effettivamente
presenti nell'albero al momento esatto della cattura — non un flag di visibilità, il nodo
letteralmente scompare/riappare dall'albero durante scroll/animazioni. Ogni fingerprint diverso
frammentava il report in una "schermata" fasulla in più.

- Nuovo `ScreenFingerprint.canonicalize(candidate, knownFingerprints)`: riconduce un fingerprint
  a uno già visto in sessione quando ha lo stesso prefisso `package::titolo` e la differenza di
  chrome-id è al massimo 1 elemento (`MAX_TRANSIENT_CHROME_DIFF`) — copre sia il caso "toolbar
  appare" sia "toolbar scompare". **Non** unifica mai fingerprint con un tab (`tab:...`) diverso:
  quello è un cambio di contenuto reale (vedi test `tabLabel_inChromeSeparatesFingerprint` già
  esistente), non chrome transitorio.
- Wired in `AccessibilityTreeScanner.scanRoot`: il fingerprint viene canonicalizzato **prima**
  di essere passato a `analyzer.analyzeTree` e a tutte le chiamate `repository.add*`/
  `registerUniqueScreen` — un solo punto di canonicalizzazione per passata di scan, quindi
  violazioni/checkSummaries/screenReaderFindings e la lista schermate visitate restano coerenti
  sullo stesso fingerprint canonico (nessuna disallineamento tra "conteggio schermate" e
  "attribuzione violazioni" nel report dinamico).
- Confronto fatto contro `seenFingerprintsThisSession` (già esistente, `ConcurrentHashMap`
  key-set thread-safe dal fix P0 Settimana 1) — nessuno stato nuovo da gestire.
- 7 test nuovi in `ScreenFingerprintCanonicalizeTest.kt` (logica pura su stringhe, nessun
  Robolectric necessario): merge toolbar-appare/scompare, tab diverso mai unificato, titolo
  diverso mai unificato, più di una differenza di chrome trattata come schermata genuinamente
  diversa, match esatto, insieme vuoto.

Verifica: `./gradlew :app:testDebugUnitTest :app:assembleDebug` verde.

### Settimana 3 — Plugin UX: lingua unica, report theme-aware, tabella AS + progress (1 agosto 2026)

Pre-mortem UX/professionalità: il plugin mescolava italiano e inglese nello stesso pannello,
il report VS Code era illeggibile in dark mode, Android Studio mostrava JSON grezzo in una
`JTextArea`, il bridge droppava metà dei dati di sessione e il setup-check mentiva sullo stato
reale del device quando scattava il version-gate. Fix:

- **Una lingua sola (inglese)** su tutte le superfici: tooltip e messaggi di
  `AccessScopeToolWindowFactory.kt`/`PluginUpdateChecker.kt` (erano un mix IT/EN nello stesso
  pannello) e `lang="it"` di `sidebarPanel.ts` allineato a `lang="en"` come il resto del plugin
  (i commenti KDoc restano in italiano, per coerenza con il resto del repo).
- **Bridge arricchito** (`ResultFetcher.buildScanResultResponse`): aggiunti `screenReaderFindings`
  e `visitedScreens` alla risposta di `fetch-results` — prima il report IDE non poteva mostrare
  né i finding TalkBack né il percorso di navigazione, molto più povero di quello on-device.
  `remediation`/`measuredValue`/`requiredValue` erano già presenti (l'array `violations` passa
  attraverso invariato), l'audit era in parte superato dagli sviluppi precedenti — solo i due
  campi a livello di sessione mancavano davvero.
- **`setup-check` non mente più**: prima, un manifest release irraggiungibile o un plugin IDE
  sotto la versione minima facevano tornare `accessibilityEnabled`/`overlayEnabled`/`appInstalled`
  tutti `false` **a prescindere** dallo stato reale del device (`SetupCheck.run`). Ora lo stato
  del device viene sempre letto per davvero; l'unica differenza è un campo opzionale
  `versionWarning` allegato al risultato.
- **Version-gate → warning, non blocco**: `PluginVersionChecker.requireCompatible` (che lanciava
  un'eccezione bloccante) è diventato `compatibilityWarning` (restituisce un messaggio o `null`).
  `ApkInstaller.installLatest` prosegue con l'installazione anche con plugin IDE datato,
  anteponendo l'avviso al messaggio di risultato invece di abortire.
- **Report VS Code theme-aware e accessibile** (`resultsWebview.ts`): colori hardcoded
  `#111`/`#555`/`#fafafa`/`#ddd` (illeggibili in dark mode) sostituiti con le variabili
  `--vscode-*` dell'editor corrente; aggiunto `<title>`, `scope="col"` sulle intestazioni tabella
  e `<caption>` (screen-reader-only) — pessima ottica altrimenti per un tool di accessibilità.
  Sezione nuova per i finding TalkBack, percorso schermate visitate e path del PDF (prima
  tipizzato in `types.ts` ma mai renderizzato in HTML).
- **Android Studio: tabella al posto della `JTextArea` con JSON grezzo** — nuova
  `ViolationsTableModel` (colonne Severity/Type/Screen/View ID/Details) alimentata dal risultato
  di `fetch-results`; il log testuale resta per messaggi di install/launch/setup-check ed errori.
  **`ProgressIndicator` su ogni azione**: `AsyncProcessIcon` + disabilitazione di tutti i bottoni
  durante l'esecuzione in background (prima `executeOnPooledThread` nudo, zero feedback visivo).

Verifica: `./gradlew :cli:compileKotlin :android-studio-plugin:compileKotlin
:android-studio-plugin:buildPlugin` verde; `npx tsc --noEmit` verde su `vscode-extension`.

**Non fatto in questo giro** (rimandato a task successivi del piano): canale update plugin
`develop`→`main` e pulizia impostazioni VS Code dichiarate e mai lette (task "Pulizia API morte");
collegamento del broadcast `SCAN_COMPLETE` (push invece di polling).

### Settimana 3 — TalkBack: refactor, dedupe, collegamento al punteggio (31 luglio 2026)

Il simulatore TalkBack (`TalkBackSimulator.kt`) non simulava davvero TalkBack: annuncio
infedele (concatenava sempre cd+testo+hint), dedupe rotta (schermate omonime perdevano
finding, nodi senza id collassavano tutti in uno), zero test, e i finding non entravano
mai nel punteggio o nelle evidenze (sezione puramente cosmetica del report). Tre fix in
sequenza:

- **Refactor su `NodeSnapshot`** (era `class` che camminava `AccessibilityNodeInfo`
  direttamente, non testabile su JVM senza mock Android): ora `object TalkBackSimulator`
  opera sugli stessi snapshot già raccolti da `NodeAccessibilityAnalyzer` (nessuna seconda
  camminata dell'albero). Modello annuncio riscritto per fedeltà a TalkBack reale:
  `contentDescription` **sostituisce** `text`/`hint` (non si concatenano più — mascherava
  difetti veri); copertura ruoli estesa (Switch/CheckBox/RadioButton/SeekBar/Spinner/Tab,
  prima solo Button/EditText/Image); stati (heading, checked, expanded, stateDescription,
  password, disabled), range percentuale, posizione riga/colonna in collection. Esclude
  nodi `isAccessibilityExcluded` (prima impossibile). 14 test JVM nuovi
  (`TalkBackSimulatorTest.kt`, Robolectric per `Rect.width()/height()`).
- **Fix dedupe** (`ScanSessionRepository.addScreenReaderFindings`): chiave ora include
  `screenFingerprint` (prima due schermate diverse con lo stesso titolo si rubavano i
  finding a vicenda); identità per nodi senza viewId/etichetta usa bounds quantizzati a
  griglia 32dp (`ViolationDedupeRules.quantizeBoundsLabel`) invece del token letterale
  `"no-id"` (10 immagini mute non collassano più in un solo finding); il finding aggregato
  ">50% silenzioso" ha chiave stabile che non include il conteggio corrente (non più
  duplicato ad ogni passata di scroll). 3 test nuovi in `ScanSessionRepositoryTest.kt`.
- **Collegamento al punteggio + filtro severità**: i finding "elemento silenzioso" per
  singolo nodo (`ScreenReaderFinding.isSilentElementFinding()`) diventano violazioni
  formali `SCREEN_READER_ANNOUNCEMENT` (dietro il confidence gate come tutto il resto,
  `TalkBackSimulator.toViolations`, wired in `NodeAccessibilityAnalyzer.analyzeTree`) —
  prima `toViolations` era dead code, mai chiamato. `DynamicReportHelper.buildFrames`
  esclude questi finding promossi da `DynamicScreenFrame.talkBackFindings` (altrimenti
  duplicati: violazione filtrabile per gravità + nota TalkBack sempre visibile); restano
  in `talkBackFindings` solo i riepiloghi di schermata (nessun elemento focalizzabile /
  >50% silenzioso, che non hanno un nodo singolo). Il filtro severità richiesto dal piano
  è così soddisfatto gratis: i finding promossi passano per `filterFrameViolations`
  esistente, nessun codice di filtro TalkBack-specifico nuovo.

Verifica: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` verde (build pulita,
nessun test esistente rotto — le fixture di `DynamicReportHelperTest.kt` usano
`announcedText` non-null, quindi non sono "elementi silenziosi" e restano in
`talkBackFindings` come prima).

**Non fatto in questo giro** (fuori scope Settimana 3, task separati nel piano): UX plugin
(lingua unica, report theme-aware, tabella AS); fingerprint instabile (3× "Home");
evidence/marker su schermate ripetute; finestre di attribuzione fingerprint (B2).

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
