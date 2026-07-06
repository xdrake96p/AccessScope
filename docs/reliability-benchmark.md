# Benchmark affidabilità report — Nexi (`it.nexi.bff`)

Ground truth annotato dal codice sorgente Nexi per validare precision/recall di AccessScope.

## Come usare

1. Test manuale sui 7 flussi scope (vedi sotto) con AccessScope in scan
2. Esportare PDF da `/storage/emulated/0/Download/`
3. Confrontare report vs tabella N01–N18
4. Calcolare per ambito: **precision** = TP/(TP+FP), **recall** = TP/(TP+FN)

## Casi annotati

| ID | File / contesto | Ambito | Atteso | Note |
|----|-----------------|--------|--------|------|
| N01 | `colors.xml` — `gray_label` #a4b1d3 | Contrasto | VIOLATION | ~2.1:1 su bianco |
| N02 | `carousel_distinte_item.xml` — textColor gray_label | Contrasto | VIOLATION | Etichette distinte (`txt_data_*`, `causale`) |
| N03 | `contentDescription="@null"` su ImageView in button con testo | Etichette | OK | Decorativo nel button (sibling label) |
| N04 | `layout_topbar_back.xml` — acc_back | Etichette | OK | Etichetta corretta |
| N05 | `importantForAccessibility="no"` in XML | Focus | OK | Fuori albero a11y |
| N06 | `MainActivity` — NO_HIDE_DESCENDANTS runtime | Focus | OK | Fragment nascosti intenzionalmente |
| N07 | RecyclerView stesso `viewId` in item template | Struttura | OK | Non segnalare DUPLICATE_VIEW_ID |
| N08 | `accessibilityHeading` su titoli F24 | Struttura | OK | Heading marcato |
| N09 | Titoli TextView non marcati heading | Struttura | VIOLATION | Solo se grandi e ≤60 char |
| N10 | WebView privacy policy | Web | VIOLATION | Solo se childCount == 0 |
| N11 | PIN pad tasti | Touch | VALUTARE | UI bancaria densa |
| N12 | Icone in toolbar con contentDescription runtime | Etichette | OK | `TopBar.kt` imposta CD |
| N13 | `payment_reason_f24_item.xml` — importantForAccessibility=no | Focus | OK | Esclusi dall'albero |
| N14 | Link «Scopri di più» generico | Etichette | VIOLATION | LINK_NOT_DESCRIPTIVE |
| N15 | Testo troncato con ellissi intenzionale | Testo | OK | Non FP TEXT_TRUNCATED |
| N16 | `vop_info` in `carousel_distinte_item_swipable.xml` | Etichette + Touch | VIOLATION | `@null` + icona piccola |
| N17 | `topbar_icon_left` / `topbar_icon_right` `@null` | Etichette | VIOLATION | Se CD runtime assente; dedupe global |
| N18 | Schermata PIN (`pin_pad_view.xml`) | Copertura | SCAN | Sezione «Inserisci PIN» in panorama |
| N19 | `fragment_homepage_titolare.xml` — `last_30` / `import_positive` su sfondo brand | Contrasto | OK | Testo decorativo widget entrate/uscite |
| N20 | `green_dot` + `importantForAccessibility=no` — `dot_filter` | Etichette | LIMITAZIONE | Fuori albero a11y: non rilevabile via TalkBack tree |

## Flusso benchmark (manuale)

Percorrere i 7 flussi scope su device con Nexi + AccessScope:

1. Home
2. Autorizza distinte
3. Paga effetti
4. Disposizioni online
5. Disposizioni istantanee
6. Rubrica
7. Nuovo pagamento (+ inserimento PIN quando richiesto)

## Checklist accettazione — Iterazione 2

| Criterio | Soglia |
|----------|--------|
| Panorama PDF: sezioni distinte | Home, DISTINTE/BONIFICI, EFFETTI IN SCADENZA, NUOVO PAGAMENTO, RUBRICA, Inserisci PIN |
| Problemi non bucketizzati sotto «AZIENDA 1» | &lt;30% del totale (era ~64%) |
| N16 `vop_info` | TP |
| N17 topbar | TP se CD assente, OK se CD runtime presente |
| Custom action + scroll senza nome | &lt;15% problemi totali |
| Precisione utile (esclusi scroll/custom) | ≥75% |
| Recall scope 7 flussi | ≥75% |

## Target post-iterazione 2

| Ambito | Precision target | Recall target |
|--------|------------------|---------------|
| Contrasto | ≥ 75% | ≥ 70% |
| Etichette | ≥ 85% | ≥ 80% |
| Touch | ≥ 80% | ≥ 75% |
| Focus/TalkBack | ≥ 80% | ≥ 70% |
| Copertura schermate | ≥ 85% | — |
| **Precisione utile** | **≥ 75%** | **≥ 75%** |
| **Precisione totale** | **55–65%** | iter. 3 per 80% |

## Metriche sessione (baseline vs iter. 1)

| Metrica | Report 150238 (iter. 1) | Target iter. 2 |
|---------|-------------------------|----------------|
| Problemi totali | 136 | ~70–90 |
| Custom actions | 31 | &lt;8 |
| Scroll senza nome | ~26 | &lt;5 |
| Heading FP | ~10 | &lt;4 |
| Sezione «AZIENDA 1» | 87 problemi | &lt;30 |

## Metriche sessione

- **Schermate uniche**: fingerprint distinti (non ogni scroll)
- **Analisi eseguite**: scan debounced totali
- **Punteggio**: pesato per severità WCAG, non solo conteggio grezzo

## Checklist accettazione — Iterazione 5

Baseline report: **154839** (24 problemi, score 83, precisione totale ~76%, precisione utile ~57%).

| Criterio | Soglia |
|----------|--------|
| Bucket «Schermata» nel panorama | **0** problemi |
| Sezione AZIENDA 1 (home chart FP) | ≤3 problemi |
| Heading FP (`totale_distinte`, `rotate_display`, `logo`) | 0 |
| N02 `txt_data_*` / `causale` contrasto | TP |
| N16 `vop_info` | TP |
| N17 topbar | TP se CD assente, OK se CD runtime |
| N19 home chart decorativo | OK (non segnalare) |
| Problemi totali | **14–18** |
| Precisione utile | **≥75%** |
| Precisione totale | **≥80%** |

### Modifiche iter. 5

- `ScreenTitleResolver`: fingerprint viewId, cache titolo per package, skip overlay splash (`logo`)
- `PrecisionRules`: skip contrasto grafico home, CTA `tv_custom`, heading in carousel
- `FocusOrderAnalyzer`: soglia inversioni 0.45, skip layout RecyclerView-dominant
- Recall: contrasto micro-label `txt_data_*` con bounds espansi

## Checklist accettazione — Iterazione 6

Baseline regressione: **160105** (32 problemi, precisione ~50%). Target: tornare ≥154839 utile senza perdere fix iter. 5.

| Criterio | Soglia |
|----------|--------|
| Bucket «Schermata» | 0 |
| AZIENDA 1 home chart | 0 |
| Ultimi insoluti (mal attribuiti) | ≤4 |
| COMUNICAZIONI / `nav_*` FP | 0 |
| DISTINTE `content` custom action | 0 |
| Problemi totali | **≤20** |
| Precisione utile | **≥70%** |

### Modifiche iter. 6

- Cache titolo: non riusare «Ultimi insoluti» su home (`rotate_display` ≠ insoluti)
- `findByDistinctiveIds`: home prima di insoluti; insoluti solo con marker dedicati
- Skip nodi `nav_*` / scroll drawer / bounds fantasma (1080×12)
- Una finestra contenuto per scan; skip drawer-only root
- Skip label/custom/role su `content` carousel template
