# AccessScope — Report affidabilità scansione

> File generato automaticamente per debug interno. Usare per confrontare sessioni,
> individuare possibili allucinazioni e misurare miglioramenti tra versioni.

## Metadati sessione

| Campo | Valore |
|-------|--------|
| Data | 2026-07-27 12:36:28 |
| App AccessScope | 1.3.1 |
| Package target | com.axa.app.myaxa.it.develop |
| Ambito | Completa |
| Schermate uniche | 3 |
| Analisi eseguite | 11 |
| Violazioni raw | 5 |
| Violazioni report (filtrate) | 5 |
| TalkBack findings | 7 |
| Check OK registrati | 4 |

## Confronto sessione precedente

| Metrica | Valore |
|---------|--------|
| Nuove violazioni | 5 |
| Risolte | 0 |
| Invariate | 0 |
| Δ score | -14 |

## Riepilogo per ambito

- **🏷️ Etichette e nomi**: 0 problemi, 2 check OK
- **👆 Tocco e dimensioni**: 1 problemi, 0 check OK
- **🎨 Colori e contrasto**: 0 problemi, 0 check OK
- **🔤 Testo e tipografia**: 0 problemi, 0 check OK
- **📝 Moduli e campi**: 0 problemi, 0 check OK
- **🧭 Struttura e navigazione**: 2 problemi, 0 check OK
- **🔊 Screen reader (TalkBack)**: 2 problemi, 0 check OK
- **🌐 Web e contenuti speciali**: 0 problemi, 0 check OK

## Schermate visitate

1. Schermata
2. Schermata
3. Accedi all'Area Clienti

## Segnali di possibile allucinazione

_Nessun pattern sospetto automatico su violazioni filtrate._

## Violazioni a bassa confidenza (<80%)

_Nessuna._

## Tutte le violazioni nel report

### Schermata (2)

- **Target di tocco piccolo** (`contactButton`) — conf 92%
  - Schermata: Schermata
  - Label: Contattaci
  - Misurato: 275×77 px (richiesto ≥ 126×126 px)
  - Dettaglio: Misura 275×77 px, minimo 126 px.
  - Bounds: 275×77 px @(752,2034)
- **Azione senza etichetta** (`webview`) — conf 88%
  - Schermata: Schermata
  - Dettaglio: 1 azione/i personalizzata/e senza etichetta.
  - Bounds: 1080×2154 px @(0,0)

### Accedi all'Area Clienti (3)

- **Contenuto dinamico silenzioso** (`—`) — conf 85%
  - Schermata: Accedi all'Area Clienti
  - Dettaglio: Il contenuto è cambiato più volte senza annunci TalkBack.
- **Salto livello titolo** (`—`) — conf 80%
  - Schermata: Accedi all'Area Clienti
  - Dettaglio: Salto da livello ~H1 a ~H3 su "Non ricordi più la password?".
  - Bounds: 520×47 px @(112,819)
- **Salto livello titolo** (`—`) — conf 80%
  - Schermata: Accedi all'Area Clienti
  - Dettaglio: Salto da livello ~H1 a ~H3 su "Non ricordi più la password?".
  - Bounds: 520×50 px @(112,847)

## Riferimento benchmark Nexi (mps-accessibility-verification)

ID noti come **falso positivo** nel benchmark manuale — se compaiono, regression:

- `currency`
- `tv_title_second_section`
- `amount_uscite_effects`
- `new_payment`
- `currency_paym`
- `causale`
- `import_positive`
- `currency_incom`
- `edt_ragione_sociale`
- `content`
- `tv_tab`

ID **confermati** — devono restare se presenti nel codice sorgente:

- `last_30`
- `rubrica_label`
- `vop_info`
- `select_accounts`
- `tv_see_account_movements`
- `see_all_insolved`
- `tv_custom`
- `tv_incassi`
- `topbar`
- `bonifico_online`

### Presenza nel report corrente

| ID | Atteso benchmark | Presente in report |
|----|------------------|-------------------|
| `currency` | FP (non segnalare) | no |
| `tv_title_second_section` | FP (non segnalare) | no |
| `amount_uscite_effects` | FP (non segnalare) | no |
| `new_payment` | FP (non segnalare) | no |
| `currency_paym` | FP (non segnalare) | no |
| `causale` | FP (non segnalare) | no |
| `import_positive` | FP (non segnalare) | no |
| `currency_incom` | FP (non segnalare) | no |
| `edt_ragione_sociale` | FP (non segnalare) | no |
| `content` | FP (non segnalare) | no |
| `tv_tab` | FP (non segnalare) | no |
| `last_30` | TP (segnalare) | no |
| `rubrica_label` | TP (segnalare) | no |
| `vop_info` | TP (segnalare) | no |
| `select_accounts` | TP (segnalare) | no |
| `tv_see_account_movements` | TP (segnalare) | no |
| `see_all_insolved` | TP (segnalare) | no |
| `tv_custom` | TP (segnalare) | no |
| `tv_incassi` | TP (segnalare) | no |
| `topbar` | TP (segnalare) | no |
| `bonifico_online` | TP (segnalare) | no |

---
*Generato da AccessScope ScanReliabilityReportExporter*
