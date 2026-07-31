# Matrice compatibilità comandi Maestro (AccessScope ↔ Maestro CLI)

> Generata per il piano M1-A3 (`docs/PIANO_MAESTRO_E_SCANSIONE.md`).  
> Export: `MaestroYamlExporter` · Import: `MaestroYamlImporter` · Play in-app: `FlowPlayer`.

| Comando Maestro | Export AS | Import AS | Play in-app | Note |
|-----------------|-----------|-----------|-------------|------|
| `launchApp` | sì | sì | sì | Sempre primo step consigliato |
| `stopApp` | sì | sì | sì (best-effort) | Kill background + cold relaunch opzionale |
| `tapOn` (id / text / point) | sì | sì | sì | `optional: true` supportato in export/import |
| `doubleTapOn` | sì | sì | sì | |
| `longPressOn` | sì | sì | sì | |
| `inputText` | sì | sì | sì | Con `viewId`: export come `tapOn`+`inputText`; password → `${PASSWORD}` / `${PIN}` (env, non più `****` letterale) |
| `eraseText` | sì | sì | sì | Con `viewId`: export come `tapOn`+`eraseText` bare — `eraseText: {id}` **non è sintassi Maestro valida** (verificato con `maestro check-syntax`: `Unknown Property: id`) |
| `hideKeyboard` | sì | sì | sì | |
| `scroll` | sì | sì | sì | Pipeline può coalescere in `scrollUntilVisible` |
| `scrollUntilVisible` | sì | sì | sì | id/text annidati sotto `element:` (obbligatorio in Maestro reale — verificato con `maestro check-syntax`: `Config Field Required: element` altrimenti) |
| `swipe` | sì | sì | sì | start/end percent **interi** (Maestro non parsa i decimali su swipe — verificato) |
| `pressKey` | sì | sì | sì (Enter best-effort) | Validato contro l'enum Maestro reale (Enter/Backspace/Back/Home/Lock/Volume Up/Volume Down/Recent Apps/Power/Tab); chiave sconosciuta → fallback `Enter` + commento |
| `back` | sì | sì | sì | |
| `assertVisible` | sì | sì | sì | |
| `assertNotVisible` | sì | sì | sì | |
| `openLink` | sì | sì | sì | Intent VIEW |
| `waitForAnimationToEnd` | sì | sì | sì | timeout opzionale |
| `extendedWaitUntil` | sì | sì | sì | visibile come `Wait(visibleId/Text)`; id sempre short (non `package:id/name`) |
| Raw YAML (`RawMaestroYaml`) | sì | **no** | skip + log | L'importer fallisce su comandi non riconosciuti (vedi `MaestroYamlImporterTest.parse_rejectsUnknownCommand`) — non c'è ancora passthrough. La riga precedente della matrice ("sì (frammento)") era sbagliata: corretta qui il 31 luglio 2026. |

## Selettori come regex (importante)

Maestro tratta `text:`/`id:` come **regex full-match**, non stringa letterale. Un'etichetta
con `( ) . + * ? [ ] { } |` (es. `"Accedi (Beta)"`) falliva il match **a runtime, senza
errore di parsing** — la rottura più subdola per chi riusa lo YAML esportato. L'exporter ora
applica l'escape regex a ogni testo/id usato come selettore (non a `inputText`, mai regex);
l'importer applica l'unescape simmetrico per preservare il round-trip
(`MaestroYamlRoundTripTest.regexMetacharsInLabel_surviveExportImportExport`).

## Condizionali (`when:` / `conditionVisibleId`/`conditionVisibleText`)

Il modello `RecordedAction.Tap` ha già i campi `conditionVisibleId`/`conditionVisibleText`
(popolati da `PopupClassifier` per i popup opzionali), ma **non sono ancora esportati** come
condizione Maestro. In Maestro CLI 2.6.1 `when:` è valido solo annidato in
`runFlow: { when:, commands: }` (verificato con `maestro check-syntax` — un `when:` inline su
un comando singolo dà `Unknown Property: when`). Implementarlo richiede anche supporto import
per `runFlow`/`commands` annidati (l'importer oggi ha solo un `readBlock` piatto, non liste
annidate) per non rompere il round-trip — rimandato a un giro dedicato.

## Header `env:`

Se il flusso usa placeholder segreti (`${PIN}`/`${PASSWORD}`), l'header ora dichiara un blocco
`env:` con valori di esempio, e un commento ricorda `maestro test -e PIN=... -e PASSWORD=...`.

## Round-trip

Test JVM: `MaestroYamlRoundTripTest` — `export → import → export` idempotente su un flusso campione multi-comando.

## ZeroEdit (contratto qualità)

Dopo optimize+heal, un flusso è **ZeroEdit** se:

- nessun tap solo `point` / senza selettore semantico (`FlowLinter` ERROR `POINT_ONLY_SELECTOR`);
- popup/permission con `optional: true` dove applicabile;
- wait di animazione inseriti dal `WaitPlanner` dove servono (non wait ciechi inutili).

Gate: `dev.accessscope.scanner.recorder.quality.ZeroEditGate` su save con optimize.

## Verifica CLI (opzionale)

```bash
tools/verify_yaml_with_maestro_cli.sh path/to/flow.yaml
```

Richiede Maestro CLI e un device/emulatore (`maestro test`). Non è bloccante in CI.
