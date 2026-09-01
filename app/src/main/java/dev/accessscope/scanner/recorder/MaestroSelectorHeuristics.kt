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

    /**
     * Package di dialog di sistema da **catturare** in registrazione Maestro
     * (permessi, installer, consent GMS) come step opzionali — non sono SystemUI/IME.
     */
    private val CAPTURE_DIALOG_PACKAGE_HINTS = listOf(
        "permissioncontroller",
        "packageinstaller",
        "com.android.vpndialogs",
        "com.google.android.gms",
        "com.google.android.permissioncontroller",
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
        // I dialog di permesso/installer non sono "foreign" ai fini della cattura Maestro.
        if (isCaptureDialogPackage(pkg)) return false
        return FOREIGN_UI_PACKAGE_PREFIXES.any { prefix ->
            pkg == prefix || pkg.startsWith("$prefix.")
        }
    }

    /**
     * Dialog di runtime permission / installer / GMS: vanno registrati (tap Allow/Consenti)
     * anche se il package ≠ app target.
     *
     * @param packageName Package dell’evento o dell’azione.
     * @return `true` se è un dialog di sistema da includere nel flusso (optional).
     */
    fun isCaptureDialogPackage(packageName: String?): Boolean {
        val pkg = packageName?.trim()?.lowercase().orEmpty()
        if (pkg.isBlank()) return false
        // SystemUI resta escluso anche se contiene sottostringhe ambigue.
        if (pkg.startsWith("com.android.systemui")) return false
        return CAPTURE_DIALOG_PACKAGE_HINTS.any { hint ->
            pkg == hint || pkg.contains(hint)
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
     * Etichette tipiche di dismiss popup (in-app e runtime permission).
     */
    val POPUP_DISMISS_LABELS = listOf(
        "non ora",
        "not now",
        "allow",
        "consenti",
        "deny",
        "nega",
        "rifiuta",
        "chiudi",
        "close",
        "annulla",
        "cancel",
        "skip",
        "no thanks",
        "accetta",
        "later",
        "più tardi",
        "solo questa volta",
        "while using",
        "ho capito",
        "got it",
        "ok, ho capito",
        "non adesso",
    )

    /**
     * `true` se il testo è un dismiss tipico di popup.
     */
    fun isPopupDismissLabel(text: String?): Boolean {
        val value = text?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return false
        return POPUP_DISMISS_LABELS.any { value == it || value.contains(it) }
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
        // Non includere edit1…edit6: sono slot OTP/PIN UI, ma il valore non è sempre ${PIN}
        // (es. codice SMS) e non va mascherato in export.
        if (short.contains("pincode") || short.contains("pin_code") ||
            short.contains("passcode") || short.contains("otp") ||
            short.contains("securecode") || short.contains("accesscode") ||
            short.contains("codice")
        ) {
            return true
        }
        return short == "pin" || short.startsWith("pin_") || short.endsWith("_pin")
    }

    /** Id tasti pad numerico IT (Nexi/MPS e simili). */
    private val PIN_PAD_KEY_IDS = setOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove",
        "key_0", "key_1", "key_2", "key_3", "key_4", "key_5", "key_6", "key_7", "key_8", "key_9",
        "btn_0", "btn_1", "btn_2", "btn_3", "btn_4", "btn_5", "btn_6", "btn_7", "btn_8", "btn_9",
        "num_0", "num_1", "num_2", "num_3", "num_4", "num_5", "num_6", "num_7", "num_8", "num_9",
        "digit_0", "digit_1", "digit_2", "digit_3", "digit_4",
        "digit_5", "digit_6", "digit_7", "digit_8", "digit_9",
    )

    /** Regex slot display PIN/OTP riempiti dal pad (non da IME). */
    private val PIN_PAD_DIGIT_SLOT_REGEX = Regex(
        "^(edit|pin|otp|digit|code|box|cell|slot)_?\\d{1,2}$|" +
            "^(pin|otp|digit|code)_?(box|digit|slot|cell)_?\\d{1,2}$",
    )

    /**
     * Slot display del PIN pad (`edit1`…`edit6`, `otp_1`, …): si riempiono coi tap
     * sul tastierino custom — **non** vanno esportati come `inputText`.
     *
     * @param viewId Resource id completo o corto.
     * @return `true` se è uno slot display, non un campo IME.
     */
    fun isPinPadDigitSlot(viewId: String?): Boolean {
        val short = shortViewId(viewId)?.lowercase().orEmpty()
        if (short.isBlank()) return false
        return PIN_PAD_DIGIT_SLOT_REGEX.matches(short)
    }

    /**
     * Tasto del pad numerico custom (es. `id/uno` + testo `1`).
     *
     * @param viewId Resource id.
     * @param text Etichetta/testo del nodo.
     * @return `true` se è un tasto 0–9 del pad.
     */
    fun isPinPadKey(viewId: String?, text: String? = null): Boolean {
        val short = shortViewId(viewId)?.lowercase().orEmpty()
        if (short.isNotBlank() && short in PIN_PAD_KEY_IDS) return true
        val digit = text?.trim().orEmpty()
        if (digit.length == 1 && digit[0] in '0'..'9') {
            // Solo se l’id suggerisce un tasto (evita tap spurî su importi “10”).
            if (short.isBlank()) return false
            return short.contains("key") || short.contains("btn") ||
                short.contains("num") || short.contains("digit") ||
                short.contains("pad") || short in PIN_PAD_KEY_IDS
        }
        return false
    }

    /**
     * Tap singolo digito 0–9 tipico di pad PIN (anche solo testo, es. payment PIN Nexi).
     *
     * @param text Testo del tap.
     * @param viewId Resource id opzionale.
     * @return `true` se è un digito pad (non un importo/label lunga).
     */
    fun isPinPadDigitTap(text: String?, viewId: String? = null): Boolean {
        if (isPinPadKey(viewId, text)) return true
        val digit = text?.trim().orEmpty()
        return digit.length == 1 && digit[0] in '0'..'9'
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

    /**
     * Id riutilizzati su più righe (accordion ExpandableList): non usare ACTION_CLICK sull’id,
     * preferire testo + gesture sui bounds della label.
     */
    fun isAmbiguousSharedViewId(viewId: String?): Boolean {
        val short = shortViewId(viewId)?.lowercase().orEmpty()
        if (short.isBlank()) return false
        if (isStructuralContainerViewId(short)) return true
        return short in setOf(
            "header", "item", "row", "title", "cell", "group", "list_item",
            "expandable", "accordion", "section",
        ) || short.endsWith("_header") || short.startsWith("header_") ||
            short.endsWith("_item") || short.endsWith("_row")
    }

    private val VOLATILE_ID_REGEX = Regex(".*(_\\d{4,}|[0-9a-f]{8}-[0-9a-f]{4}|[0-9a-f]{16,}|compose_[a-z0-9]+).*")

    /**
     * Id con hash/UUID/suffissi numerici lunghi tipici Compose — poco stabili tra build.
     */
    fun isVolatileViewId(viewId: String?): Boolean {
        val short = shortViewId(viewId)?.lowercase().orEmpty()
        if (short.isBlank()) return false
        return VOLATILE_ID_REGEX.matches(short)
    }

    /**
     * Etichetta tipica di header di sezione/accordion (non specifica di un’app).
     * Usata per dedupe scroll e preferenza testo su id condivisi.
     */
    fun isSectionHeaderLabel(text: String?): Boolean {
        val t = text?.lowercase()?.trim().orEmpty()
        if (t.length < 4) return false
        if (t.startsWith("le mie ") || t.startsWith("la mia ") || t.startsWith("il mio ") ||
            t.startsWith("my ") || t.startsWith("your ")
        ) {
            return true
        }
        return t.contains("garanzie") ||
            t.contains("inventario") ||
            t.contains("dettagli") ||
            t.contains("documenti") ||
            (t.contains("polizza") && t.length < 40) ||
            t.contains("section") ||
            t.endsWith("…") ||
            t.endsWith("...")
    }

    /**
     * `true` se il tap non ha alcun selettore utilizzabile (id, testo, contentDescription o punto).
     *
     * Il `contentDescription` va controllato esplicitamente: righe di liste composite (es. sheet
     * "Rubrica"/"Seleziona IBAN" — nome + IBAN su due righe) spesso non hanno `text` né `viewId`
     * proprio, solo un `contentDescription` sul contenitore (pattern comune per TalkBack). Senza
     * questo controllo il tap veniva scartato come rumore pur avendo un selettore valido, e la
     * selezione dalla lista spariva dal flusso esportato.
     */
    fun isNoiseTap(action: RecordedAction.Tap): Boolean =
        isSystemChromeTap(action.packageName, action.viewId, action.text, action.contentDescription) ||
            isNoiseViewId(action.viewId) ||
            (
                action.viewId == null &&
                    action.text.isNullOrBlank() &&
                    action.contentDescription.isNullOrBlank() &&
                    action.pointPercentX == null
                )

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
