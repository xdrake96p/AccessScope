# AccessScope

Scanner di accessibilità WCAG in tempo reale per sviluppatori Android.

## Funzionalità

- Selezione app da monitorare (solo launcher, senza permessi invasivi)
- Analisi live tramite `AccessibilityService`
- Simulazione TalkBack interna
- Report PDF in Download

## Controlli WCAG

Etichette mancanti, contrasto colore (screenshot API 30+), testo troppo piccolo, target di tocco, spaziatura, sovrapposizione, gerarchia titoli, focus, input/errori, immagini senza alt, link generici, nomi duplicati, aree scrollabili, stati disabilitato/espansione, password, modali, struttura liste, ruoli semantici, screen reader.

## Setup

Apri in Android Studio 2024.2+ e sincronizza Gradle.

## Permessi

- Servizio di accessibilità
- Sovrapposizione (overlay STOP)
