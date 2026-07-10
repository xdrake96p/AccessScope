/**
 * Etichette, heading, decorative e alt text.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionHome
import dev.accessscope.scanner.analyzer.precision.PrecisionNavigation
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform

internal object PrecisionLabels {
    /**
     * Verifica se un'icona è contenuta all'interno di un pulsante già etichettato testualmente.
     *
     * In questi casi segnalare l'icona come priva di etichetta produce spesso un falso positivo,
     * poiché il nome accessibile è già fornito dal contenitore cliccabile padre.
     *
     * @param snap Snapshot del nodo icona da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se l'icona è dentro un pulsante etichettato; `false` altrimenti.
     */
    fun isIconInsideLabeledButton(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        if (hasLabeledClickableAncestor(snap, all)) return true
        if (isIconWithLabeledSibling(snap, all)) return true
        return all.any { other ->
            other != snap &&
                other.bounds.contains(snap.bounds) &&
                other.isInteractiveClickable() &&
                other.hasAccessibleName() &&
                !other.isImageClass()
        }
    }

    /**
     * Verifica se un'icona ha un fratello (sibling) con etichetta accessibile nello stesso contenitore.
     *
     * Pattern comune in swipe action (tick/cestino) e stati empty state, dove l'icona decorativa
     * convive con un testo etichettato nello stesso gruppo visivo.
     *
     * @param snap Snapshot del nodo icona da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un fratello etichettato nello stesso contenitore; `false` altrimenti.
     */
    fun isIconWithLabeledSibling(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        val container = findSmallestContainer(snap, all) ?: return false
        return all.any { sibling ->
            sibling != snap &&
                sibling != container &&
                container.bounds.contains(sibling.bounds) &&
                !sibling.isImageClass() &&
                sibling.hasAccessibleName()
        }
    }

    /**
     * Trova il contenitore più piccolo che include completamente i bounds del nodo dato.
     *
     * @param snap Snapshot del nodo di riferimento.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return Lo snapshot del contenitore con area minima che contiene il nodo, oppure `null` se assente.
     */
    private fun findSmallestContainer(snap: NodeSnapshot, all: List<NodeSnapshot>): NodeSnapshot? =
        all.filter { it != snap && it.bounds.contains(snap.bounds) }
            .minByOrNull { it.bounds.width() * it.bounds.height() }

    /**
     * Verifica se il nodo ha un antenato cliccabile con nome accessibile.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un antenato cliccabile etichettato; `false` altrimenti.
     */
    fun hasLabeledClickableAncestor(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        return all.any { candidate ->
            candidate.traversalIndex < snap.traversalIndex &&
                candidate.isInteractiveClickable() &&
                candidate.hasAccessibleName() &&
                candidate.bounds.contains(snap.bounds)
        }
    }

    /**
     * Verifica se il nodo ha almeno un discendente con nome accessibile.
     *
     * @param snap Snapshot del nodo contenitore da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un discendente etichettato; `false` altrimenti.
     */
    fun hasLabeledDescendant(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                snap.bounds.contains(other.bounds) &&
                other.hasAccessibleName()
        }

    /**
     * Verifica se il nodo ha discendenti etichettati che intersecano i suoi bounds.
     *
     * Variante di [hasLabeledDescendant] utile per ScrollView, dove i bounds stretti dei figli
     * possono non essere completamente contenuti nel rettangolo del contenitore scrollabile.
     *
     * @param snap Snapshot del nodo contenitore scrollabile da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se esiste un discendente etichettato in intersezione; `false` altrimenti.
     */
    fun hasLabeledDescendantInScroll(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        all.any { other ->
            other != snap &&
                Rect.intersects(snap.bounds, other.bounds) &&
                other.hasAccessibleName()
        }
    /**
     * Determina se il controllo etichetta del container cliccabile va saltato.
     *
     * Esclude container il cui figlio espone già il nome accessibile (ad esempio CustomViewButtonCta
     * con `tv_custom`), container home, carousel e layout con discendenti etichettati.
     *
     * @param snap Snapshot del nodo contenitore da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il controllo etichetta va saltato; `false` altrimenti.
     */
    fun shouldSkipContainerLabelCheck(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (PrecisionHome.shouldSkipHomeWidgetAnalysis(snap, all, packageName)) return true
        if (PrecisionNavigation.isCarouselContentContainer(snap, all, packageName)) return true
        if (PrecisionHome.isCtaContainer(snap, packageName) && PrecisionHome.hasTvCustomDescendant(snap, all)) return true
        if (!snap.hasAccessibleName()) {
            if (hasLabeledDescendant(snap, all) || hasLabeledDescendantInScroll(snap, all)) return true
            if (isLayoutContainer(snap.className) || snap.isCustomView()) {
                return all.any { other ->
                    other != snap &&
                        snap.bounds.contains(other.bounds) &&
                        other.hasAccessibleName() &&
                        other.hasVisibleText()
                }
            }
        }
        return false
    }
    /**
     * Determina se il controllo heading strutturale va saltato per il nodo.
     *
     * Badge di stato, pill, etichette di campo e testi decorativi in maiuscolo non costituiscono
     * heading strutturali di pagina e non devono essere valutati come tali.
     *
     * @param snap Snapshot del nodo da valutare.
     * @return `true` se il controllo heading va saltato; `false` altrimenti.
     */
    fun shouldSkipHeadingCheck(snap: NodeSnapshot): Boolean {
        if (PrecisionTouch.isLikelyStatusBadge(snap)) return true
        val viewIdShort = PrecisionGeometry.viewIdShort(snap)
        if (viewIdShort == "state" || viewIdShort.contains("badge") || viewIdShort.contains("status")) {
            return true
        }
        if (viewIdShort in setOf(
                "last_access", "name_account", "labelcontacts", "enroll_user",
                "tv_custom", "topbar_title", "no_result", "filtri_attivi",
                "totale_distinte", "total_amount_ins", "user_type", "currency",
                "multiple_slection", "checkbox_all", "rotate_display", "logo",
                "tv_title_second_section", "show_more",
            )
        ) {
            return true
        }
        if (isListFieldLabel(snap)) return true
        val text = snap.text?.trim().orEmpty()
        if (text.isNotEmpty() && text == text.uppercase() && text.length <= 24 &&
            snap.bounds.height() <= snap.minTouchTargetPx
        ) {
            return true
        }
        return false
    }

    /**
     * Verifica se il nodo è un'etichetta di campo in card o lista.
     *
     * Combina gli ID definiti nel profilo app con pattern generici di label (`label`, `iban`,
     * `amount`, `hint`, ecc.).
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un'etichetta di campo lista/card; `false` altrimenti.
     */
    fun isListFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id.isEmpty()) return false
        if (id in AppPrecisionProfiles.fieldLabelIds(packageName)) return true
        return isGenericFieldLabelPattern(id)
    }

    /**
     * Verifica se un view ID corrisponde a un pattern generico di etichetta di campo.
     *
     * @param id Identificatore breve del view ID già normalizzato in minuscolo.
     * @return `true` se l'ID corrisponde a un pattern di field label; `false` altrimenti.
     */
    private fun isGenericFieldLabelPattern(id: String): Boolean =
        id.startsWith("txt_data_") ||
            id.startsWith("data_") ||
            id.contains("label") ||
            id.contains("iban") ||
            id.contains("amount") ||
            id.contains("email") ||
            id.contains("phone") ||
            id.contains("causale") ||
            id.contains("description") ||
            id.contains("subtitle") ||
            id.contains("hint")

    /**
     * Verifica se il nodo è un'etichetta di campo nota per i controlli di contrasto.
     *
     * Alias di [isListFieldLabel] usato nel contesto dei filtri di contrasto colore.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se il nodo è un'etichetta di campo nota; `false` altrimenti.
     */
    fun isKnownContrastFieldLabel(snap: NodeSnapshot, packageName: String = ""): Boolean =
        isListFieldLabel(snap, packageName)

    /**
     * Verifica se una stringa di testo rappresenta un importo o valuta.
     *
     * @param text Testo da analizzare.
     * @return `true` se il testo corrisponde a un pattern di importo/valuta; `false` altrimenti.
     */
    fun isCurrencyOrAmountText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.matches(Regex("""^[\d\s.,€$+-]+$"""))) return true
        return t.matches(Regex("""^\d{1,3}(\.\d{3})*,\d{2}\s*€?$"""))
    }

    /**
     * Verifica se un view ID appartiene ai template di item lista configurati nel profilo app.
     *
     * @param viewId View ID completo del nodo (con eventuale prefisso package).
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se l'ID è un template lista noto; `false` altrimenti.
     */
    fun isKnownListTemplateId(viewId: String?, packageName: String = ""): Boolean {
        if (viewId.isNullOrBlank()) return false
        return viewId.substringAfterLast('/').lowercase() in AppPrecisionProfiles.listTemplateIds(packageName)
    }
    /**
     * Determina se va segnalata un'azione personalizzata non etichettata sul nodo.
     *
     * Filtra nodi che hanno azioni custom senza nome accessibile, escludendo item lista, carousel,
     * widget home, container CTA etichettati e container scroll strutturali.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @param packageName Nome del package dell'app per profili di precisione specifici.
     * @return `true` se l'azione custom non etichettata va segnalata; `false` altrimenti.
     */
    fun shouldReportCustomAction(snap: NodeSnapshot, all: List<NodeSnapshot>, packageName: String = ""): Boolean {
        if (snap.unlabeledActionCount <= 0) return false
        if (snap.hasAccessibleName()) return false
        if (!snap.isInteractiveClickable() && !snap.isFocusable) return false
        if (PrecisionStructural.isRecyclerListItem(snap, all)) return false
        if (PrecisionNavigation.isCarouselContentContainer(snap, all, packageName)) return false
        if (PrecisionHome.isHomeChartOrCtaWidget(snap, packageName)) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id.contains("select") || id.contains("selection")) return false
        if (id in setOf("multiple_slection", "checkbox_all") && snap.hasAccessibleName()) return false
        if (PrecisionHome.isCtaContainer(snap, packageName) && (PrecisionHome.hasTvCustomDescendant(snap, all) || hasLabeledDescendant(snap, all))) {
            return false
        }
        val cls = snap.className.lowercase()
        if (PrecisionStructural.isScrollContainer(snap)) return false
        if (cls.contains("recyclerview") || cls.contains("scrollview") || cls.contains("viewpager")) {
            return false
        }
        if (PrecisionGeometry.viewIdShort(snap) in setOf("scrollview_port", "scroll", "card_home")) return false
        if (PrecisionGeometry.viewIdShort(snap) == "tv_custom") return false
        if (PrecisionHome.isBrandedCtaText(snap, all, packageName)) return false
        if (snap.isScrollable && hasLabeledDescendant(snap, all)) return false
        if (isLayoutContainer(snap.className) && hasLabeledDescendant(snap, all)) return false
        return true
    }

    /**
     * Verifica se il nodo è un'immagine probabilmente decorativa (non interattiva).
     *
     * @param snap Snapshot del nodo immagine da valutare.
     * @return `true` se l'immagine è considerata decorativa; `false` altrimenti.
     */
    fun isDecorative(snap: NodeSnapshot): Boolean {
        if (PrecisionNavigation.isTopBarControl(snap)) return false
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in setOf("vop_info", "dot_filter")) return false
        if (PrecisionRulesPlatform.isLottieAnimation(snap) && !snap.isInteractiveClickable()) return true
        return snap.isLikelyDecorative
    }

    /**
     * Determina se il controllo etichetta per immagine decorativa va saltato.
     *
     * @param snap Snapshot del nodo immagine da valutare.
     * @param all Elenco completo degli snapshot dei nodi nella schermata.
     * @return `true` se il controllo etichetta decorativa va saltato; `false` altrimenti.
     */
    fun shouldSkipDecorativeLabeledCheck(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (PrecisionRulesPlatform.isLottieAnimation(snap) && !snap.isInteractiveClickable()) return true
        if (!snap.isImageClass()) return false
        if (isIconWithLabeledSibling(snap, all)) return true
        if (isLikelyNavigationImage(snap, all)) return true
        if (PrecisionNavigation.isTopBarControl(snap)) {
            val cd = snap.contentDescription?.trim().orEmpty()
            return cd.isNotBlank() && !isPoorAltText(cd)
        }
        return false
    }

    /**
     * Icone freccia/chiusura/info in header o controlli di navigazione: non sono decorative.
     *
     * Evita FP tipo "mese precedente/successivo" nei calendari Material o custom.
     */
    fun isLikelyNavigationImage(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        val cd = snap.contentDescription?.trim().orEmpty()
        if (cd.isBlank() || isPoorAltText(cd)) return false
        if (snap.isInteractiveClickable() || snap.isFocusable) return true
        // Se l'icona è dentro un contenitore cliccabile (tipico: LinearLayout wrapper), non è decorativa.
        return all.any { parent ->
            parent != snap &&
                parent.isInteractiveClickable() &&
                parent.bounds.contains(snap.bounds) &&
                parent.bounds.width() <= snap.minTouchTargetPx * 3 &&
                parent.bounds.height() <= snap.minTouchTargetPx * 3
        }
    }
    /**
     * Verifica se un testo alternativo (alt text) è di qualità insufficiente.
     *
     * Identifica descrizioni generiche o placeholder (`image`, `icon`, `logo`, nomi file, ecc.)
     * che non forniscono informazione utile all'utente.
     *
     * @param text Testo alternativo da valutare.
     * @return `true` se l'alt text è considerato scadente; `false` altrimenti.
     */
    fun isPoorAltText(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.length < 2) return true
        val bad = setOf(
            "image", "img", "icon", "logo", "photo", "picture", "bitmap",
            "immagine", "foto", "icona", "logo",
        )
        if (bad.contains(t)) return true
        if (t.matches(Regex("""^(img|image|photo|icon)[-_]?\d*\.?(png|jpg|jpeg|webp|gif)?$"""))) return true
        if (t.matches(Regex("""^[a-z0-9_-]+\.(png|jpg|jpeg|webp)$"""))) return true
        return false
    }

    /**
     * Verifica se hint, testo o content description indicano un campo obbligatorio.
     *
     * @param hint Testo hint del campo, se presente.
     * @param text Testo visibile del nodo, se presente.
     * @param contentDescription Content description del nodo, se presente.
     * @return `true` se almeno uno dei testi indica un campo obbligatorio; `false` altrimenti.
     */
    fun isRequiredFieldHint(hint: String?, text: String?, contentDescription: String?): Boolean {
        val combined = listOfNotNull(hint, text, contentDescription).joinToString(" ").lowercase()
        return combined.contains("obbligatorio") || combined.contains("required") || combined.contains("*")
    }

    /**
     * Verifica se una className Android corrisponde a un container di layout strutturale.
     *
     * @param className Nome completo della classe del componente UI.
     * @return `true` se la classe rappresenta un container layout; `false` altrimenti.
     */
    fun isLayoutContainer(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("layout") ||
            lower.contains("viewgroup") ||
            lower.contains("constraint") ||
            lower.contains("coordinator") ||
            lower.contains("drawer")
    }
}
