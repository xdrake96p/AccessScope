/**
 * Etichette, heading, decorative e alt text.
 */
package dev.accessscope.scanner.analyzer.precision

import android.graphics.Rect
import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionNavigation
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform

internal object PrecisionLabelHierarchy {
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
    if (PrecisionNavigation.isCarouselContentContainer(snap, all, packageName)) return true
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

    fun isLayoutContainer(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("layout") ||
            lower.contains("viewgroup") ||
            lower.contains("constraint") ||
            lower.contains("coordinator") ||
            lower.contains("drawer")
    }
}
