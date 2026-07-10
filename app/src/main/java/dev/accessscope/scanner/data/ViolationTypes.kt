/**
 * Enumerazioni per tipi, gravità e ambiti delle violazioni WCAG.
 */
package dev.accessscope.scanner.data

/**
 * Ambiti tematici in cui vengono classificati i controlli e le violazioni di accessibilità.
 *
 * Ogni valore rappresenta una categoria WCAG con titolo, sottotitolo descrittivo ed emoji
 * per l'interfaccia utente.
 *
 * @property title Titolo breve mostrato nell'UI.
 * @property subtitle Spiegazione in linguaggio semplice dell'ambito controllato.
 * @property emoji Simbolo grafico associato all'ambito.
 */
enum class ViolationArea(
    val title: String,
    val subtitle: String,
    val emoji: String,
) {
    /** Etichette, nomi accessibili e descrizioni di pulsanti e immagini. */
    LABELS(
        title = "Etichette e nomi",
        subtitle = "Ogni pulsante e immagine deve dire cosa fa",
        emoji = "🏷️",
    ),
    /** Dimensioni minime e spaziatura tra target di tocco. */
    TOUCH(
        title = "Tocco e dimensioni",
        subtitle = "Pulsanti abbastanza grandi e distanziati",
        emoji = "👆",
    ),
    /** Contrasto tra testo, icone e sfondo. */
    COLOR(
        title = "Colori e contrasto",
        subtitle = "Testo leggibile sullo sfondo",
        emoji = "🎨",
    ),
    /** Dimensione, leggibilità e troncamento del testo. */
    TEXT(
        title = "Testo e tipografia",
        subtitle = "Caratteri non troppo piccoli o tagliati",
        emoji = "🔤",
    ),
    /** Campi input, etichette, errori e campi obbligatori. */
    FORMS(
        title = "Moduli e campi",
        subtitle = "Input con etichetta, errori e campi obbligatori",
        emoji = "📝",
    ),
    /** Titoli, ordine di lettura, liste e tabelle. */
    STRUCTURE(
        title = "Struttura e navigazione",
        subtitle = "Titoli, ordine di lettura, liste e tabelle",
        emoji = "🧭",
    ),
    /** Comportamento con TalkBack e annunci dello screen reader. */
    SCREEN_READER(
        title = "Screen reader (TalkBack)",
        subtitle = "Cosa sentirebbe chi usa la lettura vocale",
        emoji = "🔊",
    ),
    /** WebView, media e componenti avanzati non nativi. */
    MEDIA_WEB(
        title = "Web e contenuti speciali",
        subtitle = "WebView, media e componenti avanzati",
        emoji = "🌐",
    ),
}

/**
 * Livello di gravità di una violazione di accessibilità.
 *
 * @property label Etichetta localizzata mostrata nei report e nell'UI.
 */
enum class ViolationSeverity(val label: String) {
    /** Problema bloccante per l'uso con tecnologie assistive. */
    CRITICAL("Critico"),
    /** Problema significativo che ostacola l'accessibilità. */
    SERIOUS("Grave"),
    /** Problema rilevante ma con impatto moderato. */
    MODERATE("Medio"),
    /** Problema minore o di miglioramento consigliato. */
    MINOR("Lieve"),
}

/**
 * Tipi specifici di violazione WCAG rilevabili durante la scansione.
 *
 * Ogni valore associa un nome leggibile, un riferimento WCAG, la gravità,
 * l'ambito tematico e un suggerimento in linguaggio semplice.
 *
 * @property displayName Nome human-readable del tipo di violazione.
 * @property wcagRef Riferimento alla linea guida WCAG (es. «WCAG 4.1.2»).
 * @property severity Gravità predefinita della violazione.
 * @property area Ambito tematico di appartenenza.
 * @property plainHint Spiegazione semplificata per l'utente finale.
 */
enum class ViolationType(
    val displayName: String,
    val wcagRef: String,
    val severity: ViolationSeverity,
    val area: ViolationArea,
    val plainHint: String,
) {
    MISSING_LABEL(
        "Etichetta mancante", "WCAG 4.1.2", ViolationSeverity.CRITICAL, ViolationArea.LABELS,
        "Un pulsante o icona non dice cosa fa quando TalkBack ci passa sopra.",
    ),
    SMALL_TOUCH_TARGET(
        "Target di tocco piccolo", "WCAG 2.5.5", ViolationSeverity.SERIOUS, ViolationArea.TOUCH,
        "Il pulsante è troppo piccolo per essere premuto con facilità.",
    ),
    INSUFFICIENT_TOUCH_SPACING(
        "Pulsanti troppo vicini", "WCAG 2.5.8", ViolationSeverity.MODERATE, ViolationArea.TOUCH,
        "Due pulsanti sono così vicini che si rischia di premere quello sbagliato.",
    ),
    OVERLAPPING_TOUCH_TARGETS(
        "Pulsanti sovrapposti", "WCAG 2.5.5", ViolationSeverity.SERIOUS, ViolationArea.TOUCH,
        "Due aree cliccabili si sovrappongono.",
    ),
    HEADING_HIERARCHY(
        "Titolo non marcato", "WCAG 1.3.1", ViolationSeverity.MODERATE, ViolationArea.STRUCTURE,
        "Un titolo visibile non è marcato come intestazione per lo screen reader.",
    ),
    HEADING_LEVEL_SKIP(
        "Salto livello titolo", "WCAG 1.3.1", ViolationSeverity.MODERATE, ViolationArea.STRUCTURE,
        "I titoli saltano un livello (es. da H1 a H3) e confondono la struttura.",
    ),
    ILLOGICAL_FOCUS_ORDER(
        "Ordine di lettura illogico", "WCAG 2.4.3", ViolationSeverity.SERIOUS, ViolationArea.STRUCTURE,
        "TalkBack legge gli elementi in un ordine diverso da come appaiono sullo schermo.",
    ),
    NOT_FOCUSABLE(
        "Non raggiungibile con TalkBack", "WCAG 2.1.1", ViolationSeverity.CRITICAL, ViolationArea.SCREEN_READER,
        "Un elemento interattivo non può essere selezionato con lo screen reader.",
    ),
    INPUT_LABEL(
        "Campo senza etichetta", "WCAG 3.3.2", ViolationSeverity.CRITICAL, ViolationArea.FORMS,
        "Un campo di testo non dice a cosa serve.",
    ),
    INPUT_ERROR_MISSING(
        "Errore non annunciato", "WCAG 3.3.1", ViolationSeverity.SERIOUS, ViolationArea.FORMS,
        "C'è un errore visibile ma TalkBack non lo comunica.",
    ),
    REQUIRED_FIELD_UNMARKED(
        "Campo obbligatorio non indicato", "WCAG 3.3.2", ViolationSeverity.MODERATE, ViolationArea.FORMS,
        "Un campo obbligatorio non è segnalato come tale.",
    ),
    TEXT_TOO_SMALL(
        "Testo troppo piccolo", "WCAG 1.4.4", ViolationSeverity.SERIOUS, ViolationArea.TEXT,
        "Il testo è difficile da leggere perché troppo piccolo.",
    ),
    TEXT_TRUNCATED(
        "Testo tagliato", "WCAG 1.4.4", ViolationSeverity.MODERATE, ViolationArea.TEXT,
        "Il testo è troncato con «…» senza modo di leggerlo tutto.",
    ),
    LOW_COLOR_CONTRAST(
        "Contrasto colore basso", "WCAG 1.4.3", ViolationSeverity.CRITICAL, ViolationArea.COLOR,
        "Il testo si confonde con lo sfondo.",
    ),
    LOW_NON_TEXT_CONTRAST(
        "Icona poco visibile", "WCAG 1.4.11", ViolationSeverity.MODERATE, ViolationArea.COLOR,
        "Un'icona o controllo ha poco contrasto rispetto allo sfondo.",
    ),
    IMAGE_MISSING_ALT(
        "Immagine senza descrizione", "WCAG 1.1.1", ViolationSeverity.CRITICAL, ViolationArea.LABELS,
        "Un'immagine importante non ha testo alternativo.",
    ),
    DECORATIVE_IMAGE_LABELED(
        "Immagine decorativa etichettata", "WCAG 1.1.1", ViolationSeverity.MINOR, ViolationArea.LABELS,
        "Un'immagine decorativa ha un'etichetta inutile che distrae TalkBack.",
    ),
    POOR_ALT_TEXT(
        "Descrizione immagine scadente", "WCAG 1.1.1", ViolationSeverity.MODERATE, ViolationArea.LABELS,
        "Il testo alternativo è generico o poco utile (es. «immagine», nome file).",
    ),
    LINK_NOT_DESCRIPTIVE(
        "Link poco chiaro", "WCAG 2.4.4", ViolationSeverity.SERIOUS, ViolationArea.LABELS,
        "Il link dice solo «clicca qui» o «altro» senza spiegare la destinazione.",
    ),
    DUPLICATE_LINK_TEXT(
        "Link uguali, destinazioni diverse", "WCAG 2.4.4", ViolationSeverity.MODERATE, ViolationArea.LABELS,
        "Più link hanno lo stesso testo ma probabilmente portano posti diversi.",
    ),
    DUPLICATE_ACCESSIBLE_NAME(
        "Nome duplicato", "WCAG 4.1.2", ViolationSeverity.MODERATE, ViolationArea.LABELS,
        "Più elementi hanno lo stesso nome e TalkBack non li distingue.",
    ),
    REDUNDANT_ACCESSIBLE_NAME(
        "Nome ripetuto", "WCAG 4.1.2", ViolationSeverity.MINOR, ViolationArea.LABELS,
        "Lo stesso nome è ripetuto su contenitore e contenuto.",
    ),
    SCROLLABLE_WITHOUT_LABEL(
        "Area scroll senza nome", "WCAG 4.1.2", ViolationSeverity.MODERATE, ViolationArea.STRUCTURE,
        "Un'area scorrevole non ha un nome accessibile.",
    ),
    DISABLED_WITHOUT_INDICATION(
        "Disabilitato non indicato", "WCAG 4.1.2", ViolationSeverity.MODERATE, ViolationArea.SCREEN_READER,
        "Un controllo disattivato non comunica il suo stato.",
    ),
    EXPANDABLE_STATE_MISSING(
        "Stato espansione mancante", "WCAG 4.1.2", ViolationSeverity.MODERATE, ViolationArea.SCREEN_READER,
        "Un menu espandibile non dice se è aperto o chiuso.",
    ),
    PASSWORD_NOT_MASKED(
        "Password non mascherata", "WCAG 2.2.4", ViolationSeverity.CRITICAL, ViolationArea.FORMS,
        "Un campo password non è marcato come tale.",
    ),
    MODAL_WITHOUT_TITLE(
        "Finestra senza titolo", "WCAG 2.4.2", ViolationSeverity.SERIOUS, ViolationArea.STRUCTURE,
        "Un popup o dialogo non ha un titolo chiaro.",
    ),
    COLLECTION_WITHOUT_STRUCTURE(
        "Lista senza struttura", "WCAG 1.3.1", ViolationSeverity.MODERATE, ViolationArea.STRUCTURE,
        "Una lista lunga non espone informazioni di struttura a TalkBack.",
    ),
    TABLE_HEADER_MISSING(
        "Tabella senza intestazioni", "WCAG 1.3.1", ViolationSeverity.MODERATE, ViolationArea.STRUCTURE,
        "Una tabella non ha righe o colonne marcate come intestazione.",
    ),
    ROLE_UNDEFINED(
        "Ruolo non definito", "WCAG 4.1.2", ViolationSeverity.SERIOUS, ViolationArea.LABELS,
        "Un componente personalizzato non dice che tipo di controllo è.",
    ),
    SLIDER_VALUE_MISSING(
        "Slider senza valore", "WCAG 4.1.2", ViolationSeverity.MODERATE, ViolationArea.FORMS,
        "Un cursore o barra di progresso non annuncia il valore attuale.",
    ),
    TOOLTIP_INACCESSIBLE(
        "Tooltip non accessibile", "WCAG 1.4.13", ViolationSeverity.MODERATE, ViolationArea.LABELS,
        "Un suggerimento visibile non è disponibile per TalkBack.",
    ),
    CUSTOM_ACTION_UNLABELED(
        "Azione senza etichetta", "WCAG 4.1.2", ViolationSeverity.MODERATE, ViolationArea.SCREEN_READER,
        "Un'azione personalizzata non ha descrizione.",
    ),
    WEBVIEW_BARRIER(
        "WebView non accessibile", "WCAG 4.1.2", ViolationSeverity.SERIOUS, ViolationArea.MEDIA_WEB,
        "Contenuto web incorporato senza albero di accessibilità esposto.",
    ),
    MEDIA_CONTROL_UNLABELED(
        "Controllo media senza etichetta", "WCAG 1.1.1", ViolationSeverity.SERIOUS, ViolationArea.MEDIA_WEB,
        "Play, pausa o altri controlli media non hanno etichetta.",
    ),
    DUPLICATE_VIEW_ID(
        "ID vista duplicato", "WCAG 4.1.1", ViolationSeverity.MODERATE, ViolationArea.STRUCTURE,
        "Due elementi condividono lo stesso ID nella schermata.",
    ),
    DYNAMIC_CONTENT_SILENT(
        "Contenuto dinamico silenzioso", "WCAG 4.1.3", ViolationSeverity.SERIOUS, ViolationArea.SCREEN_READER,
        "Il contenuto cambia ma TalkBack non riceve alcun annuncio.",
    ),
    SCREEN_READER_ANNOUNCEMENT(
        "Annuncio assente", "WCAG 4.1.2", ViolationSeverity.CRITICAL, ViolationArea.SCREEN_READER,
        "TalkBack non avrebbe nulla da leggere su questo elemento.",
    ),
}

/** Tipo di evidenza visiva allegata a una violazione. */
enum class EvidenceKind {
    /** Crop da screenshot reale. */
    SCREENSHOT,
    /** Wireframe ricostruito su schermata FLAG_SECURE. */
    SYNTHETIC_SECURE,
}
