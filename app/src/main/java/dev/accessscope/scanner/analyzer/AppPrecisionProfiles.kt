/**
 * Profili opzionali di precisione specifici per applicazione.
 *
 * Le euristiche generiche in [PrecisionRules] valgono per tutte le app; questo modulo
 * aggiunge marker, ID e pattern noti quando il codice sorgente è disponibile
 * (es. app Nexi in benchmark), riducendo i falsi positivi senza alterare la logica generale.
 */
package dev.accessscope.scanner.analyzer

/**
 * Fornisce set di identificatori e prefissi noti per app specifiche, usati da [PrecisionRules]
 * per filtrare rumore e migliorare il recall su pattern ricorrenti.
 */
object AppPrecisionProfiles {

    /** Package noti dell'app Nexi BFF e varianti. */
    private val NEXI_PACKAGES = setOf("it.nexi.bff", "it.nexi")

    /**
     * Verifica se il package appartiene all'ecosistema Nexi.
     *
     * @param packageName Nome completo del package Android.
     * @return `true` se il package corrisponde o contiene "nexi".
     */
    fun isNexi(packageName: String): Boolean =
        NEXI_PACKAGES.any { packageName.startsWith(it) } || packageName.contains("nexi")

    /**
     * Restituisce gli ID view che identificano la schermata Home per l'app data.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di view ID corti usati come marker della home; vuoto per app generiche.
     */
    fun homeScreenMarkers(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_HOME_MARKERS else emptySet()

    /**
     * Restituisce gli ID view considerati template di item di lista ricorrenti.
     *
     * @param packageName Package dell'applicazione.
     * @return Unione di ID generici e specifici Nexi, se applicabile.
     */
    fun listTemplateIds(packageName: String): Set<String> =
        GENERIC_LIST_TEMPLATE_IDS + if (isNexi(packageName)) NEXI_LIST_TEMPLATE_IDS else emptySet()

    /**
     * Restituisce gli ID view riconosciuti come etichette di campo in card o liste.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di ID etichetta campo, inclusi quelli Nexi se rilevante.
     */
    fun fieldLabelIds(packageName: String): Set<String> =
        GENERIC_FIELD_LABEL_IDS + if (isNexi(packageName)) NEXI_FIELD_LABEL_IDS else emptySet()

    /**
     * Restituisce gli ID dei container CTA (call-to-action) noti.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di ID container pulsante/CTA.
     */
    fun ctaContainerIds(packageName: String): Set<String> =
        GENERIC_CTA_IDS + if (isNexi(packageName)) NEXI_CTA_IDS else emptySet()

    /**
     * Restituisce gli ID dei tasti del pad numerico PIN.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di ID tasti PIN specifici Nexi o generici.
     */
    fun pinPadKeyIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_PIN_KEYS else GENERIC_PIN_KEYS

    /** Prefissi comuni per voci di navigazione nel drawer laterale. */
    val drawerNavPrefixes: List<String> = listOf("nav_", "menu_", "drawer_")

    private val GENERIC_LIST_TEMPLATE_IDS = setOf(
        "content", "item", "row", "cell", "label", "title", "name", "root",
    )

    private val NEXI_LIST_TEMPLATE_IDS = setOf(
        "layout_content", "amount_dist", "amount_effetti", "causale", "vop_info",
        "data_creazione", "data_esecuzione", "txt_data_creazione", "txt_data_esecuzione",
        "nome_banca", "state", "check_multiple_selection", "multiple_slection",
        "beneficiario", "numero", "scadenza", "recycler_distinte", "currency_symbol",
    )

    private val GENERIC_FIELD_LABEL_IDS = emptySet<String>()

    private val NEXI_FIELD_LABEL_IDS = setOf(
        "causale", "nome_banca", "data_creazione", "data_esecuzione",
        "txt_data_creazione", "txt_data_esecuzione", "amount_dist", "amount_effetti",
        "beneficiario", "numero", "desc_breve", "scadenza", "iban", "ragione_sociale",
        "currency", "currency_symbol", "data_scadenza", "banca", "iban_account",
        "labelcontacts",
    )

    private val GENERIC_CTA_IDS = setOf(
        "show_more", "see_more", "see_all", "view_all", "read_more", "cta",
    )

    private val NEXI_CTA_IDS = setOf(
        "see_all_insolved", "tv_see_account_movements", "container_custom_cta", "ll_custom",
    )

    private val NEXI_HOME_MARKERS = setOf(
        "card_home", "scrollview_port", "entrate_home", "uscite_home", "ll_ultimi_dati",
    )

    private val NEXI_HOME_CHART_TEXT = setOf(
        "import_positive", "import_negative",
        "currency_incom", "currency_outcom", "currency_symbol",
    )

    private val NEXI_HOME_CHART_CONTAINERS = setOf("entrate_home", "uscite_home")

    private val NEXI_HOME_CAROUSEL_WIDGET_IDS = setOf(
        "tv_tab", "tab_home", "card_effetti", "root_layout_effetti",
        "card_insoluti", "root_layout_insoluti", "insoluti_title",
        "unsolved_count", "unsolved_amount", "txt_insolved", "txt_tot",
    )

    private val NEXI_PIN_KEYS = setOf(
        "cancell", "confirm", "zero", "uno", "due", "tre", "quattro",
        "cinque", "sei", "sette", "otto", "nove",
    )

    private val GENERIC_PIN_KEYS = setOf(
        "delete", "backspace",
    )

    /** ID/classi scroll container riconosciuti per tutte le app. */
    private val GENERIC_MAIN_SCROLL_IDS = setOf(
        "scrollview_port", "scroll", "card_home", "gridview", "recycler", "productslist",
    )

    /**
     * Restituisce gli ID del testo decorativo nei grafici della home.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di ID testo grafico home Nexi, o vuoto per altre app.
     */
    fun homeChartTextIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_HOME_CHART_TEXT else emptySet()

    /**
     * Restituisce gli ID dei container dei grafici in home.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di ID container grafico home Nexi, o vuoto per altre app.
     */
    fun homeChartContainerIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_HOME_CHART_CONTAINERS else emptySet()

    /**
     * Restituisce gli ID dei widget carousel/tab effetti in home.
     *
     * Nodi con contrasto decorativo o styling tab che non richiedono analisi standard.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di ID widget carousel home Nexi, o vuoto per altre app.
     */
    fun homeCarouselWidgetIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_HOME_CAROUSEL_WIDGET_IDS else emptySet()

    /**
     * Restituisce gli ID degli scroll container del contenuto principale.
     *
     * @param packageName Package dell'applicazione.
     * @return Set di ID scroll principale; include "content" solo per app non Nexi.
     */
    fun mainContentScrollIds(packageName: String): Set<String> =
        GENERIC_MAIN_SCROLL_IDS + if (isNexi(packageName)) emptySet() else setOf("content")

    /**
     * ID di testo decorativo in carousel/lista dove il contrasto screenshot è inaffidabile.
     */
    fun carouselDecorativeContrastIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_CAROUSEL_DECORATIVE_CONTRAST_IDS else GENERIC_CAROUSEL_DECORATIVE_CONTRAST_IDS

    /**
     * ID di testo che in layout Nexi usano sempre dimensione grande (≥18sp).
     */
    fun largeTextViewIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_LARGE_TEXT_VIEW_IDS else emptySet()

    /**
     * ID di nodi tab strip (TabLayout custom) da escludere dagli overlap touch.
     */
    fun tabStripViewIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_TAB_STRIP_IDS else GENERIC_TAB_STRIP_IDS

    /**
     * ID CTA primarie con testo brand su sfondo colorato (contrasto affidabile via risorse).
     */
    fun primaryCtaTextIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_PRIMARY_CTA_TEXT_IDS else emptySet()

    private val GENERIC_CAROUSEL_DECORATIVE_CONTRAST_IDS = setOf("currency", "currency_paym", "currency_incom", "currency_symbol")

    private val NEXI_CAROUSEL_DECORATIVE_CONTRAST_IDS = GENERIC_CAROUSEL_DECORATIVE_CONTRAST_IDS + setOf(
        "currency_outcom",
    )

    private val NEXI_LARGE_TEXT_VIEW_IDS = setOf(
        "import_positive", "import_negative", "amount_uscite_effects", "amount_effetti",
        "currency_incom", "currency_outcom", "currency_paym", "new_payment",
    )

    private val GENERIC_TAB_STRIP_IDS = setOf("tv_tab")

    private val NEXI_TAB_STRIP_IDS = GENERIC_TAB_STRIP_IDS + setOf("tab_home", "card_effetti")

    private val NEXI_PRIMARY_CTA_TEXT_IDS = setOf("new_payment", "tv_custom")
}
