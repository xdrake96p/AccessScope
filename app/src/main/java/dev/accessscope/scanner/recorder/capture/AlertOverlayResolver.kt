/**
 * Risoluzione dismiss di overlay/alert in-app (es. Nexi `alert_pop` / `id/dismiss`).
 *
 * Competenza separata da [dev.accessscope.scanner.recorder.ActionRecorder]:
 * quando il click a11y punta all’EditText sotto il dialog, recupera il bottone reale.
 */
package dev.accessscope.scanner.recorder.capture

import android.view.accessibility.AccessibilityNodeInfo
import dev.accessscope.scanner.recorder.AccessibilityRootProvider
import dev.accessscope.scanner.recorder.MaestroSelectorHeuristics
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.SelectorCandidate
import dev.accessscope.scanner.recorder.model.StepExecutionMode

/**
 * Dismiss di un alert/popup risolto dall’albero.
 *
 * @property viewId Resource-id del bottone (es. `…/dismiss`).
 * @property text Etichetta (es. «OK, HO CAPITO»).
 * @property title Titolo alert se trovato (es. «Attenzione!»).
 */
data class AlertDismissTarget(
    val viewId: String?,
    val text: String?,
    val title: String? = null,
) {
    /** `true` se c’è almeno un selettore utilizzabile. */
    fun isUsable(): Boolean = !viewId.isNullOrBlank() || !text.isNullOrBlank()
}

/**
 * Cerca overlay alert (container tipici + bottone dismiss) nelle root a11y.
 */
object AlertOverlayResolver {

    /** Short id tipici del bottone conferma/dismiss in dialog custom banking. */
    private val DISMISS_ID_HINTS = setOf(
        "dismiss",
        "btn_dismiss",
        "btn_ok",
        "button_ok",
        "btn_confirm",
        "alert_ok",
        "positive",
        "btn_positive",
    )

    /** Short id tipici del container alert. */
    private val ALERT_CONTAINER_HINTS = setOf(
        "alert_pop",
        "alert_popup",
        "popup_alert",
        "dialog_alert",
        "custom_dialog",
        "rl_alert_background",
        "fl_to_move",
    )

    /**
     * Cerca un target dismiss nelle root correnti.
     *
     * Priorità: bottone con id dismiss dentro container alert → etichetta [POPUP_DISMISS_LABELS].
     *
     * @param rootProvider Root multi-window.
     * @return Target o `null` se nessun alert dismissibile.
     */
    fun findDismiss(rootProvider: AccessibilityRootProvider): AlertDismissTarget? {
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        if (roots.isEmpty()) return null
        try {
            for (root in roots) {
                val byId = findDismissByViewId(root)
                if (byId != null && byId.isUsable()) return byId
            }
            for (root in roots) {
                val byText = findDismissByLabel(root)
                if (byText != null && byText.isUsable()) return byText
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return null
    }

    /**
     * `true` se nelle root c’è un container alert noto (anche senza aver risolto il bottone).
     *
     * @param rootProvider Root multi-window.
     */
    fun hasAlertOverlay(rootProvider: AccessibilityRootProvider): Boolean {
        val roots = rootProvider.roots().ifEmpty { listOfNotNull(rootProvider.root()) }
        if (roots.isEmpty()) return false
        try {
            for (root in roots) {
                if (findNodeByShortIdHints(root, ALERT_CONTAINER_HINTS) != null) return true
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return false
    }

    /**
     * Costruisce un [RecordedAction.Tap] optional dal target dismiss.
     *
     * @param packageName Package target.
     * @param target Dismiss risolto.
     * @param timestampMs Timestamp ms.
     * @return Tap optional con catena id+testo.
     */
    fun toOptionalTap(
        packageName: String,
        target: AlertDismissTarget,
        timestampMs: Long = System.currentTimeMillis(),
    ): RecordedAction.Tap {
        val chain = mutableListOf<SelectorCandidate>()
        if (!target.viewId.isNullOrBlank()) chain += SelectorCandidate(viewId = target.viewId)
        if (!target.text.isNullOrBlank()) chain += SelectorCandidate(text = target.text)
        return RecordedAction.Tap(
            packageName = packageName,
            viewId = target.viewId,
            text = target.text?.take(80),
            timestampMs = timestampMs,
            executionMode = StepExecutionMode.Optional,
            selectorChain = chain,
            weakSelector = false,
        )
    }

    private fun findDismissByViewId(root: AccessibilityNodeInfo): AlertDismissTarget? {
        val dismissNode = findNodeByShortIdHints(root, DISMISS_ID_HINTS) ?: return null
        try {
            val id = dismissNode.viewIdResourceName
            val text = dismissNode.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: dismissNode.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val title = findAlertTitleNear(root)
            // Preferisci se siamo in un alert o l’etichetta è tipica dismiss.
            val inAlert = hasAncestorShortId(dismissNode, ALERT_CONTAINER_HINTS) ||
                findNodeByShortIdHints(root, ALERT_CONTAINER_HINTS) != null
            val labelOk = MaestroSelectorHeuristics.isPopupDismissLabel(text) ||
                text?.contains("capito", ignoreCase = true) == true
            if (!inAlert && !labelOk) return null
            return AlertDismissTarget(viewId = id, text = text, title = title)
        } finally {
            dismissNode.recycle()
        }
    }

    private fun findDismissByLabel(root: AccessibilityNodeInfo): AlertDismissTarget? {
        for (hint in MaestroSelectorHeuristics.POPUP_DISMISS_LABELS) {
            val list = runCatching { root.findAccessibilityNodeInfosByText(hint) }.getOrNull()
                ?: continue
            try {
                for (node in list) {
                    val t = node.text?.toString()?.trim().orEmpty()
                    val cd = node.contentDescription?.toString()?.trim().orEmpty()
                    val match = t.equals(hint, true) || cd.equals(hint, true) ||
                        t.contains(hint, true) || cd.contains(hint, true)
                    if (!match) continue
                    val clickable = node.isClickable || node.parent?.isClickable == true
                    if (!clickable) continue
                    val id = node.viewIdResourceName
                    val label = t.takeIf { it.isNotBlank() } ?: cd.takeIf { it.isNotBlank() } ?: hint
                    return AlertDismissTarget(
                        viewId = id,
                        text = label,
                        title = findAlertTitleNear(root),
                    )
                }
            } finally {
                list.forEach { it.recycle() }
            }
        }
        return null
    }

    private fun findAlertTitleNear(root: AccessibilityNodeInfo): String? {
        val titleIds = setOf("txt_info", "alert_title", "dialog_title", "title")
        val node = findNodeByShortIdHints(root, titleIds) ?: return null
        return try {
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        } finally {
            node.recycle()
        }
    }

    private fun findNodeByShortIdHints(
        root: AccessibilityNodeInfo,
        hints: Set<String>,
    ): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (found != null || depth > 14) return
            val short = n.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            if (short in hints) {
                found = AccessibilityNodeInfo.obtain(n)
                return
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                try {
                    walk(c, depth + 1)
                } finally {
                    c.recycle()
                }
                if (found != null) return
            }
        }
        walk(root, 0)
        return found
    }

    private fun hasAncestorShortId(node: AccessibilityNodeInfo, hints: Set<String>): Boolean {
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 10) {
            val short = current.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            if (short in hints) {
                current.recycle()
                return true
            }
            val parent = current.parent
            current.recycle()
            current = parent
            depth++
        }
        return false
    }
}
