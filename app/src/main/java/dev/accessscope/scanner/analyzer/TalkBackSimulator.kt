/**
 * Simulazione del percorso di navigazione TalkBack e degli annunci screen reader.
 *
 * Android non consente alle app terze di attivare TalkBack programmaticamente per motivi
 * di sicurezza; questo modulo attraversa i nodi focalizzabili e ricostruisce il testo
 * che verrebbe annunciato, segnalando elementi silenziosi o poco descrittivi.
 */
package dev.accessscope.scanner.analyzer

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.ViolationType
import dev.accessscope.scanner.data.AccessibilityViolation

/**
 * Simula la navigazione TalkBack su un albero di accessibilità e produce finding
 * descrittivi per il report screen reader.
 */
class TalkBackSimulator {

    /**
     * Attraversa i nodi focalizzabili e valuta la qualità degli annunci simulati.
     *
     * @param root Nodo radice dell'albero di accessibilità.
     * @param packageName Package dell'applicazione analizzata.
     * @param screenTitle Titolo umano della schermata corrente.
     * @param screenFingerprint Fingerprint stabile della schermata (report dinamico).
     * @return Lista di [ScreenReaderFinding] per elementi silenziosi, troppo brevi o schermata innavigabile.
     */
    fun simulate(
        root: AccessibilityNodeInfo,
        packageName: String,
        screenTitle: String,
        screenFingerprint: String? = null,
    ): List<ScreenReaderFinding> {
        val findings = mutableListOf<ScreenReaderFinding>()
        val focusableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, focusableNodes)

        var silentCount = 0
        focusableNodes.forEach { node ->
            val announced = buildAnnouncement(node)
            val className = node.className?.toString() ?: "unknown"
            val viewId = node.viewIdResourceName

            if (announced.isNullOrBlank()) {
                silentCount++
                findings += ScreenReaderFinding(
                    packageName = packageName,
                    screenTitle = screenTitle,
                    nodeClassName = className,
                    announcedText = null,
                    issue = "TalkBack non avrebbe testo da annunciare su questo elemento.",
                    viewId = viewId,
                    screenFingerprint = screenFingerprint,
                )
            } else if (announced.length < 2) {
                findings += ScreenReaderFinding(
                    packageName = packageName,
                    screenTitle = screenTitle,
                    nodeClassName = className,
                    announcedText = announced,
                    issue = "Annuncio screen reader troppo breve o poco descrittivo.",
                    viewId = viewId,
                    screenFingerprint = screenFingerprint,
                )
            }
        }

        if (focusableNodes.isEmpty()) {
            findings += ScreenReaderFinding(
                packageName = packageName,
                screenTitle = screenTitle,
                nodeClassName = "—",
                announcedText = null,
                issue = "Nessun elemento focalizzabile: la schermata sarebbe quasi innavigabile con TalkBack.",
                screenFingerprint = screenFingerprint,
            )
        } else if (silentCount > focusableNodes.size / 2) {
            findings += ScreenReaderFinding(
                packageName = packageName,
                screenTitle = screenTitle,
                nodeClassName = "—",
                announcedText = "$silentCount / ${focusableNodes.size} elementi",
                issue = "Oltre il 50% degli elementi focalizzabili non ha un annuncio utile.",
                screenFingerprint = screenFingerprint,
            )
        }

        focusableNodes.forEach { it.recycle() }
        return findings
    }

    /**
     * Raccoglie ricorsivamente i nodi candidati al focus TalkBack.
     *
     * @param node Nodo corrente in visita depth-first.
     * @param output Lista mutabile in cui aggiungere copie dei nodi focalizzabili.
     */
    private fun collectFocusableNodes(
        node: AccessibilityNodeInfo,
        output: MutableList<AccessibilityNodeInfo>,
    ) {
        if (!node.isVisibleToUser) {
            recycleChildren(node)
            return
        }

        val isFocusCandidate = node.isFocusable ||
            node.isClickable ||
            node.isCheckable ||
            node.isEditable ||
            node.isScrollable

        if (isFocusCandidate) {
            output += AccessibilityNodeInfo.obtain(node)
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectFocusableNodes(child, output)
            child.recycle()
        }
    }

    /**
     * Ricicla tutti i figli di un nodo non visibile per evitare perdite di riferimento.
     *
     * @param node Nodo padre i cui figli devono essere riciclati.
     */
    private fun recycleChildren(node: AccessibilityNodeInfo) {
        for (i in 0 until node.childCount) {
            node.getChild(i)?.recycle()
        }
    }

    /**
     * Ricostruisce il testo che TalkBack annuncerebbe per un nodo.
     *
     * Combina contentDescription, text, hint e stato (es. selezionato/non selezionato),
     * con fallback sul ruolo semantico della classe.
     *
     * @param node Nodo di cui simulare l'annuncio.
     * @return Testo annunciato simulato, oppure `null` se nessun contenuto utile.
     */
    private fun buildAnnouncement(node: AccessibilityNodeInfo): String? {
        val parts = mutableListOf<String>()

        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
        node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)

        if (node.isCheckable) {
            parts += if (node.isChecked) "selezionato" else "non selezionato"
        }

        val className = node.className?.toString().orEmpty()
        if (parts.isEmpty()) {
            val role = when {
                className.contains("Button", ignoreCase = true) -> "pulsante"
                className.contains("EditText", ignoreCase = true) -> "campo di testo"
                className.contains("Image", ignoreCase = true) -> "immagine"
                else -> null
            }
            role?.let(parts::add)
        }

        return parts.distinct().joinToString(", ").ifBlank { null }
    }
}

/**
 * Converte i finding del simulatore TalkBack in violazioni formali del report.
 *
 * @param findings Lista di [ScreenReaderFinding] prodotti da [TalkBackSimulator.simulate].
 * @return Lista di [AccessibilityViolation] di tipo [ViolationType.SCREEN_READER_ANNOUNCEMENT].
 */
fun TalkBackSimulator.toViolations(
    findings: List<ScreenReaderFinding>,
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
            screenFingerprint = finding.screenFingerprint,
        )
    }
