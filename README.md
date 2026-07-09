# AccessScope

Scanner di accessibilità WCAG in tempo reale per sviluppatori Android.

## Branch

- `main` — release stabile
- `develop` — sviluppo attivo (37+ controlli WCAG, PDF per aree)

## Controlli

Etichette, contrasto multi-campione, testo, touch, form, struttura, TalkBack simulato, WebView, media, ordine focus, heading levels, contenuto dinamico silenzioso.

## Setup

Apri in Android Studio e sincronizza Gradle. Permessi: accessibilità + overlay.

## Precisione certificata (confronto col codice)

Per verificare quante violazioni del report sono **mappabili e presenti nel codice sorgente**:

```bash
python3 tools/certify_precision.py --md "/path/AccessScope_Reliability_*.md" --code "/path/to/target/app/src"
```

Note:
- Le righe con `viewId = —` sono **non verificabili** e vengono escluse dalla precisione.
