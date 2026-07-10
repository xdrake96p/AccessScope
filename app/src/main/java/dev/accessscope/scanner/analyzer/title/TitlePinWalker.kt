package dev.accessscope.scanner.analyzer.title

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

internal object TitlePinWalker {
        fun findPinScreen(root: AccessibilityNodeInfo): String? {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var hasPinPad = false
            var hasNumericKey = false
            var hasDeleteKey = false
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val id = node.viewIdResourceName.orEmpty().lowercase()
                val text = node.text?.toString()?.trim().orEmpty()
                if (id.contains("pin_pad") || id.contains("pinpad") || id.contains("pin_pad_view") ||
                    id.endsWith("/background_pin")
                ) {
                    hasPinPad = true
                }
                if (id.endsWith("/uno") || id.endsWith("/due") || id.endsWith("/tre")) {
                    hasNumericKey = true
                }
                if (id.endsWith("/cancell") || id.endsWith("/zero")) {
                    hasDeleteKey = true
                }
                if (text.contains("inserisci", true) && text.contains("pin", true) ||
                    text.contains("inserisci pin", true) ||
                    id.contains("caption_pin") || id.contains("caption_otp")
                ) {
                    return "Inserisci PIN"
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::add)
                }
            }
            if (hasPinPad || (hasNumericKey && hasDeleteKey)) return "Inserisci PIN"
            return null
        }
}
