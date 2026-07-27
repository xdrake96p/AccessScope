# AccessScope — Brief per presentazione

> **Scopo di questo file:** dare a un agent (o a una persona) tutte le informazioni necessarie per costruire una **mini-presentazione** dell'app AccessScope (slide, pitch, demo talk). Contiene fatti verificati dal codice e dalla documentazione del repository. Non inventare funzionalità non elencate qui.

---

## 1. Elevator pitch (30 secondi)

**AccessScope** è uno **scanner di accessibilità WCAG in tempo reale per app Android**.
Monitora qualsiasi app installata sul telefono **mentre la usi davvero**: analizza l'albero di accessibilità, misura il contrasto dei colori dagli screenshot, simula cosa direbbe TalkBack e alla fine produce un **report PDF e JSON** con i problemi trovati, la gravità, il riferimento WCAG e il suggerimento per correggerli — senza bisogno del codice sorgente dell'app testata.

Versione attuale app: **1.3.1** · Plugin IDE: **1.0.8** · Licenza/repository: [github.com/xdrake96p/AccessScope](https://github.com/xdrake96p/AccessScope)

---

## 2. Il problema che risolve

- L'accessibilità delle app mobile viene testata **tardi, raramente o mai** durante lo sviluppo.
- Gli audit manuali con TalkBack sono **lenti, costosi** e richiedono esperti.
- Gli scanner statici lavorano sul codice o su una singola schermata: **non vedono il comportamento reale** dell'app in uso (scroll, dialoghi, contenuti dinamici).
- Chi sviluppa non sa *cosa* correggere né *quanto è grave*: manca un report azionabile.

## 3. La soluzione in 5 punti

1. **Scansione runtime su device reale** — usa le `AccessibilityService` API di Android: vede esattamente ciò che vedrebbe uno screen reader.
2. **40 tipi di controlli WCAG automatici** in 8 aree tematiche (etichette, tocco, contrasto, testo, form, struttura, screen reader, web/media).
3. **Contrasto colore misurato via screenshot** con campionamento multi-punto (non solo euristica sul codice).
4. **Report azionabili**: PDF per il team, JSON per l'IDE, **prompt AI pronto da incollare** per far correggere il codice a un assistente.
5. **Integrazione nell'IDE**: plugin gemelli per **Android Studio** e **VS Code/Cursor** (installano l'app, avviano, scaricano i risultati).

---

## 4. Come funziona (flusso utente, per demo)

```
1. Apri AccessScope sul telefono
2. Selezioni l'app da testare (es. la tua app in sviluppo)
3. Tocchi "Avvia scansione" → compare un overlay LIVE/STOP trascinabile
4. Navighi normalmente l'app target (menu, form, dialoghi…)
5. Tocchi STOP → report con punteggio, problemi per schermata, controlli superati
6. Esporti PDF, oppure copi il prompt AI, oppure scarichi il JSON dal plugin IDE
```

**Permessi richiesti (una tantum):** Accessibilità + Overlay ("mostra sopra altre app").

**Prerequisito chiave per la demo:** la scansione la guida l'utente navigando — non è un crawler automatico.

---

## 5. Cosa controlla — le 8 aree WCAG

| Area | Cosa verifica | Esempi di violazioni rilevate |
|------|---------------|-------------------------------|
| 🏷️ **Etichette e nomi** | Ogni pulsante/immagine dice cosa fa | Etichetta mancante, immagine senza alt text, alt text scadente, link "clicca qui", nomi duplicati |
| 👆 **Tocco e dimensioni** | Target di tocco adeguati | Target < 48dp, pulsanti troppo vicini, aree cliccabili sovrapposte |
| 🎨 **Colori e contrasto** | Leggibilità testo/icone sullo sfondo | Contrasto testo insufficiente (WCAG 1.4.3), icone poco visibili (1.4.11) |
| 🔤 **Testo e tipografia** | Dimensione e completezza del testo | Testo troppo piccolo, testo troncato con "…" |
| 📝 **Moduli e campi** | Input usabili e comprensibili | Campo senza etichetta, errore non annunciato, obbligatorio non indicato, password non mascherata, slider senza valore |
| 🧭 **Struttura e navigazione** | Titoli, ordine lettura, liste/tabelle | Titolo non marcato come heading, salto di livello H1→H3, ordine focus illogico, tabella senza intestazioni, ID vista duplicati, popup senza titolo |
| 🔊 **Screen reader (TalkBack)** | Cosa sentirebbe un utente non vedente | Elemento interattivo non raggiungibile, stato disabilitato/espanso non comunicato, contenuto dinamico che cambia senza annuncio, azione custom senza descrizione |
| 🌐 **Web e contenuti speciali** | WebView e media | WebView senza albero a11y esposto, controlli media senza etichetta |

Ogni violazione ha: **nome leggibile, riferimento WCAG (es. 4.1.2), gravità (Critico/Grave/Medio/Lieve), suggerimento in linguaggio semplice, posizione `@id` nell'app**.

Il motore esegue **37+ controlli** con **forte lavoro anti-falsi-positivi**: deduplicazione rigorosa (chiavi viewId-first, bounds quantizzati), regole di precisione generiche per qualsiasi app, euristica per scroll, drawer, carousel, widget home.

---

## 6. Output e report

- **Report in-app**: problemi raggruppati per schermata, panoramica con schermate "pulite" in verde, dettaglio per singola schermata con evidenziazione visiva (crop screenshot o wireframe ricostruito per schermate protette).
- **PDF** (salvato in `Download/AccessScope_*.pdf`): copertina con punteggio, panoramica per schermata, copertura controlli per ambito WCAG, per ogni schermata prima i ✅ controlli superati poi i ⚠️ problemi, glossario finale.
- **Punteggio di accessibilità** e conteggio controlli superati (non solo errori: mostra anche cosa è fatto bene).
- **Cronologia sessioni**: fino a 20 sessioni per app, con **confronto tra scansioni** (+N nuovi problemi / −M risolti / delta punteggio) — utile per mostrare i progressi.
- **Prompt AI per fix** (`AiPromptBuilder`): genera un prompt markdown strutturato con contesto, riferimenti WCAG e problemi per schermata, da dare a un LLM per ottenere le correzioni di codice.
- **Schermate protette** (login, PIN, `FLAG_SECURE`): rilevate con gate multi-segnale; il report le marca "Protetta" e usa wireframe al posto dello screenshot (privacy by design).

---

## 7. I tre componenti del sistema

| Componente | Dove gira | Ruolo |
|------------|-----------|-------|
| **App AccessScope** | Telefono/emulatore Android | Esegue la scansione reale (questa è "l'app" della presentazione) |
| **Plugin Android Studio** | PC/Mac | Installa/aggiorna l'app sul device, la avvia, scarica i risultati JSON, verifica i permessi (Setup Check) |
| **Estensione VS Code/Cursor** | PC/Mac | Gemella del plugin AS: stessa versione, stessi comandi, stesso motore CLI condiviso |

Il plugin rileva automaticamente il package dell'app in sviluppo dal Gradle del progetto aperto. Comunicazione via `adb` + `ContentProvider` sicuro (`dev.accessscope.scanner.results`, accesso ristretto ad adb shell e app con firma condivisa).

---

## 8. Risultati misurabili (benchmark reale)

Benchmark sull'app **Nexi** (`it.nexi.bff`, con sorgente disponibile come ground truth, 7 flussi manuali):

| Iterazione | Problemi segnalati | Punteggio | Precisione utile |
|------------|--------------------|-----------|-------------------|
| 1 (baseline) | 136 | 21 | ~32% |
| 10 (attuale) | **6** | **92** | **100% (solo veri positivi, 180 controlli OK)** |

- **127 test JVM** (unit + regressione su pattern di piattaforma e casi Nexi).
- Strumento di **certificazione precisione** incluso: confronta le violazioni del report con il codice sorgente (`tools/certify_precision.py`).

---

## 9. Dati tecnici (per slide "sotto il cofano")

- **Linguaggio/UI:** Kotlin, Jetpack Compose Material 3, tema chiaro/scuro/sistema.
- **Design system proprio "Scanner & HUD"** (restyle luglio 2026): Electric Teal `#00E5FF`, font Hanken Grotesk/Inter/JetBrains Mono, splash animata + onboarding, bottom bar a due zone, drawer laterale — palette validata **WCAG AA con dogfooding** (verificata con il nostro stesso metodo di misura del contrasto).
- **Core:** `AccessibilityService` + screenshot API; pipeline: eventi a11y → analisi nodi → regole di precisione → dedupe → repository sessione → export.
- **Architettura modulare**: analyzer (nodo/contrasto/titolo/precisione), data (dedupe, cronologia, persistenza), export PDF, service/scan, viewmodel con controller separati.
- **Package:** `dev.accessscope.scanner` · **versionName 1.3.1** (versionCode 5).
- **CI/CD:** GitHub Actions — build app firmata + CLI + plugin AS/VSIX in un'unica pipeline di release; release firmate con keystore dedicato.

---

## 10. Punti di forza da enfatizzare

1. **Non serve il codice sorgente** dell'app testata: funziona su qualsiasi app installata.
2. **Testa l'uso reale**, non una schermata statica: scroll, dialoghi, contenuti dinamici, stati interattivi.
3. **Anti falsi positivi**: lavoro ingegneristico documentato (10 iterazioni di benchmark) — un report affidabile, non rumore.
4. **Dalla rilevazione alla correzione**: PDF per il team, JSON per l'IDE, prompt AI per la remediation.
5. **Osserva la privacy**: niente screenshot su schermate sicure (PIN/password), bridge API con accesso ristretto.
6. **Ecosistema completo**: app + plugin per i due IDE più usati, con aggiornamenti automatici da GitHub.

## 11. Limiti onesti (per domande Q&A)

- Non sostituisce un audit manuale completo con TalkBack né garantisce conformità legale senza revisione umana.
- La navigazione nell'app target è **manuale** (l'utente guida la scansione).
- Solo **Android**; il plugin richiede cavo USB/`adb`.
- Campi con `viewId` non disponibile non sono verificabili sul codice (esclusi dalla metrica di precisione).

---

## 12. Struttura suggerita per mini-presentazione (6 slide)

1. **Titolo + elevator pitch** — "Lo scanner di accessibilità WCAG che guarda la tua app mentre la usi".
2. **Il problema** — accessibilità testata tardi, audit lenti, tool statici ciechi al comportamento reale.
3. **Come funziona** — flusso in 5 passi (seleziona → avvia → naviga → stop → report) + screenshot overlay/report se disponibili.
4. **Cosa controlla** — le 8 aree WCAG con 2-3 esempi concreti ciascuna (tabella §5).
5. **Risultati** — benchmark Nexi: da 136 segnalazioni rumorose a 6 veri positivi (precisione 100%, score 92); 127 test.
6. **Ecosistema + chiusura** — app + plugin IDE + prompt AI: "trova, capisci, correggi". Call to action: GitHub repo.

**Possibile demo live (2 min):** avvia scansione su un'app demo, naviga 2 schermate con un problema noto (es. pulsante senza etichetta), STOP, mostra report in-app e il prompt AI generato.

---

## 13. Glossario rapido (per chi presenta)

| Termine | Significato |
|---------|-------------|
| **WCAG** | Linee guida internazionali di accessibilità dei contenuti digitali |
| **TalkBack** | Screen reader di Android |
| **AccessibilityService** | API Android che permette di osservare la UI di altre app |
| **Violazione** | Problema di accessibilità rilevato, con gravità e riferimento WCAG |
| **Sessione** | Una scansione completa (avvio → stop) |
| **Falso positivo (FP)** | Problema segnalato ma non reale — il nemico n.1 di uno scanner |

---

*Fonti: `README.md`, `docs/PROJECT.md`, `docs/MANUALE_UTENTE.md`, `docs/PLUGIN_BRIDGE.md`, `app/src/main/java/dev/accessscope/scanner/data/ViolationTypes.kt` (40 tipi di violazione, 8 aree), `app/build.gradle.kts` (v1.3.1). Ultimo aggiornamento brief: luglio 2026.*
