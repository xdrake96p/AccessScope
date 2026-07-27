/**
 * Euristiche condivise per selettori Maestro (noise, campi editabili, chrome di sistema).
 */
package dev.accessscope.scanner.recorder

/**
 * Filtra id/tap non utili per replay e export YAML Maestro.
 */
object MaestroSelectorHeuristics {

    private val NOISE_ID_SUBSTRINGS = listOf(
        "progressbar",
        "progress_bar",
        "progressbarcontent",
        "loading",
        "spinner",
        "shimmer",
        "placeholder",
        "skeleton",
        "lottie",
    )

    private val EDITABLE_ID_SUBSTRINGS = listOf(
        "password",
        "username",
        "user_name",
        "email",
        "edit",
        "input",
        "textfield",
        "search",
        "pincode",
        "pin_code",
        "passcode",
        "otp",
    )

    /** Id di layout/container inutili come selettore primario (preferire testo). */
    private val STRUCTURAL_CONTAINER_IDS = listOf(
        "drawer_layout",
        "content",
        "container",
        "root",
        "coordinator",
        "main",
        "frame",
        "wrapper",
        "parent",
        "layout",
        "scrollview",
        "recyclerview",
        "viewpager",
        "nav_host",
        "fragment_container",
    )

    /**
     * Package OS / IME / launcher: tap qui (es. Indietro nav bar) non vanno nel flusso app.
     */
    private val FOREIGN_UI_PACKAGE_PREFIXES = listOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod",
        "com.sec.android.inputmethod",
        "com.touchtype.swiftkey",
        "com.android.inputmethod",
        "android",
    )

    private val SYSTEM_CHROME_ID_EXACT = setOf(
        "back",
        "home",
        "recent_apps",
        "recent",
        "nav_bar",
        "navigation_bar_item_icon",
        "navigation_bar_frame",
    )

    private val SYSTEM_BACK_LABELS = setOf(
        "indietro",
        "back",
        "navigate up",
        "naviga in alto",
    )

    /**
     * Package di chrome sistema / tastiera (non target app).
     *
     * @param packageName Package dell’evento o dell’azione.
     * @return `true` se non appartiene all’app sotto test.
     */
    fun isForeignUiPackage(packageName: String?): Boolean {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank()) return false
        return FOREIGN_UI_PACKAGE_PREFIXES.any { prefix ->
            pkg == prefix || pkg.startsWith("$prefix.")
        }
    }

    /**
     * Tap su chrome di navigazione sistema (nav bar Indietro per chiudere tastiera).
     *
     * @param packageName Package azione.
     * @param viewId Resource id.
     * @param text Testo visibile.
     * @param contentDescription Content description.
     * @return `true` se va escluso dal flusso Maestro.
     */
    fun isSystemChromeTap(
        packageName: String?,
        viewId: String?,
        text: String? = null,
        contentDescription: String? = null,
    ): Boolean {
        if (isForeignUiPackage(packageName)) return true
        if (viewId?.contains("systemui", ignoreCase = true) == true) return true
        val idOwner = viewId?.substringBefore(":id/", missingDelimiterValue = "").orEmpty()
        if (idOwner.isNotBlank() && isForeignUiPackage(idOwner)) return true
        val short = shortViewId(viewId)?.lowercase().orEmpty()
        val label = listOfNotNull(text, contentDescription)
            .map { it.trim().lowercase() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        // Nav-bar Indietro: id back + etichetta sistema, anche se package evento è ambiguo.
        if (short == "back" && label in SYSTEM_BACK_LABELS) return true
        if (short in SYSTEM_CHROME_ID_EXACT && label in SYSTEM_BACK_LABELS) return true
        return false
    }

    /**
     * Id vista da non usare come target tap/wait (loading, progress, ecc.).
     */
    fun isNoiseViewId(viewId: String?): Boolean {
        val short = viewId?.substringAfterLast('/')?.lowercase().orEmpty()
        if (short.isBlank()) return false
        return NOISE_ID_SUBSTRINGS.any { short.contains(it) }
    }

    /**
     * Tap su id tipico di campo testo (focus spurio prima di inputText).
     */
    fun isEditableFieldViewId(viewId: String?): Boolean {
        val short = viewId?.substringAfterLast('/')?.lowercase().orEmpty()
        if (short.isBlank()) return false
        if (EDITABLE_ID_SUBSTRINGS.any { short.contains(it) }) return true
        // "pin" ma non sottostringhe ambigue (es. spinner).
        return short == "pin" || short.startsWith("pin_") || short.endsWith("_pin")
    }

    /**
     * Campo PIN / OTP (non password login): eccezione coalescenza per PIN + conferma.
     *
     * @param viewId Resource id.
     * @return `true` se è un campo pin/otp (due inserimenti restano due step).
     */
    fun isPinLikeField(viewId: String?): Boolean {
        val short = shortViewId(viewId)?.lowercase().orEmpty()
        if (short.isBlank()) return false
        if (short.contains("pincode") || short.contains("pin_code") ||
            short.contains("passcode") || short.contains("otp") ||
            short.contains("securecode") || short.contains("accesscode") ||
            short.contains("codice")
        ) {
            return true
        }
        return short == "pin" || short.startsWith("pin_") || short.endsWith("_pin")
    }

    /**
     * Campo password login (mascherato): un solo inputText fino a cambio campo.
     */
    fun isLoginPasswordField(viewId: String?, isPassword: Boolean): Boolean {
        if (!isPassword) return false
        // Se è anche pin-like (otp password), trattalo come PIN.
        return !isPinLikeField(viewId)
    }

    /**
     * Id strutturali di layout: non usarli come target primario se c’è testo.
     *
     * @param viewId Resource id completo o corto.
     * @return `true` se è shell di layout (drawer, container, …).
     */
    fun isStructuralContainerViewId(viewId: String?): Boolean {
        val short = shortViewId(viewId)?.lowercase().orEmpty()
        if (short.isBlank()) return false
        if (short in setOf(
                "drawer_layout", "content", "container", "root", "coordinator",
                "main", "frame", "wrapper", "parent", "layout",
                "scrollview", "recyclerview", "viewpager",
                "nav_host", "fragment_container",
            )
        ) {
            return true
        }
        return STRUCTURAL_CONTAINER_IDS.any { hint ->
            short.startsWith("${hint}_") ||
                short.endsWith("_$hint") ||
                short.startsWith("container_") ||
                short.contains("drawer")
        }
    }

    fun isNoiseTap(action: RecordedAction.Tap): Boolean =
        isSystemChromeTap(action.packageName, action.viewId, action.text, action.contentDescription) ||
            isNoiseViewId(action.viewId) ||
            (action.viewId == null && action.text.isNullOrBlank() && action.pointPercentX == null)

    /**
     * Normalizza id corto in `package:id/name` quando possibile.
     */
    fun normalizeViewId(viewId: String?, packageName: String): String? {
        if (viewId.isNullOrBlank()) return null
        if (viewId.contains(":id/")) return viewId
        val short = viewId.substringAfterLast('/').takeIf { it.isNotBlank() } ?: viewId
        return "$packageName:id/$short"
    }

    fun shortViewId(viewId: String?): String? =
        viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}
