package dev.accessscope.scanner.analyzer

object PrecisionRules {

    /** Link inline in un blocco di testo: esentato da touch target 48dp se il testo è leggibile. */
    fun isInlineTextLink(snap: NodeSnapshot): Boolean {
        if (!snap.isClickable && !snap.isLongClickable) return false
        val text = snap.text?.trim().orEmpty()
        if (text.isEmpty() || text.length > 40) return false
        return snap.className.contains("TextView", true) &&
            !snap.className.contains("Button", true) &&
            snap.bounds.height() >= snap.minTextHeightPx
    }

    /** Icona dentro un pulsante che ha già etichetta testuale nel parent — spesso falso positivo. */
    fun isIconInsideLabeledButton(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean {
        if (!snap.isImageClass()) return false
        return all.any { other ->
            other != snap &&
                other.bounds.contains(snap.bounds) &&
                other.isInteractiveClickable() &&
                other.hasAccessibleName() &&
                !other.isImageClass()
        }
    }

    /** Immagine probabilmente decorativa (non interattiva). */
    fun isDecorative(snap: NodeSnapshot): Boolean = snap.isLikelyDecorative

    fun shouldSkipTouchTargetCheck(snap: NodeSnapshot, all: List<NodeSnapshot>): Boolean =
        isInlineTextLink(snap) || isIconInsideLabeledButton(snap, all)

    fun shouldSkipSmallTextCheck(snap: NodeSnapshot): Boolean {
        if (snap.className.contains("Toolbar", true)) return true
        if (snap.text?.length == 1) return true // icone tipo badge
        return snap.bounds.height() >= snap.minTextHeightPx * 0.85
    }

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

    fun isRequiredFieldHint(hint: String?, text: String?, contentDescription: String?): Boolean {
        val combined = listOfNotNull(hint, text, contentDescription).joinToString(" ").lowercase()
        return combined.contains("obbligatorio") || combined.contains("required") || combined.contains("*")
    }
}
