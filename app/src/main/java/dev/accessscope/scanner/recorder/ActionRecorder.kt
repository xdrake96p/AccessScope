/**
 * Conversione eventi AccessibilityEvent in [RecordedAction] (Maestro Beta).
 */
package dev.accessscope.scanner.recorder

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.recorder.capture.AlertOverlayResolver
import dev.accessscope.scanner.recorder.capture.FieldInputTargetResolver
import dev.accessscope.scanner.recorder.capture.InferredSelectionDetector
import dev.accessscope.scanner.recorder.capture.PickerSession
import dev.accessscope.scanner.recorder.capture.TapIdentityResolver
import dev.accessscope.scanner.recorder.model.SelectorCandidate
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.util.AppFileLogger

/**
 * Fornisce root accessibility per risolvere nodi quando `event.source` è null.
 *
 * In registrazione Maestro può esporre più finestre (dialog/sheet oltre alla root attiva).
 */
fun interface AccessibilityRootProvider {
    /** @return Root corrente (finestra attiva) o `null`. */
    fun root(): AccessibilityNodeInfo?

    /**
     * Tutte le root utili (dialog, overlay app, activity), escluse IME.
     * Default: sola [root].
     */
    fun roots(): List<AccessibilityNodeInfo> = listOfNotNull(root())
}

/**
 * Costruisce azioni Maestro a partire dagli eventi di accessibilità.
 *
 * Preferisce `resource-id`, poi testo/contentDescription, infine coordinate percentuali.
 * Se `event.source` manca, cerca il testo evento su **tutte** le root (dialog/sheet).
 * Non usa FOCUS_ACCESSIBILITY sui click (rompe i tap su popup sopra EditText).
 * Popup in-app: AssertVisible sul titolo + tap dismiss anche se source è l’editabile sotto.
 * Coalescia input testo; scroll solo con delta reale; Back da [onBackPressed]; password → `****`.
 */
class ActionRecorder {

    private var lastTapKey: String? = null
    private var lastTapAtMs: Long = 0L
    private var pendingText: PendingText? = null
    private var lastScrollAtMs: Long = 0L
    private var lastBackAtMs: Long = 0L
    private var lastPopupAssertKey: String? = null
    private var lastPopupAssertAtMs: Long = 0L
    /** Dialog aperto senza tap dismiss a11y (es. KYC Compose/Dialog custom). */
    private var pendingDialog: PendingDialog? = null
    /** Stato sheet picker rubrica/IBAN durante REC. */
    private val pickerSession = PickerSession()
    /** Ultimi valori campo noti: baseline per [captureScreenStateAndInfer]. */
    private var lastFieldValues: Map<String, String> = emptyMap()

    /**
     * Testi visti di recente (testo → timestamp), con scadenza: permette di riconoscere la voce
     * scelta da una lista **dopo** che la lista è già sparita dallo schermo.
     */
    private val recentVisibleTexts = LinkedHashMap<String, Long>()

    /** Campi valorizzati digitando: esclusi dall'inferenza (sono già `InputText`). */
    private val recentlyTypedFieldIds = LinkedHashSet<String>()

    private var lastInferredLabel: String? = null
    private var lastInferredAtMs: Long = 0L

    private data class PendingText(
        val packageName: String,
        val viewId: String?,
        val text: String,
        val isPassword: Boolean,
        val timestampMs: Long,
    )

    private data class PendingDialog(
        val packageName: String,
        val title: String?,
        val dismissLabels: List<String>,
        val openedAtMs: Long,
    )

    /**
     * Interpreta un evento e restituisce zero o più azioni da appendere al flusso.
     *
     * @param event Evento di sistema.
     * @param screenWidthPx Larghezza display per calcolo point %.
     * @param screenHeightPx Altezza display per calcolo point %.
     * @param rootProvider Root finestra attiva (fallback se source null).
     */
    fun onEvent(
        event: AccessibilityEvent,
        screenWidthPx: Int,
        screenHeightPx: Int,
        rootProvider: AccessibilityRootProvider = AccessibilityRootProvider { null },
    ): List<RecordedAction> {
        val packageName = event.packageName?.toString() ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = mutableListOf<RecordedAction>()

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                flushPendingText()?.let(out::add)
                val tap = buildTap(
                    event = event,
                    packageName = packageName,
                    now = now,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    longPress = false,
                    rootProvider = rootProvider,
                )
                if (tap == null) {
                    logDiscard(event.eventType, packageName, "tap_unresolved")
                } else {
                    val key = tapKey(tap)
                    val pinPadDigit = tap is RecordedAction.Tap &&
                        (
                            MaestroSelectorHeuristics.isPinPadKey(tap.viewId, tap.text) ||
                                MaestroSelectorHeuristics.isPinPadDigitTap(tap.text, tap.viewId)
                            )
                    if (!pinPadDigit && key == lastTapKey && now - lastTapAtMs < TAP_DEBOUNCE_MS) {
                        return out
                    }
                    lastTapKey = key
                    lastTapAtMs = now
                    clearPendingDialogIfDismiss(tap)
                    out += tap
                }
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                flushPendingText()?.let(out::add)
                val press = buildTap(
                    event = event,
                    packageName = packageName,
                    now = now,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    longPress = true,
                    rootProvider = rootProvider,
                )
                if (press == null) {
                    logDiscard(event.eventType, packageName, "longpress_unresolved")
                } else {
                    out += press
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                out += updatePendingText(event, packageName, now, rootProvider)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val resolved = resolveNode(event, rootProvider)
                val editable = resolved.node?.isEditable == true
                val focusViewId = resolved.node?.viewIdResourceName
                resolved.recycleIfNeeded()
                if (editable) {
                    // PIN + conferma: nuovo focus su editabile → flush campo precedente.
                    if (pendingText != null) {
                        flushPendingText()?.let(out::add)
                    }
                    out += updatePendingText(event, packageName, now, rootProvider)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                flushPendingText()?.let(out::add)
                // Dialog chiuso senza TYPE_VIEW_CLICKED (evidenza KYC AXA): sintetizza dismiss.
                synthesizeDismissIfDialogClosed(event, packageName, now)?.let(out::add)
                captureScreenStateAndInfer(rootProvider, packageName, now)?.let(out::add)
                out += capturePopupOpen(event, packageName, now, rootProvider)
                if (out.any { it is RecordedAction.AssertVisible }) {
                    lastPopupAssertAtMs = now
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                captureScreenStateAndInfer(rootProvider, packageName, now)?.let(out::add)
                // Solo se l’evento porta già un titolo “dialog-like” (niente scan root: troppo rumoroso).
                capturePopupAssert(
                    event = event,
                    packageName = packageName,
                    now = now,
                    rootProvider = rootProvider,
                    allowRootScan = false,
                )?.let { assert ->
                    lastPopupAssertAtMs = now
                    out += assert
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Dopo Back / comparsa popup / tap, scroll di layout non devono diventare step spurî.
                if (now - lastBackAtMs < SCROLL_SUPPRESS_AFTER_BACK_MS) {
                    logDiscard(event.eventType, packageName, "scroll_after_back")
                    return out
                }
                if (now - lastPopupAssertAtMs < SCROLL_SUPPRESS_AFTER_POPUP_MS) {
                    logDiscard(event.eventType, packageName, "scroll_after_popup")
                    return out
                }
                if (now - lastTapAtMs < SCROLL_SUPPRESS_AFTER_TAP_MS) {
                    logDiscard(event.eventType, packageName, "scroll_after_tap")
                    return out
                }
                val direction = scrollDirectionOrNull(event) ?: run {
                    logDiscard(event.eventType, packageName, "scroll_no_delta")
                    return out
                }
                if (now - lastScrollAtMs < SCROLL_DEBOUNCE_MS) return out
                lastScrollAtMs = now
                flushPendingText()?.let(out::add)
                out += RecordedAction.Scroll(
                    packageName = packageName,
                    direction = direction,
                    timestampMs = now,
                )
            }
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val pickerOpen = isPickerRecordingOpen(rootProvider)
                val src = runCatching { event.source }.getOrNull()
                val actionable = src != null && (src.isClickable || src.isCheckable)
                src?.recycle()
                if (!actionable && !pickerOpen) {
                    logDiscard(event.eventType, packageName, "selected_not_actionable")
                    return out
                }
                flushPendingText()?.let(out::add)
                buildTap(
                    event = event,
                    packageName = packageName,
                    now = now,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    longPress = false,
                    rootProvider = rootProvider,
                )?.let { tap ->
                    val key = tapKey(tap)
                    if (key == lastTapKey && now - lastTapAtMs < TAP_DEBOUNCE_MS * 2) return out
                    lastTapKey = key
                    lastTapAtMs = now
                    out += tap
                }
            }
        }
        return out
    }

    /** Scarica eventuale testo in attesa (es. a fine registrazione). */
    fun flush(): List<RecordedAction> = listOfNotNull(flushPendingText())

    /** Notifica pressione Back (hardware/gesture) dal servizio. */
    fun onBackPressed(packageName: String, now: Long = System.currentTimeMillis()): List<RecordedAction> {
        lastBackAtMs = now
        flushPendingText()
        return listOf(
            RecordedAction.Back(packageName = packageName, timestampMs = now),
        )
    }

    fun reset() {
        lastTapKey = null
        lastTapAtMs = 0L
        pendingText = null
        lastScrollAtMs = 0L
        lastBackAtMs = 0L
        lastPopupAssertKey = null
        lastPopupAssertAtMs = 0L
        pendingDialog = null
        pickerSession.reset()
        lastFieldValues = emptyMap()
        recentVisibleTexts.clear()
        recentlyTypedFieldIds.clear()
        lastInferredLabel = null
        lastInferredAtMs = 0L
    }

    /** Picker aperto: stato sessione, overlay strutturale o titolo sheet visibile. */
    private fun isPickerRecordingOpen(rootProvider: AccessibilityRootProvider): Boolean {
        if (pickerSession.isOpen()) return true
        if (FieldInputTargetResolver.isPickerOverlayOpen(rootProvider)) {
            syncPickerSessionFromOverlay(rootProvider)
            return true
        }
        return false
    }

    /** Allinea sessione REC se l'overlay picker è visibile ma manca il titolo in stato. */
    private fun syncPickerSessionFromOverlay(rootProvider: AccessibilityRootProvider) {
        if (pickerSession.title() != null) return
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        try {
            FieldInputTargetResolver.findPickerTitleInRoots(roots)?.let { title ->
                pickerSession.onPickerTitle(title, System.currentTimeMillis())
            }
        } finally {
            roots.forEach { it.recycle() }
        }
    }

    /**
     * Aggiorna lo stato schermo e, se ne trova la prova, ricava la selezione che gli eventi non
     * hanno riportato.
     *
     * Sostituisce l'approccio precedente basato su euristiche di testo ("questo titolo sembra uno
     * sheet rubrica?"): quelle assunzioni si rompono su qualunque app che non le rispetti — bug
     * reale su it.nexi.bff/Banca MPS, dove l'intestazione di sezione «Beneficiario» del form veniva
     * scambiata per il titolo dello sheet di selezione, convincendo il recorder che il picker fosse
     * perennemente aperto. Qui non si indovina nulla: si confronta lo stato prima/dopo e si
     * pretende una prova strutturale (vedi [InferredSelectionDetector]).
     *
     * @return Tap sintetico sulla voce scelta, o `null` se non c'è prova di una selezione.
     */
    private fun captureScreenStateAndInfer(
        rootProvider: AccessibilityRootProvider,
        packageName: String,
        now: Long,
    ): RecordedAction.Tap? {
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        val snapshot = try {
            InferredSelectionDetector.snapshot(roots)
        } finally {
            roots.forEach { it.recycle() }
        }

        // Campi digitati dall'utente: già coperti da InputText, non vanno reinterpretati come scelte.
        val typedFieldIds = buildSet {
            pendingText?.viewId?.let(::add)
            addAll(recentlyTypedFieldIds)
        }
        val inferred = InferredSelectionDetector.inferSelection(
            before = lastFieldValues,
            after = snapshot.fieldValues,
            recentVisibleTexts = recentVisibleTexts.keys,
            ignoredFieldIds = typedFieldIds,
        )

        lastFieldValues = snapshot.fieldValues
        rememberVisibleTexts(snapshot.visibleTexts, now)

        if (inferred == null) return null
        val label = inferred.matchedVisibleText.take(80)
        if (label == lastInferredLabel && now - lastInferredAtMs < INFERRED_SELECTION_DEBOUNCE_MS) {
            return null
        }
        lastInferredLabel = label
        lastInferredAtMs = now
        pickerSession.close()
        AppFileLogger.info(
            "ActionRecorder",
            "inferred_selection field=${inferred.fieldViewId} label=$label",
        )
        return RecordedAction.Tap(
            packageName = packageName,
            text = label,
            timestampMs = now,
            selectorChain = listOf(SelectorCandidate(text = label)),
        )
    }

    /** Memoria a scadenza dei testi visti: serve a riconoscere la voce scelta dopo la chiusura della lista. */
    private fun rememberVisibleTexts(texts: Set<String>, now: Long) {
        texts.forEach { recentVisibleTexts[it] = now }
        if (recentVisibleTexts.size > RECENT_TEXTS_MAX) {
            val cutoff = now - RECENT_TEXTS_TTL_MS
            recentVisibleTexts.entries.removeAll { it.value < cutoff }
            while (recentVisibleTexts.size > RECENT_TEXTS_MAX) {
                val oldest = recentVisibleTexts.entries.minByOrNull { it.value } ?: break
                recentVisibleTexts.remove(oldest.key)
            }
        }
    }

    /** Segna un campo come digitato dall'utente (evita di reinterpretarlo come selezione da lista). */
    private fun markFieldAsTyped(viewId: String?) {
        if (viewId.isNullOrBlank()) return
        recentlyTypedFieldIds += viewId
        if (recentlyTypedFieldIds.size > TYPED_FIELDS_MAX) {
            recentlyTypedFieldIds.remove(recentlyTypedFieldIds.first())
        }
    }


    /**
     * Sceglie il testo davvero digitato, escludendo l'hint quando trapela come testo del nodo.
     *
     * Un campo `EditText` vuoto riporta il proprio hint come testo per l'accessibilità
     * (`TextView.getTextForAccessibility()` di AOSP ricade su `mHint` quando `mText` è vuoto), e
     * il focus su un campo ancora vuoto passa da qui (branch `TYPE_VIEW_FOCUSED`). Senza questo
     * filtro il placeholder finisce dentro un `inputText` esportato — bug reale trovato su
     * it.nexi.bff/MPS: `- inputText: "IBAN (obbligatorio)"` su un campo mai toccato.
     *
     * Limite noto e accettato: un testo digitato che coincide *alla lettera* con l'hint viene
     * anch'esso trattato come vuoto — non c'è modo affidabile di distinguere i due casi con i
     * soli campi `text`/`hintText`, e digitare il placeholder alla lettera è comunque
     * praticamente impossibile in pratica.
     *
     * @param eventText Testo portato dall'[AccessibilityEvent] stesso (se presente).
     * @param nodeText Testo del nodo sorgente.
     * @param hintText Hint del nodo sorgente.
     * @return Testo digitato, o `null` se il campo è vuoto (testo assente o coincidente con l'hint).
     */
    internal fun resolveTypedText(eventText: String?, nodeText: String?, hintText: String?): String? {
        val hint = hintText?.trim()?.takeIf { it.isNotBlank() }
        val text = eventText?.takeIf { it.isNotBlank() } ?: nodeText?.takeIf { it.isNotBlank() }
        return text?.takeIf { hint == null || it.trim() != hint }
    }

    /**
     * Aggiorna il buffer testo; se cambia campo, flush del precedente (PIN + conferma).
     *
     * @return Azioni da appendere (0 o 1 InputText flushato).
     */
    private fun updatePendingText(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
        rootProvider: AccessibilityRootProvider,
    ): List<RecordedAction> {
        val resolved = resolveNode(event, rootProvider)
        val node = resolved.node
        val isPassword = node?.isPassword == true
        val text = resolveTypedText(
            eventText = event.text?.joinToString(""),
            nodeText = node?.text?.toString(),
            hintText = node?.hintText?.toString(),
        )
        val viewId = node?.viewIdResourceName?.takeIf { it.isNotBlank() }
        val isPickerSelection = node != null && !text.isNullOrBlank() && !isPassword &&
            (
                FieldInputTargetResolver.isPickerBackedField(node) ||
                    FieldInputTargetResolver.isPickerBackedViewId(viewId)
                ) &&
            FieldInputTargetResolver.isPickerListLabel(text, null)

        // Selezione da picker: il valore compare come TEXT_CHANGED sul campo (nessun click lista a11y).
        if (isPickerSelection && node != null) {
            val out = mutableListOf<RecordedAction>()
            if (!pickerSession.hasRecentIconTap(now)) {
                FieldInputTargetResolver.findPickerIconNearEditable(node)?.let { icon ->
                    val iconId = icon.viewIdResourceName
                    out += buildPickerIconTap(
                        packageName = packageName,
                        icon = icon,
                        now = now,
                        screenWidthPx = 0,
                        screenHeightPx = 0,
                        event = event,
                    )
                    pickerSession.onIconTap(now)
                    icon.recycle()
                    AppFileLogger.info(
                        "ActionRecorder",
                        "picker_selection_icon_synth id=$iconId value=$text",
                    )
                }
            }
            pendingText = null
            out += tapFromEventLabel(packageName, text!!.take(80), now, chainFromLabel = true)
            pickerSession.close()
            AppFileLogger.info("ActionRecorder", "picker_selection_rec value=$text viewId=$viewId")
            resolved.recycleIfNeeded()
            return out
        }

        resolved.recycleIfNeeded()
        // edit1…edit6 sono EditText reali (OTP SMS / PIN): vanno registrati come inputText.
        // I tap sul pad (uno/due) restano a parte e in optimize diventano Optional se ridondanti.
        val pinLike = MaestroSelectorHeuristics.isPinLikeField(viewId)
        val loginPassword = MaestroSelectorHeuristics.isLoginPasswordField(viewId, isPassword)
        // Campo svuotato: flush pending (PIN conferma / nuovo ciclo).
        if (text.isNullOrBlank()) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                if (pendingText != null) {
                    return listOfNotNull(flushPendingText())
                }
                logDiscard(event.eventType, packageName, "text_blank")
            }
            return emptyList()
        }

        val flushed = mutableListOf<RecordedAction>()
        val pending = pendingText
        val storedText = if (isPassword) "****" else text.take(200)
        val reason = when {
            pending != null && !samePendingField(pending.viewId, viewId) -> "field_change"
            // Password login: NON trattare ****==**** come re-entry (evita N duplicati).
            loginPassword -> null
            // Stesso campo + stesso testo completo → nuovo step (PIN + conferma), senza attendere gap.
            pending != null &&
                !loginPassword &&
                samePendingField(pending.viewId, viewId) &&
                pending.text == storedText -> "duplicate_complete"
            // PIN: accorciato / cifra singola dopo pausa → nuovo inserimento.
            pending != null &&
                pinLike &&
                samePendingField(pending.viewId, viewId) &&
                (storedText.length < pending.text.length || storedText.length <= 1) &&
                now - pending.timestampMs >= 800L -> "pin_reentry"
            else -> null
        }
        if (reason != null) {
            flushPendingText()?.let(flushed::add)
        }

        // Password: aggiorna pending senza cambiare timestamp a ogni keystroke (evita gap spurî).
        val ts = if (loginPassword && pending != null && samePendingField(pending.viewId, viewId)) {
            pending.timestampMs
        } else {
            now
        }
        pendingText = PendingText(
            packageName = packageName,
            viewId = viewId,
            text = storedText,
            isPassword = isPassword,
            timestampMs = ts,
        )
        // Valore digitato: già coperto da InputText, non va reinterpretato come scelta da lista.
        markFieldAsTyped(viewId)
        return flushed
    }

    private fun samePendingField(a: String?, b: String?): Boolean {
        val idA = MaestroSelectorHeuristics.shortViewId(a)
        val idB = MaestroSelectorHeuristics.shortViewId(b)
        return when {
            !idA.isNullOrBlank() && !idB.isNullOrBlank() -> idA == idB
            idA.isNullOrBlank() && idB.isNullOrBlank() -> true
            else -> false
        }
    }

    private fun flushPendingText(): RecordedAction.InputText? {
        val pending = pendingText ?: return null
        pendingText = null
        return RecordedAction.InputText(
            packageName = pending.packageName,
            text = pending.text,
            viewId = pending.viewId,
            isPassword = pending.isPassword,
            timestampMs = pending.timestampMs,
        )
    }

    private fun buildTap(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
        screenWidthPx: Int,
        screenHeightPx: Int,
        longPress: Boolean,
        rootProvider: AccessibilityRootProvider,
    ): RecordedAction? {
        val eventLabel = eventLabel(event)
        val resolved = resolveNode(event, rootProvider, preferFocusFallback = false)
        val node = resolved.node

        // Alert in-app (es. Nexi alert_pop / id/dismiss): il click a11y spesso punta
        // all’EditText sotto → editable_skip. Risolvi il bottone reale nelle root.
        val alertDismiss = if (!longPress) AlertOverlayResolver.findDismiss(rootProvider) else null
        if (alertDismiss != null) {
            val forceDismiss = node?.isEditable == true ||
                eventLabel.isNullOrBlank() ||
                MaestroSelectorHeuristics.isPopupDismissLabel(eventLabel) ||
                eventLabel.equals(alertDismiss.text, ignoreCase = true) ||
                (alertDismiss.text != null &&
                    eventLabel?.contains(alertDismiss.text!!, ignoreCase = true) == true) ||
                (alertDismiss.viewId != null &&
                    node?.viewIdResourceName?.endsWith("/dismiss") == true)
            if (forceDismiss) {
                resolved.recycleIfNeeded()
                AppFileLogger.info(
                    "ActionRecorder",
                    "tap_alert_dismiss id=${alertDismiss.viewId} text=${alertDismiss.text}",
                )
                return AlertOverlayResolver.toOptionalTap(packageName, alertDismiss, now)
            }
        }

        // Popup Compose: click su "Non ora" con source=editabile sotto il dialog.
        val dismissFromEvent = eventLabel != null && MaestroSelectorHeuristics.isPopupDismissLabel(eventLabel)
        val pickerOpen = isPickerRecordingOpen(rootProvider)
        val eventCd = event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }

        // Tap su voce lista picker: source spesso editabile (campo «Ricerca in rubrica» sotto).
        if (node?.isEditable == true && !longPress && !dismissFromEvent && pickerOpen) {
            if (FieldInputTargetResolver.isPickerListLabel(eventLabel, eventCd)) {
                resolved.recycleIfNeeded()
                AppFileLogger.info(
                    "ActionRecorder",
                    "picker_list_item_rec text=${eventLabel ?: eventCd}",
                )
                pickerSession.close()
                return tapFromEventLabel(
                    packageName,
                    (eventLabel ?: eventCd)!!.take(80),
                    now,
                    chainFromLabel = true,
                )
            }
        }

        // Campo form con icona picker: tap sul label/hint → registra tap icona (apre sheet).
        if (node != null && node.isEditable && !longPress && !dismissFromEvent) {
            val fieldLabel = FieldInputTargetResolver.fieldLabelForEditable(node)
                ?: textOrLabel(eventLabel, node)
            if (!fieldLabel.isNullOrBlank() &&
                FieldInputTargetResolver.looksLikeFieldLabel(fieldLabel) &&
                FieldInputTargetResolver.isPickerBackedField(node)
            ) {
                val icon = FieldInputTargetResolver.findPickerIconNearEditable(node)
                if (icon != null) {
                    try {
                        pickerSession.onIconTap(now)
                        AppFileLogger.info(
                            "ActionRecorder",
                            "picker_field_tap_promoted_to_icon label=$fieldLabel id=${icon.viewIdResourceName}",
                        )
                        resolved.recycleIfNeeded()
                        return buildPickerIconTap(
                            packageName = packageName,
                            icon = icon,
                            now = now,
                            screenWidthPx = screenWidthPx,
                            screenHeightPx = screenHeightPx,
                            event = event,
                        )
                    } finally {
                        icon.recycle()
                    }
                }
            }
        }

        if (node?.isEditable == true && !longPress && !dismissFromEvent) {
            if (pickerOpen && FieldInputTargetResolver.isPickerListLabel(eventLabel, eventCd)) {
                resolved.recycleIfNeeded()
                AppFileLogger.info("ActionRecorder", "picker_list_item_rec text=$eventLabel")
                pickerSession.close()
                return tapFromEventLabel(
                    packageName,
                    (eventLabel ?: eventCd)!!.take(80),
                    now,
                    chainFromLabel = true,
                )
            }
            val dismissFromUi = alertDismiss?.text
                ?: findDismissLabelInRoots(rootProvider)
            if (dismissFromUi != null) {
                resolved.recycleIfNeeded()
                AppFileLogger.info(
                    "ActionRecorder",
                    "tap_override_editable_with_dismiss text=$dismissFromUi",
                )
                val target = alertDismiss ?: dev.accessscope.scanner.recorder.capture.AlertDismissTarget(
                    viewId = null,
                    text = dismissFromUi,
                )
                return AlertOverlayResolver.toOptionalTap(packageName, target, now)
            }
            resolved.recycleIfNeeded()
            logDiscard(event.eventType, packageName, "editable_skip_tap")
            return null
        }

        val opensPicker = node != null && FieldInputTargetResolver.opensSelectionPicker(node)
        val iconRedirectEdit = if (node != null && !opensPicker && FieldInputTargetResolver.isFieldAccessoryIcon(node)) {
            FieldInputTargetResolver.findSiblingEditable(node)?.also { edit ->
                AppFileLogger.info(
                    "ActionRecorder",
                    "field_icon_redirect_rec icon=${node.viewIdResourceName} edit=${edit.viewIdResourceName}",
                )
            }
        } else {
            null
        }
        if (opensPicker) {
            pickerSession.onIconTap(now)
            AppFileLogger.info(
                "ActionRecorder",
                "picker_icon_tap_rec id=${node?.viewIdResourceName} text=$eventLabel",
            )
        }
        val identity = TapIdentityResolver.resolve(iconRedirectEdit ?: node, event)
        val viewId = identity.viewId
        var text = identity.text ?: eventLabel
        if (iconRedirectEdit != null) {
            FieldInputTargetResolver.fieldLabelForEditable(iconRedirectEdit)?.let { text = it }
        }
        val cd = identity.contentDescription

        if (MaestroSelectorHeuristics.isSystemChromeTap(packageName, viewId, text, cd) ||
            MaestroSelectorHeuristics.isForeignUiPackage(packageName)
        ) {
            resolved.recycleIfNeeded()
            logDiscard(event.eventType, packageName, "system_chrome_skip")
            return null
        }
        if (MaestroSelectorHeuristics.isNoiseViewId(viewId)) {
            resolved.recycleIfNeeded()
            logDiscard(event.eventType, packageName, "noise_view_id")
            return null
        }

        var px: Float? = null
        var py: Float? = null
        if (iconRedirectEdit != null && screenWidthPx > 0 && screenHeightPx > 0) {
            FieldInputTargetResolver.tapBoundsLeftOfCenter(iconRedirectEdit)?.let { (x, y) ->
                px = ((x / screenWidthPx) * 100f).coerceIn(0f, 100f)
                py = ((y / screenHeightPx) * 100f).coerceIn(0f, 100f)
            }
        } else if (opensPicker && node != null && screenWidthPx > 0 && screenHeightPx > 0) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                px = ((bounds.centerX().toFloat() / screenWidthPx) * 100f).coerceIn(0f, 100f)
                py = ((bounds.centerY().toFloat() / screenHeightPx) * 100f).coerceIn(0f, 100f)
            }
        } else if (node != null && screenWidthPx > 0 && screenHeightPx > 0) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                px = ((bounds.centerX().toFloat() / screenWidthPx) * 100f).coerceIn(0f, 100f)
                py = ((bounds.centerY().toFloat() / screenHeightPx) * 100f).coerceIn(0f, 100f)
            }
        }
        identity.clickableBounds?.let { b ->
            if (iconRedirectEdit == null && screenWidthPx > 0 && screenHeightPx > 0 && !b.isEmpty) {
                px = ((b.centerX().toFloat() / screenWidthPx) * 100f).coerceIn(0f, 100f)
                py = ((b.centerY().toFloat() / screenHeightPx) * 100f).coerceIn(0f, 100f)
            }
        }
        iconRedirectEdit?.recycle()
        resolved.recycleIfNeeded()

        val optional = MaestroSelectorHeuristics.isPopupDismissLabel(text ?: cd ?: eventLabel)
        val chain = buildRecChain(identity.candidates, px, py)

        // Fallback testo evento: Compose dialog senza source ma con label sul click.
        if (viewId == null && text == null && cd == null && (px == null || py == null)) {
            if (!eventLabel.isNullOrBlank()) {
                if (pickerOpen && FieldInputTargetResolver.isPickerListLabel(eventLabel, eventCd)) {
                    pickerSession.close()
                    return tapFromEventLabel(packageName, eventLabel, now, chainFromLabel = true)
                }
                val opt = MaestroSelectorHeuristics.isPopupDismissLabel(eventLabel)
                return tapFromEventLabel(packageName, eventLabel, now, optional = opt)
            }
            // Ultima chance: alert overlay / bottone dismiss tipico nelle root.
            val alert = AlertOverlayResolver.findDismiss(rootProvider)
            if (alert != null) {
                AppFileLogger.info(
                    "ActionRecorder",
                    "tap_from_alert_overlay id=${alert.viewId} text=${alert.text}",
                )
                return AlertOverlayResolver.toOptionalTap(packageName, alert, now)
            }
            val dismiss = findDismissLabelInRoots(rootProvider)
            if (dismiss != null) {
                AppFileLogger.info("ActionRecorder", "tap_from_dismiss_hint text=$dismiss")
                return RecordedAction.Tap(
                    packageName = packageName,
                    text = dismiss.take(80),
                    timestampMs = now,
                    executionMode = StepExecutionMode.Optional,
                    selectorChain = listOf(SelectorCandidate(text = dismiss.take(80))),
                    weakSelector = false,
                )
            }
            return null
        }

        // Point-only: registra come weak (gate ZeroEdit / PICK) invece di scartare il gesto.
        val weak = identity.weak && viewId == null && text == null && cd == null

        return if (longPress) {
            RecordedAction.LongPress(
                packageName = packageName,
                viewId = viewId,
                text = text?.take(80),
                contentDescription = cd?.take(80),
                pointPercentX = px,
                pointPercentY = py,
                timestampMs = now,
            )
        } else {
            RecordedAction.Tap(
                packageName = packageName,
                viewId = viewId,
                text = text?.take(80),
                contentDescription = cd?.take(80),
                pointPercentX = if (weak) px else if (viewId != null || text != null || cd != null) null else px,
                pointPercentY = if (weak) py else if (viewId != null || text != null || cd != null) null else py,
                timestampMs = now,
                executionMode = if (optional) StepExecutionMode.Optional else StepExecutionMode.Required,
                selectorChain = chain,
                weakSelector = weak,
            ).also { tap ->
                val picked = tap.text ?: eventLabel
                if (FieldInputTargetResolver.isPickerListLabel(picked, tap.contentDescription)) {
                    pickerSession.close()
                }
            }
        }
    }

    private fun textOrLabel(eventLabel: String?, node: AccessibilityNodeInfo): String? =
        eventLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

    /** Tap su icona picker (viewId + coordinate centro icona). */
    private fun buildPickerIconTap(
        packageName: String,
        icon: AccessibilityNodeInfo,
        now: Long,
        screenWidthPx: Int,
        screenHeightPx: Int,
        event: AccessibilityEvent,
    ): RecordedAction.Tap {
        val identity = TapIdentityResolver.resolve(icon, event)
        var px: Float? = null
        var py: Float? = null
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            val bounds = Rect()
            icon.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                px = ((bounds.centerX().toFloat() / screenWidthPx) * 100f).coerceIn(0f, 100f)
                py = ((bounds.centerY().toFloat() / screenHeightPx) * 100f).coerceIn(0f, 100f)
            }
        }
        val chain = buildRecChain(identity.candidates, px, py)
        return RecordedAction.Tap(
            packageName = packageName,
            viewId = identity.viewId,
            text = identity.text?.take(80),
            contentDescription = identity.contentDescription?.take(80),
            pointPercentX = px,
            pointPercentY = py,
            timestampMs = now,
            selectorChain = chain,
            weakSelector = identity.weak && identity.viewId == null,
        )
    }

    /**
     * Catena candidati a REC: semantic first, point solo se weak.
     */
    private fun buildRecChain(
        candidates: List<SelectorCandidate>,
        px: Float?,
        py: Float?,
    ): List<SelectorCandidate> {
        val out = candidates.toMutableList()
        if (px != null && py != null && out.none { !it.viewId.isNullOrBlank() || !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() }) {
            out += SelectorCandidate(pointPercentX = px, pointPercentY = py)
        }
        return out.distinctBy { it.dedupeKey() }
    }

    /**
     * Risolve il nodo: source evento → testo evento su **tutte** le root (dialog),
     * senza fallback FOCUS_ACCESSIBILITY (rompe i tap su popup sopra EditText).
     */
    private fun resolveNode(
        event: AccessibilityEvent,
        rootProvider: AccessibilityRootProvider,
        preferFocusFallback: Boolean = false,
    ): ResolvedNode {
        val source = runCatching { event.source }.getOrNull()
        if (source != null) return ResolvedNode(source, owned = true)

        val eventLabel = eventLabel(event)
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        if (roots.isEmpty()) return ResolvedNode(null, owned = false)

        try {
            if (!eventLabel.isNullOrBlank()) {
                for (root in roots) {
                    val list = runCatching { root.findAccessibilityNodeInfosByText(eventLabel) }.getOrNull()
                    val match = list?.firstOrNull { node ->
                        val t = node.text?.toString()
                        val cd = node.contentDescription?.toString()
                        t.equals(eventLabel, ignoreCase = true) ||
                            cd.equals(eventLabel, ignoreCase = true) ||
                            (node.isClickable && (
                                t?.contains(eventLabel, ignoreCase = true) == true ||
                                    cd?.contains(eventLabel, ignoreCase = true) == true
                                ))
                    }
                    if (match != null) {
                        val keep = AccessibilityNodeInfo.obtain(match)
                        list.forEach { it.recycle() }
                        return ResolvedNode(keep, owned = true)
                    }
                    list?.forEach { it.recycle() }
                }
            }

            if (preferFocusFallback) {
                for (root in roots) {
                    val a11yFocus = runCatching {
                        root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                    }.getOrNull()
                    if (a11yFocus != null) return ResolvedNode(a11yFocus, owned = true)
                }
            }
            return ResolvedNode(null, owned = false)
        } finally {
            roots.forEach { it.recycle() }
        }
    }

    private fun eventLabel(event: AccessibilityEvent): String? {
        val items = eventTextItems(event)
        if (items.isNotEmpty()) return items.joinToString(" ")
        return event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Singole stringhe in `event.text` (non concatenate: possono superare 120 char). */
    private fun eventTextItems(event: AccessibilityEvent): List<String> =
        event.text
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
            .orEmpty()

    /**
     * All’apertura dialog: AssertVisible sul titolo + memorizza label dismiss per sintesi se manca CLICKED.
     */
    private fun capturePopupOpen(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
        rootProvider: AccessibilityRootProvider,
    ): List<RecordedAction> {
        val className = event.className?.toString().orEmpty()
        val items = eventTextItems(event)
        val title = pickPopupTitle(items, className)
            ?: if (isDialogClass(className)) findPopupTitleInRoots(rootProvider) else null
        val dismissFromEvent = items.filter { MaestroSelectorHeuristics.isPopupDismissLabel(it) }
        val dismissFromRoots = if (isDialogClass(className) || title != null) {
            listOfNotNull(findDismissLabelInRoots(rootProvider))
        } else {
            emptyList()
        }
        val dismissLabels = (dismissFromEvent + dismissFromRoots)
            .distinctBy { it.lowercase() }
        val isDialog = isDialogClass(className) || title != null || dismissLabels.isNotEmpty()


        if (!isDialog) return emptyList()

        if (dismissLabels.isNotEmpty() || title != null) {
            pendingDialog = PendingDialog(
                packageName = packageName,
                title = title,
                dismissLabels = dismissLabels.ifEmpty {
                    listOf("Non ora", "chiudi")
                },
                openedAtMs = now,
            )
        }

        if (title == null) return emptyList()
        if (FieldInputTargetResolver.isSelectionPickerTitle(title)) {
            pickerSession.onPickerTitle(title, now)
            // Sheet comparso senza tap icona recente → rumore REC (assert orphan).
            if (!pickerSession.hasRecentIconTap(now)) {
                AppFileLogger.info("ActionRecorder", "picker_assert_skipped_orphan title=$title")
                return emptyList()
            }
        }
        val key = "$packageName|$title"
        if (key == lastPopupAssertKey && now - lastPopupAssertAtMs < POPUP_ASSERT_DEBOUNCE_MS) {
            return emptyList()
        }
        lastPopupAssertKey = key
        return listOf(
            RecordedAction.AssertVisible(
                packageName = packageName,
                text = title.take(80),
                timestampMs = now,
                executionMode = if (FieldInputTargetResolver.isSelectionPickerTitle(title)) {
                    StepExecutionMode.Optional
                } else {
                    StepExecutionMode.Required
                },
            ),
        )
    }

    /**
     * Se un dialog era aperto e la finestra cambia senza tap dismiss, registra tap optional.
     */
    private fun synthesizeDismissIfDialogClosed(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
    ): RecordedAction.Tap? {
        val pending = pendingDialog ?: return null
        val className = event.className?.toString().orEmpty()
        if (isDialogClass(className)) return null
        // Stesso dialog ri-notificato: non chiudere.
        val items = eventTextItems(event)
        val titleAgain = pickPopupTitle(items, className)
        if (titleAgain != null && titleAgain == pending.title) return null
        if (now - pending.openedAtMs < 250L) return null

        val label = preferDismissLabel(pending.dismissLabels) ?: return null
        pendingDialog = null
        return RecordedAction.Tap(
            packageName = pending.packageName.ifBlank { packageName },
            text = label.take(80),
            timestampMs = now,
            executionMode = StepExecutionMode.Optional,
        )
    }

    private fun preferDismissLabel(labels: List<String>): String? {
        if (labels.isEmpty()) return null
        val preferred = listOf("non ora", "not now", "chiudi", "close", "annulla", "skip", "later")
        for (p in preferred) {
            labels.firstOrNull { it.equals(p, true) || it.contains(p, true) }?.let { return it }
        }
        return labels.firstOrNull()
    }

    private fun clearPendingDialogIfDismiss(action: RecordedAction) {
        val pending = pendingDialog ?: return
        val text = when (action) {
            is RecordedAction.Tap -> action.text ?: action.contentDescription
            else -> null
        } ?: return
        if (MaestroSelectorHeuristics.isPopupDismissLabel(text) ||
            pending.dismissLabels.any { text.contains(it, ignoreCase = true) }
        ) {
            pendingDialog = null
            pickerSession.close()
        }
    }

    private fun pickPopupTitle(items: List<String>, className: String): String? {
        val candidates = items.filter { it.length in 4..120 && !isNavChromeTitle(it) }
        candidates.firstOrNull { FieldInputTargetResolver.isSelectionPickerTitle(it) }?.let { return it }
        candidates.firstOrNull { looksLikePopupTitle(it, className) }?.let { return it }
        if (isDialogClass(className)) {
            return candidates.firstOrNull {
                !MaestroSelectorHeuristics.isPopupDismissLabel(it) && !isNavChromeTitle(it)
            }
        }
        return null
    }

    private fun isNavChromeTitle(title: String): Boolean {
        val lower = title.trim().lowercase()
        return lower in NAV_CHROME_TITLES || lower.startsWith("torna ")
    }

    private fun tapFromEventLabel(
        packageName: String,
        eventLabel: String,
        now: Long,
        optional: Boolean = false,
        chainFromLabel: Boolean = false,
    ): RecordedAction.Tap {
        val label = eventLabel.take(80)
        return RecordedAction.Tap(
            packageName = packageName,
            text = label,
            timestampMs = now,
            executionMode = if (optional) StepExecutionMode.Optional else StepExecutionMode.Required,
            selectorChain = if (chainFromLabel) listOf(SelectorCandidate(text = label)) else emptyList(),
            weakSelector = false,
        )
    }

    private fun isDialogClass(className: String): Boolean =
        className.contains("Dialog", ignoreCase = true) ||
            className.contains("Popup", ignoreCase = true) ||
            className.contains("BottomSheet", ignoreCase = true) ||
            className.contains("AlertDialog", ignoreCase = true)

    /**
     * AssertVisible su CONTENT_CHANGED solo se l’evento porta già un titolo dialog-like.
     */
    private fun capturePopupAssert(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
        rootProvider: AccessibilityRootProvider,
        allowRootScan: Boolean,
    ): RecordedAction.AssertVisible? {
        val className = event.className?.toString().orEmpty()
        val items = eventTextItems(event)
        val eventTitle = pickPopupTitle(items, className)
        val title = when {
            eventTitle != null -> eventTitle
            allowRootScan -> findPopupTitleInRoots(rootProvider)
            else -> null
        }
        if (title == null) return null
        val key = "$packageName|$title"
        if (key == lastPopupAssertKey && now - lastPopupAssertAtMs < POPUP_ASSERT_DEBOUNCE_MS) return null
        lastPopupAssertKey = key
        return RecordedAction.AssertVisible(
            packageName = packageName,
            text = title.take(80),
            timestampMs = now,
        )
    }

    private fun looksLikePopupTitle(title: String, className: String): Boolean {
        if (isNavChromeTitle(title)) return false
        if (FieldInputTargetResolver.isSelectionPickerTitle(title)) return true
        if (isDialogClass(className)) return true
        val lower = title.lowercase().replace('\n', ' ')
        return POPUP_TITLE_HINTS.any { lower.contains(it) } ||
            (title.split(Regex("\\s+")).size >= 3 && title.length >= 18)
    }

    /**
     * Titolo dialog vicino a un bottone dismiss tipico (es. «Non ora»).
     */
    private fun findPopupTitleInRoots(rootProvider: AccessibilityRootProvider): String? {
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        if (roots.isEmpty()) return null
        try {
            for (root in roots) {
                for (hint in MaestroSelectorHeuristics.POPUP_DISMISS_LABELS) {
                    val list = runCatching { root.findAccessibilityNodeInfosByText(hint) }.getOrNull()
                        ?: continue
                    try {
                        val dismiss = list.firstOrNull { node ->
                            val t = node.text?.toString()?.trim().orEmpty()
                            val cd = node.contentDescription?.toString()?.trim().orEmpty()
                            (t.equals(hint, true) || cd.equals(hint, true) ||
                                t.contains(hint, true) || cd.contains(hint, true)) &&
                                (node.isClickable || node.parent?.isClickable == true)
                        } ?: continue
                        val title = findNearbyTitleText(dismiss) ?: continue
                        if (looksLikePopupTitle(title, "")) return title.take(80)
                    } finally {
                        list.forEach { it.recycle() }
                    }
                }
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return null
    }

    /** Salendo dall’azione dismiss, cerca un testo lungo da usare come titolo dialog. */
    private fun findNearbyTitleText(from: AccessibilityNodeInfo): String? {
        var parent = from.parent
        var depth = 0
        while (parent != null && depth < 6) {
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChild(i) ?: continue
                try {
                    if (child.isClickable) continue
                    val t = child.text?.toString()?.trim().orEmpty()
                    if (t.length in 12..120 && !MaestroSelectorHeuristics.isPopupDismissLabel(t)) {
                        return t
                    }
                } finally {
                    child.recycle()
                }
            }
            val next = parent.parent
            parent.recycle()
            parent = next
            depth++
        }
        parent?.recycle()
        return null
    }

    private fun scrollDirectionOrNull(event: AccessibilityEvent): ScrollDirection? {
        val dy = if (android.os.Build.VERSION.SDK_INT >= 28) {
            event.scrollDeltaY
        } else {
            0
        }
        val dx = if (android.os.Build.VERSION.SDK_INT >= 28) {
            event.scrollDeltaX
        } else {
            0
        }
        val hasPhysicalDelta = kotlin.math.abs(dy) >= SCROLL_MIN_DELTA_PX ||
            kotlin.math.abs(dx) >= SCROLL_MIN_DELTA_PX
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // Solo `fromIndex`/`toIndex` (RecyclerView bind/prefetch) ≠ scroll utente.
            if (!hasPhysicalDelta) return null
        } else {
            val indexChanged = event.fromIndex >= 0 &&
                event.toIndex >= 0 &&
                event.fromIndex != event.toIndex
            if (!indexChanged) return null
        }
        return when {
            kotlin.math.abs(dy) >= kotlin.math.abs(dx) ->
                if (dy < 0) ScrollDirection.UP else ScrollDirection.DOWN
            dx < 0 -> ScrollDirection.LEFT
            else -> ScrollDirection.RIGHT
        }
    }

    /**
     * Cerca etichette dismiss tipiche nelle root (dialog sopra la UI).
     */
    private fun findDismissLabelInRoots(rootProvider: AccessibilityRootProvider): String? {
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        if (roots.isEmpty()) return null
        try {
            for (hint in MaestroSelectorHeuristics.POPUP_DISMISS_LABELS) {
                for (root in roots) {
                    val list = runCatching { root.findAccessibilityNodeInfosByText(hint) }.getOrNull()
                        ?: continue
                    var label: String? = null
                    for (node in list) {
                        val t = node.text?.toString()?.trim().orEmpty()
                        val cd = node.contentDescription?.toString()?.trim().orEmpty()
                        val match = t.equals(hint, true) || cd.equals(hint, true) ||
                            t.contains(hint, true) || cd.contains(hint, true)
                        val actionable = node.isClickable || node.isEnabled ||
                            node.parent?.isClickable == true
                        if (match && actionable) {
                            label = t.takeIf { it.isNotBlank() }
                                ?: cd.takeIf { it.isNotBlank() }
                                ?: hint
                            break
                        }
                    }
                    list.forEach { it.recycle() }
                    if (label != null) return label
                }
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return null
    }

    /**
     * Campione etichette cliccabili nelle root (solo debug).
     */
    private fun sampleClickableLabels(rootProvider: AccessibilityRootProvider): List<String> {
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        if (roots.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        try {
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (out.size >= 12 || depth > 8) return
                val t = n.text?.toString()?.trim().orEmpty()
                val cd = n.contentDescription?.toString()?.trim().orEmpty()
                if (n.isClickable && (t.isNotBlank() || cd.isNotBlank())) {
                    out += (t.ifBlank { cd }).take(40)
                }
                for (i in 0 until n.childCount) {
                    val c = n.getChild(i) ?: continue
                    try {
                        walk(c, depth + 1)
                    } finally {
                        c.recycle()
                    }
                }
            }
            roots.forEach { walk(it, 0) }
        } finally {
            roots.forEach { it.recycle() }
        }
        return out
    }

    private fun logDiscard(eventType: Int, packageName: String, reason: String) {
        AppFileLogger.info(
            "ActionRecorder",
            "discard type=$eventType pkg=$packageName reason=$reason",
        )
    }

    private fun tapKey(action: RecordedAction): String = when (action) {
        is RecordedAction.Tap ->
            "${action.viewId}|${action.text}|${action.contentDescription}|${action.pointPercentX}"
        is RecordedAction.LongPress ->
            "L|${action.viewId}|${action.text}|${action.contentDescription}|${action.pointPercentX}"
        else -> action.toString()
    }

    private data class ResolvedNode(
        val node: AccessibilityNodeInfo?,
        val owned: Boolean,
    ) {
        fun recycleIfNeeded() {
            if (owned) node?.recycle()
        }
    }

    companion object {
        private const val TAP_DEBOUNCE_MS = 150L
        private const val SCROLL_DEBOUNCE_MS = 600L
        private const val SCROLL_SUPPRESS_AFTER_BACK_MS = 700L
        private const val SCROLL_SUPPRESS_AFTER_POPUP_MS = 900L
        private const val SCROLL_SUPPRESS_AFTER_TAP_MS = 800L
        private const val SCROLL_MIN_DELTA_PX = 12
        private const val POPUP_ASSERT_DEBOUNCE_MS = 1_500L
        private const val INFERRED_SELECTION_DEBOUNCE_MS = 2_000L
        /** Testi ricordati per riconoscere la voce scelta dopo la chiusura della lista. */
        private const val RECENT_TEXTS_MAX = 600
        private const val RECENT_TEXTS_TTL_MS = 60_000L
        private const val TYPED_FIELDS_MAX = 32
        private val POPUP_TITLE_HINTS = listOf(
            "caricamento",
            "documento",
            "procedi",
            "permesso",
            "consenti",
            "privacy",
            "cookie",
            "aggiornamento",
            "notifica",
            "abilitare",
            "accedere",
        )
        private val NAV_CHROME_TITLES = setOf(
            "torna indietro",
            "indietro",
            "back",
            "chiudi",
            "close",
        )
    }
}
