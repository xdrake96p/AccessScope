/**
 * Set di identificatori generici riusati dalle euristiche di precisione.
 *
 * Storicamente questo modulo ospitava anche marker specifici per le app usate in fase di
 * validazione iniziale (Nexi, AXA): sono stati rimossi perché il motore deve restare generico
 * per qualunque app sotto scansione, non tarato su un singolo target noto.
 */
package dev.accessscope.scanner.analyzer

/**
 * Fornisce set di identificatori e prefissi noti, riusati da [PrecisionRules] per filtrare
 * rumore strutturale ricorrente in base a convenzioni di naming comuni (non a un'app specifica).
 */
object AppPrecisionProfiles {

    /**
     * Restituisce gli ID view considerati template di item di lista ricorrenti.
     *
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return Set di ID generici usati come template di lista.
     */
    fun listTemplateIds(packageName: String = ""): Set<String> = GENERIC_LIST_TEMPLATE_IDS

    /**
     * Restituisce gli ID container CTA (call-to-action) noti per convenzione.
     *
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return Set di ID container pulsante/CTA.
     */
    fun ctaContainerIds(packageName: String = ""): Set<String> = GENERIC_CTA_IDS

    /**
     * Restituisce gli ID dei tasti di cancellazione del tastierino numerico.
     *
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return Set di ID generici; il riconoscimento delle cifre usa comunque il testo del nodo.
     */
    fun pinPadKeyIds(packageName: String = ""): Set<String> = GENERIC_PIN_KEYS

    /** Prefissi comuni per voci di navigazione nel drawer laterale. */
    val drawerNavPrefixes: List<String> = listOf("nav_", "menu_", "drawer_")

    private val GENERIC_LIST_TEMPLATE_IDS = setOf(
        "content", "item", "row", "cell", "label", "title", "name", "root",
    )

    private val GENERIC_CTA_IDS = setOf(
        "show_more", "see_more", "see_all", "view_all", "read_more", "cta",
    )

    private val GENERIC_PIN_KEYS = setOf(
        "delete", "backspace",
    )

    /** ID/classi scroll container riconosciuti per convenzione di naming comune. */
    private val GENERIC_MAIN_SCROLL_IDS = setOf(
        "scrollview_port", "scroll", "gridview", "recycler",
    )

    /**
     * Restituisce gli ID degli scroll container del contenuto principale.
     *
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     * @return Set di ID scroll principale per convenzione comune, incluso `content`.
     */
    fun mainContentScrollIds(packageName: String = ""): Set<String> =
        GENERIC_MAIN_SCROLL_IDS + setOf("content")

    /**
     * ID di testo decorativo in carousel/lista dove il contrasto screenshot è inaffidabile.
     *
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     */
    fun carouselDecorativeContrastIds(packageName: String = ""): Set<String> =
        GENERIC_CAROUSEL_DECORATIVE_CONTRAST_IDS

    /**
     * ID di nodi tab strip (TabLayout custom) da escludere dagli overlap touch.
     *
     * @param packageName Non usato: mantenuto per compatibilità di firma con i chiamanti esistenti.
     */
    fun tabStripViewIds(packageName: String = ""): Set<String> = GENERIC_TAB_STRIP_IDS

    private val GENERIC_CAROUSEL_DECORATIVE_CONTRAST_IDS = setOf("currency", "currency_symbol")

    private val GENERIC_TAB_STRIP_IDS = setOf("tv_tab")
}
