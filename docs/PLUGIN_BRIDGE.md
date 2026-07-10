# AccessScope Plugin Bridge (v1.3.0)

## Bridge API per plugin IDE

### ScanResultProvider

- Authority: `dev.accessscope.scanner.results`
- Endpoint status, latest per package, session per ID
- JSON compatibile con `ArchivedScanSession` (campi ridotti per il bridge)

### Sicurezza (v1.3.1+)

- **Accesso ristretto:** solo AccessScope, `adb shell` (uid shell) e app con **firma condivisa** possono interrogare il provider. Altre app sul device ricevono `SecurityException`.
- **Permesso signature** `dev.accessscope.scanner.permission.READ_SCAN_RESULTS` dichiarato per eventuali companion app firmate allo stesso modo.
- **Validazione input:** `sessionId` deve essere UUID; `package` deve essere un package Android valido (no path traversal).
- **JSON bridge ridotto:** esclusi `elementLabel`, `announcedText`, `evidenceImagePath` e codici colore esadecimali dalle risposte del provider (restano disponibili nell'app e nei PDF interni).

### Broadcast

- Action: `dev.accessscope.scanner.SCAN_COMPLETE`
- Extras: `sessionId`, `packageName`
- Logcat tag: `AccessScopeBridge`

### Serializzazione violazioni

Ogni violazione include il campo `severity` per report IDE.

## Build app con bridge

```bash
./gradlew assembleRelease
```

## Test bridge su device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell content query --uri content://dev.accessscope.scanner.results/status
```
