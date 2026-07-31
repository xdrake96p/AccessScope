/**
 * PIN pad e CTA generiche.
 */
package dev.accessscope.scanner.analyzer.precision

import dev.accessscope.scanner.analyzer.AppPrecisionProfiles
import dev.accessscope.scanner.analyzer.NodeSnapshot
import dev.accessscope.scanner.analyzer.precision.PrecisionGeometry
import dev.accessscope.scanner.analyzer.precision.PrecisionStructural
import dev.accessscope.scanner.analyzer.precision.PrecisionRulesPlatform

internal object PrecisionHome {

    /**
     * Verifica se il nodo corrisponde a un tasto del tastierino PIN.
     *
     * Riconosce controlli cliccabili con testo di una singola cifra numerica, o gli ID
     * generici di cancellazione (`delete`/`backspace`).
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return `true` se il nodo è un tasto del PIN pad; `false` altrimenti.
     */
    fun isPinPadKey(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        if (id in AppPrecisionProfiles.pinPadKeyIds(packageName)) return true
        val digit = snap.text?.trim()
        return snap.isInteractiveClickable() &&
            digit?.length == 1 &&
            digit[0].isDigit()
    }

    /**
     * Determina se l'analisi di un tasto PIN va saltata perché non si è in schermata PIN.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param screenTitle Titolo della schermata corrente.
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return `true` se il nodo è un tasto PIN ma la schermata non è PIN e va saltato; `false` altrimenti.
     */
    fun shouldSkipPinPadWhenNotPinScreen(snap: NodeSnapshot, screenTitle: String, packageName: String = ""): Boolean {
        if (screenTitle.contains("PIN", ignoreCase = true)) return false
        return isPinPadKey(snap, packageName)
    }

    /**
     * Determina se il controllo su contenuto dinamico silenzioso (senza live region) va saltato.
     *
     * Le liste scrollabili con RecyclerView e ricerca aggiornano il contenuto durante lo scroll
     * senza annuncio alle tecnologie assistive: comportamento atteso, non da segnalare.
     *
     * @param screenTitle Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @param snapshots Elenco degli snapshot dei nodi nella schermata.
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return `true` se il controllo contenuto dinamico va saltato; `false` altrimenti.
     */
    fun shouldSkipSilentDynamicContent(
        screenTitle: String,
        snapshots: List<NodeSnapshot>,
        packageName: String,
    ): Boolean {
        if (PrecisionStructural.isScrollableListScreen(snapshots)) return true
        if (snapshots.any { PrecisionRulesPlatform.isMediaPlayerSurface(it) }) return true
        if (snapshots.any {
                PrecisionRulesPlatform.isMapSurface(it) &&
                    it.bounds.width() * it.bounds.height() >
                    PrecisionGeometry.estimateViewport(snapshots).let { v -> v.width() * v.height() } * 0.25f
            }
        ) {
            return true
        }
        return false
    }

    /**
     * Verifica se il nodo è un container CTA (Call To Action) per convenzione di naming comune.
     *
     * @param snap Snapshot del nodo da valutare.
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return `true` se il nodo è un container CTA; `false` altrimenti.
     */
    fun isCtaContainer(snap: NodeSnapshot, packageName: String = ""): Boolean {
        val id = PrecisionGeometry.viewIdShort(snap)
        return id in AppPrecisionProfiles.ctaContainerIds(packageName)
    }
}
