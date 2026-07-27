# Golden master anti-regressione (M0-R2)

Baseline di riferimento per il confronto anti-regressione del piano
`docs/PIANO_MAESTRO_E_SCANSIONE.md`. Il confronto si esegue con:

```bash
python3 tools/compare_scan_outputs.py --baseline reports/golden/<tag>/<flusso>.json --current <output>.json
```

## Struttura

```
reports/golden/<tag-git>/<flusso>_<pkg>.json
```

Esempio: `reports/golden/v1.3.0/home_it.nexi.bff.json`

## Procedura di cattura baseline (richiede device + app target, es. Nexi)

1. Checkout del tag baseline e installazione APK:
   ```bash
   git checkout v1.3.0 && ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Abilita accessibilità + overlay per AccessScope sul device.
3. Per ciascuno dei 7 flussi benchmark (`docs/reliability-benchmark.md`):
   avvia scansione, naviga il flusso, STOP.
4. Esporta il JSON della sessione (due opzioni):
   - **Bridge** (consigliata):
     ```bash
     adb shell content query --uri \
       "content://dev.accessscope.scanner.results/latest?package=it.nexi.bff" \
       > reports/golden/v1.3.0/<flusso>_it.nexi.bff.json
     ```
   - **Store** (debug build, include `visitedScreens`):
     ```bash
     adb exec-out run-as dev.accessscope.scanner \
       sh -c 'cat files/scan_history/by_package/it.nexi.bff.json' # trova ultimo sessionId
     adb exec-out run-as dev.accessscope.scanner \
       sh -c 'cat files/scan_history/sessions/<sessionId>.json' \
       > reports/golden/v1.3.0/<flusso>_it.nexi.bff.json
     ```
5. Torna al branch di lavoro (`git checkout restyle`), reinstalla l'APK corrente,
   ripeti i flussi e confronta con `tools/compare_scan_outputs.py`.

## Approvazione derive

Una deriva va accettata solo se intenzionale e documentata: rieseguire lo script
con `--approve` (aggiorna la baseline) e citare il motivo nel task/PROJECT.md.

> Nota: i JSON baseline Nexi NON sono ancora catturati in questa repo — richiedono il
> device con l'app target. Questa cartella contiene la procedura; i file vanno
> aggiunti alla prima cattura.

Fixture smoke (self-check dello script, non una baseline prodotto):

```bash
python3 tools/compare_scan_outputs.py \
  --baseline reports/golden/fixtures/sample_session.json \
  --current reports/golden/fixtures/sample_session.json
# → OK: nessuna deriva
```
