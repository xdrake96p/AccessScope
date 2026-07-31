/**
 * Etichette, heading, decorative e alt text.
 */
package dev.accessscope.scanner.analyzer.precision

import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionNavigation
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform

internal object PrecisionDecorativeLabels {
fun isDecorative(snap: NodeSnapshot): Boolean {
    if (PrecisionNavigation.isTopBarControl(snap)) return false
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
    if (PrecisionLabelHierarchy.isIconWithLabeledSibling(snap, all)) return true
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
}
