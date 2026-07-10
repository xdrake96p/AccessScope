# AccessScope Plugin Bridge (v1.3.0)

## Bridge API per plugin IDE

### ScanResultProvider

- Authority: `dev.accessscope.scanner.results`
- Endpoint status, latest per package, session per ID
- JSON compatibile con `ArchivedScanSession`

### Broadcast

- Action: `dev.accessscope.scanner.SCAN_COMPLETE`
- Extras: `sessionId`, `packageName`
- Logcat tag: `AccessScopeBridge`

### Serializzazione violazioni

Ogni violazione include ora il campo `severity` per report IDE.

## Build app con bridge

```bash
./gradlew assembleRelease
```

## Test bridge su device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell content query --uri content://dev.accessscope.scanner.results/status
```
