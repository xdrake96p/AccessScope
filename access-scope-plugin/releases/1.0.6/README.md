# Release plugin Android Studio v1.0.6

## Installazione

Scarica e installa **solo** `AccessScope-1.0.6.zip`:

**Settings → Plugins → Install Plugin from Disk…** → riavvia Android Studio.

## Fix inclusi (rispetto a 1.0.5)

- **Firma incompatibile** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): disinstalla automaticamente la build precedente (es. debug) e reinstalla quella da GitHub Release
- **Launch** non fallisce se l'app è già installata ma l'update non è necessario
- Controlli device: `unauthorized`, `offline`, non trovato
- Messaggi di errore più chiari (storage, permessi USB, rate limit GitHub)
- Setup Check più robusto se l'app non risponde

## Dopo reinstallazione firma diversa

Se AccessScope viene reinstallato da zero, **riabilita** su device:
1. **Impostazioni → Accessibilità** → AccessScope ON
2. **Permesso overlay** per AccessScope
