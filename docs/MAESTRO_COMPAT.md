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
| `inputText` | sì | sì | sì | Con `viewId`: export come `tapOn`+`inputText`; password → `****` / commento masked |
| `eraseText` | sì | sì | sì | |
| `hideKeyboard` | sì | sì | sì | |
| `scroll` | sì | sì | sì | Pipeline può coalescere in `scrollUntilVisible` |
| `scrollUntilVisible` | sì | sì | sì | id o text + direction |
| `swipe` | sì | sì | sì | start/end percent |
| `pressKey` | sì | sì | sì (Enter best-effort) | |
| `back` | sì | sì | sì | |
| `assertVisible` | sì | sì | sì | |
| `assertNotVisible` | sì | sì | sì | |
| `openLink` | sì | sì | sì | Intent VIEW |
| `waitForAnimationToEnd` | sì | sì | sì | timeout opzionale |
| `extendedWaitUntil` | sì | sì | sì | visibile come `Wait(visibleId/Text)` |
| Raw YAML (`RawMaestroYaml`) | sì | sì (frammento) | skip + log | Per comandi non tipizzati |

## Round-trip

Test JVM: `MaestroYamlRoundTripTest` — `export → import → export` idempotente su un flusso campione multi-comando.

## Verifica CLI (opzionale)

```bash
tools/verify_yaml_with_maestro_cli.sh path/to/flow.yaml
```

Richiede Maestro CLI e un device/emulatore (`maestro test`). Non è bloccante in CI.
