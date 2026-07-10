# AccessScope — VS Code / Cursor extension

Estensione complementare al plugin Android Studio **AccessScope 1.0.8**.

## Cosa fa

Integra nel flusso di sviluppo l'app [AccessScope](https://github.com/xdrake96p/AccessScope), scanner di accessibilità WCAG su device Android:

- Elenca device collegati via `adb`
- Installa/aggiorna l'APK AccessScope da GitHub Releases
- Apre l'app sul device
- Recupera l'ultima scansione in JSON
- Verifica setup (accessibilità, overlay, app installata)

## Dove trovare il pannello

- **Icona AccessScope** nella Activity Bar (barra icone a sinistra)
- Command Palette: `AccessScope: Open Panel`

## Comandi (Command Palette)

| Comando | Azione |
|---------|--------|
| `AccessScope: Select Device` | Scegli device/emulatore |
| `AccessScope: Install / Update` | Installa o aggiorna APK |
| `AccessScope: Install and Launch` | Install + apre app |
| `AccessScope: Fetch Results` | Scarica report JSON |
| `AccessScope: Check Setup` | Verifica prerequisiti |
| `AccessScope: Clear Output` | Pulisce output log |
| `AccessScope: Check Extension Update` | Verifica nuova VSIX su GitHub |

## Requisiti

- Android SDK `platform-tools` (`adb`)
- Java 17+ (JBR di Android Studio consigliato)
- USB debugging sul device
- AccessScope: accessibilità + overlay abilitati (prima scansione)

## Configurazione

`accessScope.targetPackage` — se vuoto, rileva `applicationId` da `app/build.gradle` del workspace.

## Aggiornare l'estensione

1. **Check Extension Update** dalla Command Palette
2. Oppure: **Extensions → ⋯ → Install from VSIX…**

Le versioni estensione e plugin Android Studio vanno mantenute allineate (es. entrambe **1.0.8**).
