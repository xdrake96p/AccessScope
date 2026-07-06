# AccessScope — Documento di progetto (living doc)

> **Aggiornare questo file** ad ogni iterazione significativa: nuove feature, fix di precisione, cambi UI, metriche benchmark, commit rilevanti.

**Repository:** [github.com/xdrake96p/AccessScope](https://github.com/xdrake96p/AccessScope)  
**Package:** `dev.accessscope.scanner`  
**Branch principale sviluppo:** `develop`  
**Ultimo aggiornamento:** 6 luglio 2026

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
    → ScanSessionRepository (violazioni, check OK, schermate)
    → Stop → PdfReportExporter + ReportScreen
```

| Modulo | Ruolo |
|--------|--------|
| `AccessScopeAccessibilityService` | Cattura eventi, debounce scan, multi-finestra, overlay |
| `NodeAccessibilityAnalyzer` | 37 tipi di violazione WCAG, contrasto, touch, label |
| `PrecisionRules` | Euristiche generiche anti-FP (drawer, home widget, carousel…) |
| `AppPrecisionProfiles` | Marker opzionali per app note (Nexi) |
| `ScreenTitleResolver` | Titolo schermata da topbar, heading, viewId |
| `CheckCollector` | Registra controlli superati per report |
| `PdfReportExporter` | PDF con problemi dettagliati + sezione OK |
| `ScanSessionRepository` | Stato sessione, dedupe, fingerprint schermate |

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

---

*Documento mantenuto dal team di sviluppo. Per dettagli tecnici sui casi Nexi vedere `reliability-benchmark.md`.*
