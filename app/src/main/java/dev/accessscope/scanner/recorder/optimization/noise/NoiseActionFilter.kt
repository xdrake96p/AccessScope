/**
 * Filtro azioni spurie nella pipeline Maestro (noise, focus, scroll tastiera).
 */
package dev.accessscope.scanner.recorder.optimization.noise

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.StepExecutionMode
import dev.accessscope.scanner.util.DebugSessionLog

/**
 * Rimuove tap/wait/scroll non utili per replay e export YAML.
 */
object NoiseActionFilter {

    private val KEYBOARD_PKG_HINTS = listOf(
        "com.google.android.inputmethod",
        "com.samsung.android.honeyboard",
        "com.sec.android.inputmethod",
        "com.touchtype.swiftkey",
    )

    /**
     * Rimuove scroll tra input o da package tastiera.
     *
     * @param actions Azioni in ingresso.
     * @return Azioni filtrate.
     */
    fun dropNoiseScrolls(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        return actions.filterIndexed { index, action ->
            if (action !is RecordedAction.Scroll) return@filterIndexed true
            if (KEYBOARD_PKG_HINTS.any { action.packageName.startsWith(it) }) return@filterIndexed false
            val prev = actions.getOrNull(index - 1)
            val next = actions.getOrNull(index + 1)
            if (prev is RecordedAction.InputText || next is RecordedAction.InputText) return@filterIndexed false
            // Scroll tra tap accordion/section header: chiude la sezione appena aperta.
            if (isSectionHeaderTap(prev) || isSectionHeaderTap(next)) return@filterIndexed false
            // Scroll dopo input/wait (IME / lista): rumore tipico post-PIN.
            //
            // Bug reale (flusso AXA registrato): questo filtro gira sia in optimize() sia di
            // nuovo in sanitizeForPlay() — ma tra le due passate WaitPlanner inserisce un
            // WaitForAnimation dopo OGNI scroll. Alla seconda passata, camminare indietro solo
            // attraverso Scroll consecutivi si fermava sul wait appena inserito e trattava come
            // "rumore post-PIN" ogni scroll di una sequenza reale di scroll multipli tranne il
            // primo — 4 scroll reali diventavano 1 solo scroll + 4 waitForAnimationToEnd orfani
            // nello YAML esportato, perdendo la distanza di scroll necessaria a raggiungere il
            // target. Ora la camminata indietro tollera anche i wait-like interposti, per
            // trovare il vero innesco della sequenza indipendentemente da quante volte il
            // filtro è già passato su azioni via via arricchite.
            var i = index - 1
            while (i >= 0 && (actions[i] is RecordedAction.Scroll || isWaitLike(actions[i]))) i--
            // Il salto sopra assorbe già Wait/WaitForAnimation/HideKeyboard: qui può arrivare
            // solo InputText (o qualunque altra azione reale, che non è rumore).
            if (actions.getOrNull(i) is RecordedAction.InputText) return@filterIndexed false
            true
        }
    }

    /** Tap su header sezione / accordion (etichetta generica). */
    private fun isSectionHeaderTap(action: RecordedAction?): Boolean {
        val tap = action as? RecordedAction.Tap ?: return false
        if (MaestroSelectorHeuristics.isSectionHeaderLabel(tap.text)) return true
        if (MaestroSelectorHeuristics.isAmbiguousSharedViewId(tap.viewId) &&
            !tap.text.isNullOrBlank()
        ) {
            return true
        }
        return false
    }

    /**
     * Rimuove tap fantasma subito dopo scroll / hideKeyboard (stesso punto ±8%, ≤300ms).
     */
    fun dropGhostTapsAfterScrollOrIme(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.size < 2) return actions
        val out = mutableListOf<RecordedAction>()
        for (i in actions.indices) {
            val action = actions[i]
            if (action is RecordedAction.Tap) {
                val prev = actions.getOrNull(i - 1)
                val afterScrollOrIme = prev is RecordedAction.Scroll ||
                    prev is RecordedAction.ScrollUntilVisible ||
                    prev is RecordedAction.HideKeyboard
                if (afterScrollOrIme && isGhostNearPrevPoint(prev, action)) {
                    continue
                }
                // Tap point-like subito dopo altro tap sulle stesse % entro 300ms.
                val lastTap = out.lastOrNull() as? RecordedAction.Tap
                if (lastTap != null &&
                    kotlin.math.abs(action.timestampMs - lastTap.timestampMs) <= 300L &&
                    samePointApprox(lastTap, action) &&
                    action.text.isNullOrBlank() &&
                    action.viewId.isNullOrBlank()
                ) {
                    continue
                }
            }
            out += action
        }
        return out
    }

    private fun isGhostNearPrevPoint(prev: RecordedAction?, tap: RecordedAction.Tap): Boolean {
        if (prev == null) return false
        if (kotlin.math.abs(tap.timestampMs - prev.timestampMs) > 300L) return false
        // Dopo scroll/IME: tap senza id = spesso fantasma (anche con testo accidentale corto).
        if (!tap.viewId.isNullOrBlank()) return false
        if (tap.contentDescription.isNullOrBlank() &&
            (tap.text.isNullOrBlank() || (tap.text?.length ?: 0) <= 2)
        ) {
            return true
        }
        // Testo presente ma stesso punto del tap precedente entro 300ms → duplicato spurio.
        return false
    }

    private fun samePointApprox(a: RecordedAction.Tap, b: RecordedAction.Tap): Boolean {
        val ax = a.pointPercentX ?: return false
        val ay = a.pointPercentY ?: return false
        val bx = b.pointPercentX ?: return false
        val by = b.pointPercentY ?: return false
        return kotlin.math.abs(ax - bx) <= 8f && kotlin.math.abs(ay - by) <= 8f
    }

    /**
     * Rimuove tap SystemUI / IME / nav-bar (es. Indietro che chiude la tastiera).
     *
     * @param actions Azioni grezze.
     * @param appId Package target; azioni di altri package non-LaunchApp vengono scartate.
     */
    fun dropForeignUiActions(actions: List<RecordedAction>, appId: String): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val target = appId.ifBlank {
            actions.firstOrNull { it is RecordedAction.LaunchApp }?.packageName.orEmpty()
        }
        return actions.filter { action ->
            when (action) {
                is RecordedAction.LaunchApp,
                is RecordedAction.Wait,
                is RecordedAction.WaitForAnimation,
                is RecordedAction.HideKeyboard,
                is RecordedAction.Back,
                is RecordedAction.PressKey,
                is RecordedAction.AssertVisible,
                is RecordedAction.AssertNotVisible,
                is RecordedAction.OpenLink,
                is RecordedAction.StopApp,
                is RecordedAction.RawMaestroYaml,
                is RecordedAction.EraseText,
                is RecordedAction.Swipe,
                -> true
                is RecordedAction.Tap -> !isForeignOrChromeTap(action, target)
                is RecordedAction.DoubleTap ->
                    !MaestroSelectorHeuristics.isSystemChromeTap(
                        action.packageName,
                        action.viewId,
                        action.text,
                        action.contentDescription,
                    ) && packageOk(action.packageName, target)
                is RecordedAction.LongPress ->
                    !MaestroSelectorHeuristics.isSystemChromeTap(
                        action.packageName,
                        action.viewId,
                        action.text,
                        action.contentDescription,
                    ) && packageOk(action.packageName, target)
                is RecordedAction.InputText -> packageOk(action.packageName, target)
                is RecordedAction.Scroll,
                is RecordedAction.ScrollUntilVisible,
                ->
                    packageOk(action.packageName, target) &&
                        !MaestroSelectorHeuristics.isForeignUiPackage(action.packageName)
            }
        }
    }

    /**
     * Rimuove tap su progress/loading, SystemUI e campi editabili.
     * Usato in pipeline di **ottimizzazione registrazione** (non su Play di flussi editati).
     */
    fun dropNoiseTaps(actions: List<RecordedAction>): List<RecordedAction> =
        actions.filter { action ->
            when (action) {
                is RecordedAction.Tap -> {
                    !MaestroSelectorHeuristics.isNoiseTap(action) &&
                        !MaestroSelectorHeuristics.isEditableFieldViewId(action.viewId)
                }
                is RecordedAction.DoubleTap ->
                    !MaestroSelectorHeuristics.isNoiseViewId(action.viewId) &&
                        !MaestroSelectorHeuristics.isSystemChromeTap(
                            action.packageName,
                            action.viewId,
                            action.text,
                            action.contentDescription,
                        )
                is RecordedAction.LongPress ->
                    !MaestroSelectorHeuristics.isNoiseViewId(action.viewId) &&
                        !MaestroSelectorHeuristics.isSystemChromeTap(
                            action.packageName,
                            action.viewId,
                            action.text,
                            action.contentDescription,
                        )
                else -> true
            }
        }

    /**
     * Filtro leggero per Play: solo tap SystemUI / progress / selettore vuoto / point-only.
     * Conserva tap su campi editabili, hideKeyboard e wait aggiunti dall’editor.
     */
    fun dropPlaybackNoiseTaps(actions: List<RecordedAction>): List<RecordedAction> =
        actions.filter { action ->
            when (action) {
                is RecordedAction.Tap -> {
                    !MaestroSelectorHeuristics.isSystemChromeTap(
                        action.packageName,
                        action.viewId,
                        action.text,
                        action.contentDescription,
                    ) &&
                        !MaestroSelectorHeuristics.isNoiseViewId(action.viewId) &&
                        // Point-only: coordinate obsolete su un’altra schermata → tap spurî.
                        !(
                            action.viewId.isNullOrBlank() &&
                                action.text.isNullOrBlank() &&
                                action.contentDescription.isNullOrBlank()
                            )
                }
                is RecordedAction.DoubleTap ->
                    !MaestroSelectorHeuristics.isNoiseViewId(action.viewId) &&
                        !MaestroSelectorHeuristics.isSystemChromeTap(
                            action.packageName,
                            action.viewId,
                            action.text,
                            action.contentDescription,
                        )
                is RecordedAction.LongPress ->
                    !MaestroSelectorHeuristics.isNoiseViewId(action.viewId) &&
                        !MaestroSelectorHeuristics.isSystemChromeTap(
                            action.packageName,
                            action.viewId,
                            action.text,
                            action.contentDescription,
                        )
                else -> true
            }
        }

    /** Tetto massimo tra due tap "duplicati" perché considerati lo stesso doppio tap umano. */
    private const val DUPLICATE_TAP_MAX_GAP_MS = 4_000L

    /**
     * Collassa tap identici (stesso testo/id) separati solo da Wait / HideKeyboard.
     * Evita doppio tap accordion da a11y duplicati.
     *
     * Limitato a [DUPLICATE_TAP_MAX_GAP_MS]: senza un tetto, due tap sullo stesso testo separati
     * da un caricamento lento di decine di secondi verrebbero uniti allo stesso modo di un vero
     * doppio tap umano — un rischio strutturale non ancora osservato ma plausibile. Sui dati
     * reali che hanno motivato questa funzione (flusso AXA registrato) i doppi tap genuini erano
     * tutti a 0.8–1.8s di distanza, ampiamente sotto la soglia.
     */
    fun dropDuplicateTapsAcrossWaits(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.size < 2) return actions
        val out = mutableListOf<RecordedAction>()
        for (action in actions) {
            if (action is RecordedAction.Tap) {
                val lastTapIdx = out.indexOfLast { it is RecordedAction.Tap }
                if (lastTapIdx >= 0) {
                    val prev = out[lastTapIdx] as RecordedAction.Tap
                    val onlyWaitsBetween = out.subList(lastTapIdx + 1, out.size).all { isWaitLike(it) }
                    val withinGap = action.timestampMs - prev.timestampMs <= DUPLICATE_TAP_MAX_GAP_MS
                    if (onlyWaitsBetween && withinGap && sameLogicalTap(prev, action)) {
                        continue
                    }
                }
            }
            out += action
        }
        return out
    }

    /**
     * Rimuove ScrollUntilVisible su id strutturali (drawer_layout, …) inutili/pericolosi.
     */
    fun dropStructuralScrollUntilVisible(actions: List<RecordedAction>): List<RecordedAction> =
        actions.filter { action ->
            if (action !is RecordedAction.ScrollUntilVisible) return@filter true
            !MaestroSelectorHeuristics.isStructuralContainerViewId(action.visibleId)
        }

    private fun isWaitLike(action: RecordedAction): Boolean =
        action is RecordedAction.Wait ||
            action is RecordedAction.WaitForAnimation ||
            action is RecordedAction.HideKeyboard

    private fun sameLogicalTap(a: RecordedAction.Tap, b: RecordedAction.Tap): Boolean {
        val textA = a.text?.trim()?.lowercase().orEmpty()
        val textB = b.text?.trim()?.lowercase().orEmpty()
        if (textA.isNotBlank() && textA == textB) return true
        val idA = MaestroSelectorHeuristics.shortViewId(a.viewId)
        val idB = MaestroSelectorHeuristics.shortViewId(b.viewId)
        if (!idA.isNullOrBlank() && idA == idB && textA == textB) return true
        return false
    }

    private fun isForeignOrChromeTap(action: RecordedAction.Tap, appId: String): Boolean =
        MaestroSelectorHeuristics.isSystemChromeTap(
            action.packageName,
            action.viewId,
            action.text,
            action.contentDescription,
        ) || !packageOk(action.packageName, appId)

    private fun packageOk(packageName: String, appId: String): Boolean {
        if (MaestroSelectorHeuristics.isCaptureDialogPackage(packageName)) return true
        if (MaestroSelectorHeuristics.isForeignUiPackage(packageName)) return false
        if (appId.isBlank() || packageName.isBlank()) return true
        return packageName == appId
    }

    /**
     * Per OTP/PIN su `edit1`…`edit6` (EditText reali):
     * 1) collassa N `inputText` sugli slot in **un solo** `inputText` su `edit1` col codice;
     * 2) **elimina** i tap del pad (`uno`/`due`/…) subito dopo (SET_TEXT sugli slot basta;
     *    i tap optional facevano aspettare 10s e riscrivevano le cifre);
     * 3) collassa sequenze di soli tap pad (4–8 cifre) in un `inputText` su `edit1`
     *    (es. conferma PIN senza TEXT_CHANGED registrati).
     *
     * @param actions Azioni in ingresso.
     * @return Azioni ripulite per Play affidabile.
     */
    fun normalizePinOrOtpSlotInputs(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val coalesced = coalesceDigitSlotInputs(actions)
        val withoutPadDupes = dropRedundantPinPadKeysAndWaits(coalesced)
        val out = coalescePinPadDigitTaps(withoutPadDupes)
        // #region agent log
        runCatching {
            val slotIns = out.count {
                it is RecordedAction.InputText &&
                    MaestroSelectorHeuristics.isPinPadDigitSlot(it.viewId)
            }
            val optPads = out.count {
                it is RecordedAction.Tap &&
                    it.executionMode == StepExecutionMode.Optional &&
                    MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text)
            }
            val reqPads = out.count {
                it is RecordedAction.Tap &&
                    it.executionMode != StepExecutionMode.Optional &&
                    (
                        MaestroSelectorHeuristics.isPinPadKey(it.viewId, it.text) ||
                            MaestroSelectorHeuristics.isPinPadDigitTap(it.text, it.viewId)
                        )
            }
            DebugSessionLog.log(
                "E",
                "NoiseActionFilter.normalizePinOrOtpSlotInputs",
                "normalize_result",
                mapOf(
                    "in" to actions.size,
                    "out" to out.size,
                    "slotInputs" to slotIns,
                    "optionalPads" to optPads,
                    "requiredPads" to reqPads,
                ),
            )
        }
        // #endregion
        return out
    }

    /**
     * Unisce run consecutive di InputText su slot `editN` in un input su `edit1`.
     */
    private fun coalesceDigitSlotInputs(actions: List<RecordedAction>): List<RecordedAction> {
        val out = mutableListOf<RecordedAction>()
        var i = 0
        while (i < actions.size) {
            val a = actions[i]
            if (a is RecordedAction.InputText &&
                MaestroSelectorHeuristics.isPinPadDigitSlot(a.viewId)
            ) {
                val run = mutableListOf(a)
                var j = i + 1
                while (j < actions.size) {
                    val n = actions[j]
                    when {
                        n is RecordedAction.InputText &&
                            MaestroSelectorHeuristics.isPinPadDigitSlot(n.viewId) -> {
                            run += n
                            j++
                        }
                        n is RecordedAction.WaitForAnimation || n is RecordedAction.Wait -> j++
                        else -> break
                    }
                }
                val code = resolveOtpOrPinCode(run)
                val first = run.first()
                val pkg = first.packageName
                val baseId = first.viewId?.substringBeforeLast('/')?.let { "$it/edit1" }
                    ?: first.viewId
                out += RecordedAction.InputText(
                    packageName = pkg,
                    text = code,
                    viewId = baseId,
                    isPassword = first.isPassword,
                    timestampMs = first.timestampMs,
                )
                i = j
            } else {
                out += a
                i++
            }
        }
        return collapseAdjacentAnimationWaits(out)
    }

    /**
     * Deduce il codice da inserire: preferisce un codice completo ripetuto, altrimenti
     * concatena le ultime cifre per slot.
     */
    private fun resolveOtpOrPinCode(run: List<RecordedAction.InputText>): String {
        val texts = run.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (texts.isEmpty()) return ""
        // Tutti uguali e lunghezza ≥4 → un solo codice (es. "123456" su ogni edit).
        if (texts.distinct().size == 1 && texts.first().length >= 4) {
            return texts.first().take(12)
        }
        // Una cifra (o ultimo char) per slot in ordine edit1…editN.
        val bySlot = run.mapNotNull { action ->
            val short = MaestroSelectorHeuristics.shortViewId(action.viewId)?.lowercase().orEmpty()
            val num = Regex("(\\d{1,2})$").find(short)?.groupValues?.get(1)?.toIntOrNull()
            val digit = action.text.trim().lastOrNull()?.takeIf { it.isDigit() }?.toString()
                ?: action.text.trim().takeIf { it.length == 1 }
            if (num != null && digit != null) num to digit else null
        }.sortedBy { it.first }
        if (bySlot.isNotEmpty()) {
            return bySlot.joinToString("") { it.second }.take(12)
        }
        return texts.last().take(12)
    }

    /**
     * Elimina tap pad e wait su id pad subito dopo un input su slot,
     * fino a CONTINUA/Conferma/OK. Evita doppia scrittura e timeout su pad assente.
     */
    private fun dropRedundantPinPadKeysAndWaits(actions: List<RecordedAction>): List<RecordedAction> {
        var dropPads = false
        return actions.mapNotNull { action ->
            if (action is RecordedAction.InputText &&
                MaestroSelectorHeuristics.isPinPadDigitSlot(action.viewId)
            ) {
                dropPads = true
                return@mapNotNull action
            }
            if (dropPads && action is RecordedAction.Tap &&
                (
                    MaestroSelectorHeuristics.isPinPadKey(action.viewId, action.text) ||
                        MaestroSelectorHeuristics.isPinPadDigitTap(action.text, action.viewId)
                    )
            ) {
                return@mapNotNull null
            }
            if (dropPads && action is RecordedAction.Wait &&
                MaestroSelectorHeuristics.isPinPadKey(action.visibleId, null)
            ) {
                return@mapNotNull null
            }
            if (action is RecordedAction.Tap) {
                val t = action.text?.lowercase().orEmpty()
                if (t.contains("continua") || t.contains("conferma") || t == "ok") {
                    dropPads = false
                }
            }
            action
        }
    }

    /**
     * Sequenza di 4–8 tap pad → un `inputText` su `edit1` (Play distribuisce le cifre).
     */
    private fun coalescePinPadDigitTaps(actions: List<RecordedAction>): List<RecordedAction> {
        val out = mutableListOf<RecordedAction>()
        var i = 0
        while (i < actions.size) {
            val a = actions[i]
            if (a is RecordedAction.Tap && isPinPadDigitAction(a)) {
                val run = mutableListOf(a)
                var j = i + 1
                while (j < actions.size) {
                    val n = actions[j]
                    when {
                        n is RecordedAction.Tap && isPinPadDigitAction(n) -> {
                            run += n
                            j++
                        }
                        n is RecordedAction.WaitForAnimation || n is RecordedAction.Wait -> j++
                        else -> break
                    }
                }
                val digits = run.mapNotNull { tapToDigit(it) }
                if (digits.size in 4..8) {
                    val first = run.first()
                    val pkg = first.packageName
                    out += RecordedAction.InputText(
                        packageName = pkg,
                        text = digits.joinToString(""),
                        viewId = "$pkg:id/edit1",
                        timestampMs = first.timestampMs,
                    )
                    i = j
                } else {
                    out += a
                    i++
                }
            } else {
                out += a
                i++
            }
        }
        return collapseAdjacentAnimationWaits(out)
    }

    private fun isPinPadDigitAction(action: RecordedAction.Tap): Boolean =
        MaestroSelectorHeuristics.isPinPadKey(action.viewId, action.text) ||
            MaestroSelectorHeuristics.isPinPadDigitTap(action.text, action.viewId)

    private fun tapToDigit(action: RecordedAction.Tap): String? {
        action.text?.trim()?.takeIf { it.length == 1 && it[0].isDigit() }?.let { return it }
        val short = MaestroSelectorHeuristics.shortViewId(action.viewId)?.lowercase().orEmpty()
        return when (short) {
            "zero" -> "0"
            "uno" -> "1"
            "due" -> "2"
            "tre" -> "3"
            "quattro" -> "4"
            "cinque" -> "5"
            "sei" -> "6"
            "sette" -> "7"
            "otto" -> "8"
            "nove" -> "9"
            else -> Regex("(\\d)$").find(short)?.groupValues?.get(1)
        }
    }

    /**
     * Collassa run consecutive di [RecordedAction.WaitForAnimation] tenendo il timeout più lungo.
     */
    private fun collapseAdjacentAnimationWaits(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.size < 2) return actions
        val out = mutableListOf<RecordedAction>()
        for (action in actions) {
            val prev = out.lastOrNull()
            if (action is RecordedAction.WaitForAnimation && prev is RecordedAction.WaitForAnimation) {
                out[out.lastIndex] = if ((action.timeoutMs ?: 0L) >= (prev.timeoutMs ?: 0L)) {
                    action
                } else {
                    prev
                }
            } else {
                out += action
            }
        }
        return out
    }

    /**
     * Rimuove assert spurî da overlay rating / Play Store catturati durante PIN.
     */
    fun dropSpuriousRatingAsserts(actions: List<RecordedAction>): List<RecordedAction> =
        actions.filterNot { action ->
            if (action !is RecordedAction.AssertVisible) return@filterNot false
            val t = action.text?.lowercase().orEmpty()
            t.contains("valutazione") || t.contains("lasciare una recensione") ||
                t.contains("rate this") || t.contains("enjoying") ||
                (t.contains("scoprire questa app") && t.length > 40)
        }

    /**
     * Rimuove tap di focus prima di inputText sullo stesso campo.
     */
    fun dropFocusTapsBeforeInput(actions: List<RecordedAction>): List<RecordedAction> {
        if (actions.isEmpty()) return actions
        val out = mutableListOf<RecordedAction>()
        var i = 0
        while (i < actions.size) {
            val a = actions[i]
            val next = actions.getOrNull(i + 1)
            if (a is RecordedAction.Tap && next is RecordedAction.InputText && sameFieldTapAndInput(a, next)) {
                i++
                continue
            }
            out += a
            i++
        }
        return out
    }

    /**
     * Rimuove extendedWaitUntil su id loading/progress.
     */
    fun dropNoiseWaits(actions: List<RecordedAction>): List<RecordedAction> =
        actions.filter { action ->
            if (action !is RecordedAction.Wait) return@filter true
            !MaestroSelectorHeuristics.isNoiseViewId(action.visibleId)
        }

    private fun sameFieldTapAndInput(tap: RecordedAction.Tap, input: RecordedAction.InputText): Boolean {
        val tapId = MaestroSelectorHeuristics.shortViewId(tap.viewId)
        val inputId = MaestroSelectorHeuristics.shortViewId(input.viewId)
        if (!tapId.isNullOrBlank() && !inputId.isNullOrBlank()) return tapId == inputId
        return MaestroSelectorHeuristics.isEditableFieldViewId(tap.viewId) &&
            MaestroSelectorHeuristics.isEditableFieldViewId(input.viewId)
    }
}
