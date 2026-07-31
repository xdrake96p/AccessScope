/**
 * Simulazione del percorso di navigazione TalkBack e degli annunci screen reader.
 *
 * Android non consente alle app terze di attivare TalkBack programmaticamente per motivi
 * di sicurezza; questo modulo lavora sugli stessi [NodeSnapshot] già raccolti per l'analisi
 * WCAG (nessuna seconda camminata dell'albero) e ricostruisce il testo che verrebbe
 * annunciato, segnalando elementi silenziosi o poco descrittivi.
 */
package dev.accessscope.scanner.analyzer

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationType

/**
 * Simula la navigazione TalkBack su una schermata e produce finding descrittivi per il
 * report screen reader.
 */
object TalkBackSimulator {

    /**
     * Individua i nodi candidati al focus TalkBack e valuta la qualità degli annunci simulati.
     *
     * @param snapshots Nodi già raccolti per la schermata (stesso input degli altri controlli WCAG).
     * @param packageName Package dell'applicazione analizzata.
     * @param screenTitle Titolo umano della schermata corrente.
     * @param screenFingerprint Fingerprint stabile della schermata (report dinamico).
     * @return Lista di [ScreenReaderFinding] per elementi silenziosi, troppo brevi o schermata innavigabile.
     */
    fun simulate(
        snapshots: List<NodeSnapshot>,
        packageName: String,
        screenTitle: String,
        screenFingerprint: String? = null,
    ): List<ScreenReaderFinding> {
        val findings = mutableListOf<ScreenReaderFinding>()
        val focusable = snapshots
            .filter { !it.isAccessibilityExcluded && isFocusCandidate(it) }
            .sortedBy { it.traversalIndex }

        var silentCount = 0
        focusable.forEach { snap ->
            val announced = buildAnnouncement(snap)
            if (announced.isNullOrBlank()) {
                silentCount++
                findings += ScreenReaderFinding(
                    packageName = packageName,
                    screenTitle = screenTitle,
                    nodeClassName = snap.className,
                    announcedText = null,
                    issue = "TalkBack non avrebbe testo da annunciare su questo elemento.",
                    viewId = snap.viewId,
                    screenFingerprint = screenFingerprint,
                    boundsLabel = snap.boundsLabel(),
                )
            } else if (announced.length < 2) {
                findings += ScreenReaderFinding(
                    packageName = packageName,
                    screenTitle = screenTitle,
                    nodeClassName = snap.className,
                    announcedText = announced,
                    issue = "Annuncio screen reader troppo breve o poco descrittivo.",
                    viewId = snap.viewId,
                    screenFingerprint = screenFingerprint,
                    boundsLabel = snap.boundsLabel(),
                )
            }
        }

        if (focusable.isEmpty()) {
            findings += ScreenReaderFinding(
                packageName = packageName,
                screenTitle = screenTitle,
                nodeClassName = "—",
                announcedText = null,
                issue = "Nessun elemento focalizzabile: la schermata sarebbe quasi innavigabile con TalkBack.",
                screenFingerprint = screenFingerprint,
            )
        } else if (silentCount > focusable.size / 2) {
            findings += ScreenReaderFinding(
                packageName = packageName,
                screenTitle = screenTitle,
                nodeClassName = "—",
                announcedText = "$silentCount / ${focusable.size} elementi",
                issue = "Oltre il 50% degli elementi focalizzabili non ha un annuncio utile.",
                screenFingerprint = screenFingerprint,
            )
        }

        return findings
    }

    /** Stessi criteri usati storicamente per il focus TalkBack (nessun cambio comportamentale qui). */
    private fun isFocusCandidate(snap: NodeSnapshot): Boolean =
        snap.isFocusable || snap.isClickable || snap.isCheckable || snap.isEditable || snap.isScrollable

    /**
     * Ricostruisce il testo che TalkBack annuncerebbe per un nodo.
     *
     * Fedele al comportamento reale (non alla vecchia simulazione, che concatenava tutto):
     * `contentDescription` **sostituisce** `text` quando presente (non si accodano); `hintText`
     * è annunciato solo quando manca sia cd sia text (campo vuoto), non come terzo elemento
     * sempre in coda — altrimenti un campo etichettato correttamente con hint informativo
     * risultava "label, valore, hint", mascherando eventuali difetti reali di etichettatura.
     *
     * @param snap Nodo di cui simulare l'annuncio.
     * @return Testo annunciato simulato, oppure `null` se nessun contenuto utile.
     */
    internal fun buildAnnouncement(snap: NodeSnapshot): String? {
        val parts = mutableListOf<String>()

        val label = snap.contentDescription?.trim()?.takeIf { it.isNotBlank() }
            ?: snap.text?.trim()?.takeIf { it.isNotBlank() }
        val hint = if (label == null) snap.hintText?.trim()?.takeIf { it.isNotBlank() } else null
        (label ?: hint)?.let(parts::add)

        roleFor(snap)?.let(parts::add)

        if (snap.isHeading) parts += "intestazione"
        if (snap.isCheckable) parts += if (snap.isChecked) "selezionato" else "non selezionato"
        snap.isExpanded?.let { parts += if (it) "espanso" else "compresso" }
        snap.stateDescription?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        if (snap.isPassword) parts += "password"
        if (!snap.isEnabled) parts += "disabilitato"

        formatRange(snap)?.let(parts::add)
        formatCollectionPosition(snap)?.let(parts::add)

        return parts.distinct().joinToString(", ").ifBlank { null }
    }

    /**
     * Ruolo semantico annunciato da TalkBack in base alla classe del nodo.
     *
     * Copertura estesa rispetto alla versione precedente (solo Button/EditText/Image):
     * include i controlli che TalkBack annuncia sempre con un ruolo esplicito.
     */
    private fun roleFor(snap: NodeSnapshot): String? {
        val cls = snap.className
        return when {
            cls.contains("Switch", true) -> "interruttore"
            cls.contains("CheckBox", true) -> "casella di controllo"
            cls.contains("RadioButton", true) -> "pulsante di opzione"
            cls.contains("SeekBar", true) || cls.contains("Slider", true) -> "cursore"
            cls.contains("Spinner", true) -> "selettore"
            cls.contains("Tab", true) -> "scheda"
            cls.contains("ImageButton", true) -> "pulsante"
            cls.contains("Button", true) -> "pulsante"
            cls.contains("EditText", true) -> "campo di testo"
            cls.contains("Image", true) -> "immagine"
            else -> null
        }
    }

    /** Annuncio percentuale per slider/progress ([NodeSnapshot.rangeCurrent]/min/max). */
    private fun formatRange(snap: NodeSnapshot): String? {
        val current = snap.rangeCurrent ?: return null
        val min = snap.rangeMin
        val max = snap.rangeMax
        if (min != null && max != null && max > min) {
            val percent = ((current - min) / (max - min) * 100).toInt().coerceIn(0, 100)
            return "$percent per cento"
        }
        return "valore ${current.toInt()}"
    }

    /**
     * Posizione riga/colonna in una griglia strutturata ([NodeSnapshot.collectionRow]/Column).
     *
     * Solo indice, non "elemento N di M": lo snapshot del singolo nodo non porta il conteggio
     * totale della collection (serve `CollectionInfo` sul contenitore, non catturato oggi).
     */
    private fun formatCollectionPosition(snap: NodeSnapshot): String? {
        if (snap.collectionRow < 0 && snap.collectionColumn < 0) return null
        return buildString {
            if (snap.collectionRow >= 0) append("riga ${snap.collectionRow + 1}")
            if (snap.collectionColumn >= 0) {
                if (isNotEmpty()) append(", ")
                append("colonna ${snap.collectionColumn + 1}")
            }
        }
    }
}

/**
 * `true` se il finding descrive un elemento singolo silenzioso (non un riepilogo di schermata
 * come "nessun elemento focalizzabile" o "oltre il 50% silenzioso", che non hanno un nodo
 * puntuale a cui agganciare una violazione).
 */
fun ScreenReaderFinding.isSilentElementFinding(): Boolean =
    nodeClassName != "—" && announcedText == null

/**
 * Converte i finding del simulatore TalkBack in violazioni formali del report.
 *
 * Confidenza di default a metà tra "non fidarsi mai" e i tipi ad alta certezza (0.95f come
 * `MISSING_LABEL`): la simulazione è un'inferenza euristica su come si comporterebbe TalkBack,
 * non una regola WCAG diretta — passa comunque per il confidence gate come tutto il resto.
 *
 * @param findings Lista di [ScreenReaderFinding] prodotti da [TalkBackSimulator.simulate].
 * @param confidence Confidenza da assegnare a ciascuna violazione generata.
 * @return Lista di [AccessibilityViolation] di tipo [ViolationType.SCREEN_READER_ANNOUNCEMENT].
 */
fun TalkBackSimulator.toViolations(
    findings: List<ScreenReaderFinding>,
    confidence: Float = 0.7f,
): List<AccessibilityViolation> =
    findings.map { finding ->
        AccessibilityViolation(
            type = ViolationType.SCREEN_READER_ANNOUNCEMENT,
            viewClassName = finding.nodeClassName,
            screenTitle = finding.screenTitle,
            packageName = finding.packageName,
            details = buildString {
                append(finding.issue)
                finding.announcedText?.let { append(" Annuncio simulato: \"$it\".") }
            },
            viewId = finding.viewId,
            bounds = finding.boundsLabel,
            confidence = confidence,
            screenFingerprint = finding.screenFingerprint,
        )
    }
