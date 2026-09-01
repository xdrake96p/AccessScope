/**
 * Evita tap su icone accessorio (trailing) in righe form — punta all'EditText sibling.
 *
 * Regole **app-agnostiche**: struttura a11y (ImageView + EditText stesso parent), non testi AXA/Nexi.
 */
package dev.accessscope.scanner.recorder.capture

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.recorder.AccessibilityRootProvider
import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics

/**
 * Risolve tap su icona trailing in campi compositi verso l'input editabile.
 */
object FieldInputTargetResolver {

    /** Fragmenti id comuni per icone trailing (Material, Android, i18n-agnostic). */
    private val ACCESSORY_ID_FRAGMENTS = listOf(
        "icon",
        "ic_",
        "_ic",
        "img_",
        "image",
        "calendar",
        "date",
        "contact",
        "pick",
        "picker",
        "trailing",
        "end_icon",
        "suffix",
        "drawable",
        "clear",
        "dropdown",
        "chevron",
    )

    /** Content description tipiche icone campo (multilingua parziale). */
    private val ACCESSORY_CD_FRAGMENTS = listOf(
        "calendar",
        "date",
        "contact",
        "pick",
        "select",
        "choose",
        "search",
        "clear",
        "browse",
        "dropdown",
        "open",
    )

    private val BUTTON_LABELS = setOf(
        "ok", "cancel", "continue", "continua", "submit", "next", "back", "done", "save",
        "close", "chiudi", "annulla", "confirm", "conferma", "pay", "paga",
    )

    private val DISMISS_LABELS = listOf(
        "close", "chiudi", "cancel", "annulla", "dismiss", "back",
    )

    private val DISMISS_ID_FRAGMENTS = listOf(
        "close",
        "dismiss",
        "img_close",
        "toolbar_close",
        "btn_close",
        "iv_close",
        "ic_close",
    )

    /**
     * `true` se [node] è un'icona accessorio accanto a un EditText (stesso contenitore).
     */
    fun isFieldAccessoryIcon(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return false
        val sibling = findSiblingEditable(node) ?: return false
        sibling.recycle()
        return isAccessoryWidgetShape(node)
    }

    /**
     * Esito risoluzione target tap su campo form.
     *
     * @property node Nodo su cui eseguire il tap (sempre obtain separato).
     * @property redirectedFromIcon `true` se [node] è l'EditText al posto di un'icona accessorio.
     */
    data class FieldTarget(
        val node: AccessibilityNodeInfo,
        val redirectedFromIcon: Boolean,
    )

    /**
     * Tap che apre sheet picker (icona rubrica/IBAN in form).
     */
    fun isPickerOpeningTap(viewId: String?, text: String? = null): Boolean {
        val shortId = MaestroSelectorHeuristics.shortViewId(viewId)?.lowercase().orEmpty()
        if (shortId.isNotBlank() && PICKER_OPENING_ID_FRAGMENTS.any { shortId.contains(it) }) return true
        val t = text?.lowercase().orEmpty()
        return t.contains("rubrica") && t.length < 20
    }

    /** Fragmenti id che aprono overlay di selezione (rubrica, IBAN, …) — non reindirizzare a REC. */
    private val PICKER_OPENING_ID_FRAGMENTS = listOf(
        "contact",
        "rubrica",
        "beneficiar",
        "iban",
        "bank",
        "search",
        "search_contact",
        "pick",
        "picker",
        "browse",
    )

    /**
     * `true` se il tap sull'icona deve aprire un picker (rubrica / IBAN), non focalizzare il campo.
     */
    fun opensSelectionPicker(node: AccessibilityNodeInfo): Boolean {
        val shortId = MaestroSelectorHeuristics.shortViewId(node.viewIdResourceName)?.lowercase().orEmpty()
        if (shortId.isNotBlank() && PICKER_OPENING_ID_FRAGMENTS.any { shortId.contains(it) }) {
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("Image", ignoreCase = true) || node.isClickable) return true
        }
        if (!isAccessoryWidgetShape(node)) return false
        if (shortId.isNotBlank() && PICKER_OPENING_ID_FRAGMENTS.any { shortId.contains(it) }) return true
        val cd = node.contentDescription?.toString()?.lowercase().orEmpty()
        return listOf("contact", "rubrica", "iban", "bank", "search", "pick", "select", "browse")
            .any { cd.contains(it) }
    }

    /** Campo form collegato a picker (beneficiario / IBAN) per id view. */
    fun isPickerBackedViewId(viewId: String?): Boolean {
        val id = MaestroSelectorHeuristics.shortViewId(viewId)?.lowercase().orEmpty()
        if (id.isBlank()) return false
        return listOf("beneficiar", "iban", "contact", "rubrica", "cd_iban", "search").any { id.contains(it) }
    }

    /** Overlay picker aperto in almeno una root accessibility. */
    fun isPickerOverlayOpen(rootProvider: AccessibilityRootProvider): Boolean {
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        if (roots.isEmpty()) return false
        return try {
            roots.any { isSelectionPickerOverlay(it) }
        } finally {
            roots.forEach { it.recycle() }
        }
    }

    /**
     * Titolo tipico sheet picker beneficiario/IBAN (Banca MPS, Nexi, …).
     */
    fun isSelectionPickerTitle(title: String): Boolean {
        val lower = title.trim().lowercase()
        if (lower.isBlank()) return false
        return lower.contains("rubrica") ||
            lower.contains("iban") ||
            lower.contains("beneficiar") ||
            lower.contains("seleziona")
    }

    /**
     * Voce lista in picker (beneficiario, IBAN, …) — non hint di campo form.
     */
    fun looksLikePickerListItem(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false
        val compact = t.replace(" ", "")
        if (compact.matches(Regex("^IT[0-9A-Z]{13,34}$", RegexOption.IGNORE_CASE))) return true
        if (t.contains(" Srl", ignoreCase = true) || t.contains(" Spa", ignoreCase = true)) return true
        if (t.contains(" S.r.l", ignoreCase = true)) return true
        if (MaestroSelectorHeuristics.isPopupDismissLabel(t)) return false
        if (looksLikeFieldLabel(t)) return false
        return t.split(Regex("\\s+")).size >= 2 && t.length >= 8
    }

    /**
     * Label evento tap lista: testo principale o contentDescription.
     */
    fun isPickerListLabel(text: String?, contentDescription: String? = null): Boolean {
        if (!text.isNullOrBlank() && looksLikePickerListItem(text)) return true
        if (!contentDescription.isNullOrBlank() && looksLikePickerListItem(contentDescription)) return true
        return false
    }

    /**
     * Cerca titolo sheet picker visibile nel subtree.
     */
    fun findPickerTitleInTree(root: AccessibilityNodeInfo): String? {
        val direct = root.text?.toString()?.trim()?.takeIf { isSelectionPickerTitle(it) }
        if (direct != null) return direct
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findPickerTitleInTree(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Icona picker sibling di un EditText (rubrica / IBAN trailing).
     */
    fun findPickerIconNearEditable(edit: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = edit.parent
        repeat(4) {
            val p = parent ?: return null
            for (i in 0 until p.childCount) {
                val child = p.getChild(i) ?: continue
                try {
                    if (opensSelectionPicker(child)) {
                        return AccessibilityNodeInfo.obtain(child)
                    }
                } finally {
                    child.recycle()
                }
            }
            val next = p.parent
            p.recycle()
            parent = next
        }
        parent?.recycle()
        return null
    }

    /** Campo form con icona picker accanto — tap sul label apre selezione, non digitazione. */
    fun isPickerBackedField(node: AccessibilityNodeInfo): Boolean {
        val icon = findPickerIconNearEditable(node) ?: return false
        icon.recycle()
        return true
    }

    /** Se [node] è icona accessorio, restituisce l'EditText sibling; altrimenti copia di [node]. */
    fun resolveFieldTarget(node: AccessibilityNodeInfo): FieldTarget {
        if (opensSelectionPicker(node)) {
            return FieldTarget(AccessibilityNodeInfo.obtain(node), redirectedFromIcon = false)
        }
        if (!isFieldAccessoryIcon(node)) {
            return FieldTarget(AccessibilityNodeInfo.obtain(node), redirectedFromIcon = false)
        }
        val edit = findSiblingEditable(node)
        return if (edit != null) {
            FieldTarget(edit, redirectedFromIcon = true)
        } else {
            FieldTarget(AccessibilityNodeInfo.obtain(node), redirectedFromIcon = false)
        }
    }

    /**
     * Cerca EditText nello stesso contenitore dell'icona (max 4 livelli parent).
     */
    fun findSiblingEditable(iconNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent: AccessibilityNodeInfo? = iconNode.parent
        repeat(4) {
            val p = parent ?: return null
            val edit = findFirstEditableIn(p, exclude = iconNode)
            if (edit != null) {
                p.recycle()
                return edit
            }
            val next = p.parent
            p.recycle()
            parent = next
        }
        parent?.recycle()
        return null
    }

    /** Hint/label del campo per selettore testo Maestro. */
    fun fieldLabelForEditable(edit: AccessibilityNodeInfo): String? {
        edit.hintText?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it.take(80) }
        edit.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it.take(80) }
        val parent = edit.parent ?: return null
        try {
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                val t = child.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                child.recycle()
                if (t != null && looksLikeFieldLabel(t)) return t.take(80)
            }
        } finally {
            parent.recycle()
        }
        return null
    }

    /** Tap sul lato sinistro del campo (evita icona trailing a destra). */
    fun tapBoundsLeftOfCenter(edit: AccessibilityNodeInfo): Pair<Float, Float>? {
        val bounds = android.graphics.Rect()
        edit.getBoundsInScreen(bounds)
        if (bounds.isEmpty()) return null
        val x = bounds.left + (bounds.width() * 0.28f)
        val y = bounds.centerY().toFloat()
        return x to y
    }

    /**
     * Etichetta/hint di campo form (non bottone) — euristica multilingua debole.
     */
    fun looksLikeFieldLabel(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4) return false
        val lower = t.lowercase()
        if (lower in BUTTON_LABELS) return false
        if (t.contains('*')) return true
        if ('(' in t && ')' in t) return true
        if (lower.endsWith(':')) return true
        return t.length >= 10
    }

    /** @see looksLikeFieldLabel */
    fun looksLikeFieldHint(text: String): Boolean = looksLikeFieldLabel(text)

    /**
     * Overlay picker/lista aperta — titolo sheet, struttura dismiss+lista, o search+lista.
     */
    fun isSelectionPickerOverlay(root: AccessibilityNodeInfo): Boolean {
        val pickerTitle = findPickerTitleInTree(root)
        if (pickerTitle != null && isSelectionPickerTitle(pickerTitle)) {
            return hasListLikeContent(root) ||
                findDismissControl(root) != null ||
                countEditables(root) <= 1
        }
        val editables = countEditables(root)
        if (editables > 1) return false
        val hasDismiss = findDismissControl(root) != null
        val listLike = hasListLikeContent(root)
        return hasDismiss && (listLike || editables == 0)
    }

    /** Titolo picker in almeno una root accessibility. */
    fun findPickerTitleInRoots(roots: List<AccessibilityNodeInfo>): String? {
        for (root in roots) {
            findPickerTitleInTree(root)?.let { return it }
        }
        return null
    }

    private fun isAccessoryWidgetShape(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString().orEmpty()
        val imageOrBtn = cls.contains("Image", ignoreCase = true) ||
            (cls.contains("Button", ignoreCase = true) && node.text.isNullOrBlank())
        if (imageOrBtn && node.isClickable) return true
        val shortId = MaestroSelectorHeuristics.shortViewId(node.viewIdResourceName)?.lowercase().orEmpty()
        if (shortId.isNotBlank() && ACCESSORY_ID_FRAGMENTS.any { shortId.contains(it) }) return true
        val cd = node.contentDescription?.toString()?.lowercase().orEmpty()
        return ACCESSORY_CD_FRAGMENTS.any { cd.contains(it) }
    }

    private fun findDismissControl(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val t = root.text?.toString()?.trim().orEmpty()
        val cd = root.contentDescription?.toString()?.trim().orEmpty()
        if (DISMISS_LABELS.any { t.equals(it, ignoreCase = true) || cd.equals(it, ignoreCase = true) }) {
            return AccessibilityNodeInfo.obtain(root)
        }
        if (t == "×" || t.equals("x", ignoreCase = true) || cd.equals("close", ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(root)
        }
        val shortId = MaestroSelectorHeuristics.shortViewId(root.viewIdResourceName)?.lowercase().orEmpty()
        if (shortId.isNotBlank() && DISMISS_ID_FRAGMENTS.any { shortId.contains(it) }) {
            if (root.isClickable || root.className?.toString().orEmpty().contains("Image", ignoreCase = true)) {
                return AccessibilityNodeInfo.obtain(root)
            }
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findDismissControl(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun hasListLikeContent(root: AccessibilityNodeInfo): Boolean {
        val cls = root.className?.toString().orEmpty()
        if (cls.contains("RecyclerView", ignoreCase = true) ||
            cls.contains("ListView", ignoreCase = true) ||
            cls.contains("ScrollView", ignoreCase = true)
        ) {
            return root.childCount >= 2
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = hasListLikeContent(child)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun countEditables(node: AccessibilityNodeInfo): Int {
        var n = if (node.isEditable) 1 else 0
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            n += countEditables(child)
            child.recycle()
        }
        return n
    }

    private fun findFirstEditableIn(
        node: AccessibilityNodeInfo,
        exclude: AccessibilityNodeInfo,
    ): AccessibilityNodeInfo? {
        if (node.isEditable && node != exclude) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child == exclude) {
                child.recycle()
                continue
            }
            val found = findFirstEditableIn(child, exclude)
            child.recycle()
            if (found != null) return found
        }
        return null
    }
}
