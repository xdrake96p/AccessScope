/**
 * Filtro azioni spurie nella pipeline Maestro (noise, focus, scroll tastiera).
 */
package dev.accessscope.scanner.recorder.optimization.noise

import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction

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
            var i = index - 1
            while (i >= 0 && actions[i] is RecordedAction.Scroll) i--
            when (actions.getOrNull(i)) {
                is RecordedAction.InputText,
                is RecordedAction.Wait,
                is RecordedAction.WaitForAnimation,
                is RecordedAction.HideKeyboard,
                -> return@filterIndexed false
                else -> Unit
            }
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
        // Dopo scroll/IME: tap senza testo/id = spesso fantasma.
        return tap.viewId.isNullOrBlank() && tap.text.isNullOrBlank() &&
            tap.contentDescription.isNullOrBlank()
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

    /**
     * Collassa tap identici (stesso testo/id) separati solo da Wait / HideKeyboard.
     * Evita doppio tap accordion da a11y duplicati.
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
                    if (onlyWaitsBetween && sameLogicalTap(prev, action)) {
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
