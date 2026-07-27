# AccessScope — Piano di Restyle (branch `restyle`)

> **Stato:** approvato con decisioni D1–D4 (23 luglio 2026) · **Base:** commit `9e2ca17` · **Materiali:** `docs/restyle/stitch_parte1` (splash, tutorial, temi) + `docs/restyle/stitch_parte2` (schermate app)
> **Regola d'oro:** restyle **solo visivo** — zero modifiche ad analyzer, service, data, bridge e contratti dati dei ViewModel. I 127 test JVM devono restare verdi a ogni fase.

---

## 1. Obiettivo

Portare l'app dal tema attuale "ciano/lavanda/viola" al nuovo design system **"Scanner & HUD"** (Futuristic Minimalist): Electric Teal `#00E5FF` come firma visiva, font Hanken Grotesk/Inter/JetBrains Mono, etichette mono uppercase, card bento, glow soft. Aggiungere le schermate mancanti: **splash animata**, **onboarding/tutorial** (6 pagine), **Preferiti** dedicata. Nuova navigazione a **due zone** con bottom bar contestuali + drawer.

## 2. Decisioni approvate (23 luglio 2026)

### D1 — Navigazione a due zone con bottom bar contestuali

Le due bottom bar dei mockup **non** sono incoerenti: servono sezioni diverse dell'app.

**Zona PRINCIPALE** (sezione home) — bottom bar `Home / Preferiti / Settings`:

| Tab | Destinazione | Stato |
|---|---|---|
| Home | `HomeScreen` | esistente, da restilizzare |
| Preferiti | `FavoritesScreen` | **nuova** (da mockup `preferiti_accessscope`) |
| Settings | `SettingsScreen` | esistente, da restilizzare |

**Zona SESSIONE** — si entra dalla home con **"Vedi dettagli"** della card Ultima sessione — bottom bar `Scansione / Dettagli / Report / Storico`:

| Tab | Mockup di riferimento | Schermata app |
|---|---|---|
| Scansione | `report_scansione_nav_allineata` (donut + elementi scansionati) | `ReportScreen` (restilizzata) |
| Dettagli | `dettaglio_anomalia_nav_allineata` | `ViolationDetailScreen` (restilizzata) |
| Report | `report_dinamico_nav_allineata` (schermate + badge) | `DynamicReportScreen` (restilizzata) |
| Storico | `storico_scansioni_accessscope` | `ScanHistoryScreen` (restilizzata) |

Implementazione: un solo `NavHost`; la bottom bar è un composable nello `Scaffold` radice che **cambia set di tab in base alla route corrente** (zona principale vs zona sessione). La zona sessione opera sulla sessione selezionata (package + sessionId passati via argomenti di navigazione).

### D2 — Navigation drawer: SÌ

`ModalNavigationDrawer` nella zona principale (da `home_page_menu_aperto`): header con logo + "AccessScope — Accessibility Auditor" + versione (`BuildConfig.VERSION_NAME`), voci: **Cronologia scansioni**, **Ultima sessione**, **Suggerimenti e Segnalazioni**. Voce attiva con bg `primaryContainer` + barra sinistra `primary`.

### D3 — Preferiti: schermata dedicata

Nuova `FavoritesScreen` (tab 2 della zona principale): search bar "Cerca app da aggiungere", griglia/card App Preferite (stella amber piena, categoria, conteggio), lista "Altre App" con stella vuota per aggiungere. Riusa la logica preferiti esistente del ViewModel.

### D4 — Font: downloadable Google Fonts

`ui-text-google-fonts` + certs: **Hanken Grotesk** (display/headline), **Inter** (body), **JetBrains Mono** (label/chip/meta). Fallback `FontFamily.SansSerif/Monospace` se non disponibili. Il dark DESIGN.md cita Geist: **ignorato**, si unifica su Hanken Grotesk.

## 3. Inventario codice attuale

| Area | File | Note |
|------|------|------|
| Tema | `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`, `Motion.kt`, `Focus.kt` | Palette custom ciano/viola; font di sistema; nessun font bundled |
| Nav | `MainActivity.kt` → `AccessScopeNavHost` | Route: `home`, `settings`, `feedback`, `log_checker`, `dynamic_report`, `report`, `violation_detail/{key}`, `history/{pkg}`. **Niente** splash, onboarding, bottom bar, drawer |
| Home | `HomeScreen.kt` (340) + `home/HomeScanActionBar`, `HomeAppSelectionPanel`, `HomePdfResultCard` | Usa `HeroHeader`, `PermissionsCard`, `ScanDashboard` (405 righe) |
| Report | `ReportScreen.kt` + `report/*` | `DynamicReportScreen.kt` + `ScreenFilmstrip`, `ScreenIssueCanvas`, `ScreenIssueList` |
| Dettaglio | `ViolationDetailScreen.kt` + `ViolationDetailUi`, `ViolationEvidenceViewer` | |
| Storico | `ScanHistoryScreen.kt` + `SessionComparisonCard` | |
| Settings | `SettingsScreen.kt` + `ThemeModeSelector` | Sezioni piatte, non accordion |
| Feedback | `FeedbackScreen.kt` | Apre GitHub Issues |
| Assets | `ic_access_scope_logo.xml`, logo PNG/SVG in `docs/assets/` | Nessuna illustrazione 3D nell'app |

## 4. Discrepanze dei mockup → correzioni obbligatorie

I mockup Stitch contengono testi **sbagliati** rispetto alla realtà dell'app. Nel restyle si usano i layout, **non** i testi errati:

| Mockup dice | Realtà corretta |
|---|---|
| "Invia su **GitLab**" | **GitHub** Issues (come `FeedbackScreen` attuale) |
| "Il **bot** simula l'interazione utente", "processo **basato su cloud**" | Navigazione **manuale** dell'utente, analisi **100% on-device** |
| "**37** parametri" | **40 tipi di violazione** in 8 aree WCAG |
| "v2.4.0 PRO" | `BuildConfig.VERSION_NAME` (ora 1.3.1) |
| "Punteggio **stimato** 53/100" (report dinamico) | Punteggio reale da `ScanSessionRepository` |
| Font dark = **Geist** | Unificato: **Hanken Grotesk** (D4) |
| Onboarding HTML: 3 slide ma 6 pallini | Tutorial = **6 pagine** (F1) |

## 5. Token mapping (vecchio → nuovo)

**Colori (`Color.kt` + `Theme.kt`)** — da `accessscope_design_system/DESIGN.md` + `accessscope_dark/DESIGN.md`:

| Ruolo M3 | Light nuovo | Dark nuovo | Vecchio (da rimuovere) |
|---|---|---|---|
| primary | `#006875` | `#C3F5FF` | `#0891B2` / `#22D3EE` |
| primaryContainer (CTA, glow) | `#00E5FF` | `#00E5FF` | `#E0F2FE` / `#164E63` |
| secondary | `#5B00DF` | `#D1BEEF` | `#7C3AED` / `#A78BFA` |
| tertiary (warning/amber) | `#765A00` (container `#FEC931`) | `#DCF0F8` | `#D97706` |
| error | `#BA1A1A` | `#FFB4AB` | `#DC2626` |
| background/surface | `#FCF9F8` | `#0D1518` | `#F8F9FA` / `#121212` |
| surfaceContainer family | `#FFFFFF`→`#E5E2E1` (5 tier) | `#070F12`→`#2E3639` (5 tier) | — (nuovo) |
| outline / outlineVariant | `#6B7A7D` / `#BAC9CC` | `#849396` / `#3B494C` | slate/grigio |
| success (semantico custom) | primaryContainer teal | primaryFixed `#9CF0FF` | `#059669` |
| favorite accent | `#FEC931` | `#F3BF26` | `#F59E0B` |

`severityColor()`: CRITICAL→error, SERIOUS→error soft, MODERATE→tertiary amber, MINOR→outline. I tier `surfaceContainer*` sono nativi M3.

**Tipografia (`Type.kt`)** — tri-font per entrambi i temi:

| Ruolo | Font | Uso |
|---|---|---|
| display/headline | **Hanken Grotesk** Bold/SemiBold (40/32/28/24sp) | titoli, score donut |
| body/title | **Inter** (16/18sp, LH 1.5) | testi |
| label | **JetBrains Mono** Medium 12/14sp, **uppercase**, tracking 0.05–0.08em | chip, badge, tab bar, meta-dati, `CodeTextStyle` |

**Forme (`Shape.kt`)**: small 8dp, card 16dp, hero 24dp invariati; **chip e CTA primari → pill `full`** (firma del nuovo design: pulsante "Avvia scansione" pill `#00E5FF`).

**Effetti**: glow = ombra 30dp blur 12% tintata primary (solo CTA primaria e card attive); glass = bordo 1dp bianco 10% + blur (solo dark, level 2). Niente WebGL: lo shader della splash diventa **gradiente animato** in Compose (`Brush` + `infiniteTransition`).

## 6. Piano per fasi

Ogni fase = commit separato, verifica `./gradlew :app:testDebugUnitTest :app:assembleDebug` + screenshot light/dark. A fine fase: KDoc aggiornata + `docs/PROJECT.md` (regole Cursor del repo).

### F0 — Design tokens (fondamenta) · ~0.5g
- Riscrittura `Color.kt` (palette completa + semantici), `Theme.kt` (due `colorScheme` completi con tier surface), `Type.kt` (tri-font downloadable, D4), `Shape.kt` (pill CTA).
- Aggiornare `themes.xml` (`windowBackground` → nuovo surface) e `colors.xml`.
- Sweep dei riferimenti diretti ai vecchi colori nei componenti: tutto via `colorScheme`, niente costanti hardcoded.
- Dipendenze: `androidx.compose.ui:ui-text-google-fonts` + font certs in `res/values`.
- **Deliverable:** app identica nel comportamento, già "vestita" coi nuovi token. Test verdi.

### F1 — Splash + Onboarding (schermate nuove) · ~1.5g
- `ui/screen/onboarding/SplashScreen.kt`: logo con entrance (scale 0.8→1 + fade + blur), scanline animata, label mono `INITIALIZING HUD INTERFACE`, versione da `BuildConfig`, sfondo gradiente teal animato, corner accents HUD.
- `ui/screen/onboarding/OnboardingScreen.kt`: `HorizontalPager` **6 pagine** con copy **corretto** (§4):
  1. Benvenuto (illustrazione scanner 3D da `stitch_parte1`, importata in `drawable`)
  2. Il problema (3 card: audit lenti / tool statici / report frammentati)
  3. Ecosistema (App mobile / Plugin Studio / VS Code + "Fix con IA")
  4. Come funziona (5 step: Seleziona→Avvia→**Navighi tu**→Stop→Report)
  5. Cosa controlliamo (8 aree WCAG con icone, da `ViolationArea`)
  6. Risultati certificati (136→6, score 92, 127 test — benchmark reali)
- UI: progress dots con glow sull'attivo, "Salta tutto", CTA "Avanti"→"Inizia subito", checkbox "Non mostrare più al prossimo avvio".
- `OnboardingStore` (SharedPreferences) + `startDestination` dinamica in `MainActivity`: `splash` → `onboarding` (solo primo avvio) → `home`.

### F2 — Navigazione a due zone + Home · ~2.5g
- **Bottom bar contestuale** nello Scaffold radice (`MainActivity`): zona principale `Home/Preferiti/Settings` (icona attiva FILL + dot glow), zona sessione `Scansione/Dettagli/Report/Storico`; switch del set di tab in base alla route (D1).
- **ModalNavigationDrawer** (D2) con header brand + 3 voci.
- `HomeScreen`: hero card "Pronto all'analisi?" (bg `primary` + grid pattern + glow, CTA pill `primaryContainer`), bento "Ultima sessione" (donut % + TOTALI/OK/KO) con **"VEDI DETTAGLI" → entra nella zona sessione**, CTA "Vedi report dinamico", search underline→teal on focus, toggle auto-launch, `AppListRow` restyle.
- `AccessScopeTopBar`: hamburger (drawer) + logo + titolo Hanken Grotesk.
- `ScanDashboard` restyle coerente; `HeroHeader` pensionato (assorbito dalla hero card).

### F3 — Zona sessione: Report · ~2g
- `ReportScreen` (+`report/*`) = tab **Scansione**: donut "Distribuzione Risultati" (stroke 12dp, glow teal, centro Hanken 40sp), strip TOTALE/OK/KO, lista elementi con barra laterale severità + chip pill mono (CRITICO/GRAVE/MEDIO/LIEVE/SUCCESS).
- `DynamicReportScreen` = tab **Report**: summary card punteggio reale, severity filter chips, card schermata con screenshot + badge numerati (restyle `ScreenIssueCanvas`/`ScreenFilmstrip`), error card con left-bar 4dp per severità.
- `ViolationDetailScreen` (+`ViolationDetailUi`) = tab **Dettagli**: hero chip severità + ID mono, **"Anteprima visiva" before/after** per violazioni contrasto (stato attuale vs correzione con rapporti x.x:1 — dati da `WcagContrastMeasurement`), "Azioni correttive" numerate, box "Pro Tip", bento tecnico, bottom bar "Condividi report". *(Il bottone "Segna come risolto" del mockup è feature nuova → fuori scope.)*

### F4 — Storico + Preferiti · ~1g
- `ScanHistoryScreen` = tab **Storico**: card sessione (icona app 48dp, titolo, data mono, trend arrow +N/−M verde/rosso da `SessionComparisonHelper`, chip score `NN/100`).
- `FavoritesScreen` (**nuova**, D3): search "Cerca app da aggiungere", card preferiti con stella amber piena `#FEC931` + categoria + chip conteggio, lista "Altre App" con stella vuota toggle. Logica preferiti riusata dal ViewModel esistente.

### F5 — Settings + Feedback · ~1.5g
- `SettingsScreen`: sezioni **accordion** (chevron ruota, expanded tint primary soft): Permessi (badge `2/2`), Ambiti di scansione, Categorie di controllo (8 aree, emoji + switch), Preferenze (theme selector Chiaro/Scuro/Auto a 3 card), Diagnostica, Legali, Suggerimenti. **Danger Zone** (elimina cronologia, bordo error).
- `FeedbackScreen`: segmented Bug/Suggerimento/Altro, input underline + floating label, textarea, CTA "**Invia su GitHub**".

### F6 — Polish & dogfooding · ~1g
- Overlay scansione (service) coerente coi nuovi token.
- Motion: spring nav, micro-scale bottoni.
- **Verifica WCAG AA/AAA della nuova palette con AccessScope stesso** (dogfooding — utile anche per la presentazione).
- Pass dark completo; aggiornamento `MANUALE_UTENTE.md`, `PROJECT.md`, `PRESENTATION_BRIEF.md` (nuovi screenshot).

**Stima totale: ~10 giorni** (F0–F6).

## 7. Invarianti e verifica

- **Non si toccano:** `analyzer/`, `service/`, `data/`, `bridge/`, `export/` e i modelli dati. I ViewModel cambiano solo se serve esporre stato UI già disponibile (es. sessione selezionata per la zona sessione) — nessuna modifica di logica.
- 127 test JVM verdi a ogni fase; `assembleDebug` pulito.
- KDoc in italiano su ogni file toccato; `docs/PROJECT.md` aggiornato a fine fase (regola Cursor `.cursor/rules/project-maintenance.mdc`).
- Palette finale validata AA minimo su entrambi i temi (dogfooding F6).
