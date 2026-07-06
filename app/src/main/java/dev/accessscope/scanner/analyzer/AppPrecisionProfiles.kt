package dev.accessscope.scanner.analyzer

/**
 * Profili opzionali per regole di precisione specifiche per app.
 * Le euristiche in [PrecisionRules] restano generiche; qui si aggiungono solo marker noti.
 */
object AppPrecisionProfiles {

    private val NEXI_PACKAGES = setOf("it.nexi.bff", "it.nexi")

    fun isNexi(packageName: String): Boolean =
        NEXI_PACKAGES.any { packageName.startsWith(it) } || packageName.contains("nexi")

    fun homeScreenMarkers(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_HOME_MARKERS else emptySet()

    fun listTemplateIds(packageName: String): Set<String> =
        GENERIC_LIST_TEMPLATE_IDS + if (isNexi(packageName)) NEXI_LIST_TEMPLATE_IDS else emptySet()

    fun fieldLabelIds(packageName: String): Set<String> =
        GENERIC_FIELD_LABEL_IDS + if (isNexi(packageName)) NEXI_FIELD_LABEL_IDS else emptySet()

    fun ctaContainerIds(packageName: String): Set<String> =
        GENERIC_CTA_IDS + if (isNexi(packageName)) NEXI_CTA_IDS else emptySet()

    fun pinPadKeyIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_PIN_KEYS else GENERIC_PIN_KEYS

    val drawerNavPrefixes: List<String> = listOf("nav_", "menu_", "drawer_")

    private val GENERIC_LIST_TEMPLATE_IDS = setOf("content", "item", "row", "cell")

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
        "labelcontacts", "nome_filiale",
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
        "last_30", "last_30_negative", "import_positive", "import_negative",
        "currency_incom", "currency_outcom", "currency_symbol",
    )

    private val NEXI_HOME_CHART_CONTAINERS = setOf("entrate_home", "uscite_home")

    private val NEXI_PIN_KEYS = setOf(
        "cancell", "confirm", "zero", "uno", "due", "tre", "quattro",
        "cinque", "sei", "sette", "otto", "nove",
    )

    private val GENERIC_PIN_KEYS = setOf(
        "delete", "backspace", "key", "digit",
    )

    fun homeChartTextIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_HOME_CHART_TEXT else emptySet()

    fun homeChartContainerIds(packageName: String): Set<String> =
        if (isNexi(packageName)) NEXI_HOME_CHART_CONTAINERS else emptySet()

    fun mainContentScrollIds(packageName: String): Set<String> =
        setOf("scrollview_port", "scroll", "card_home") + if (isNexi(packageName)) emptySet() else setOf("content")
}
