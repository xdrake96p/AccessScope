/**
 * Import YAML Maestro subset → [RecordedAction] (Maestro Beta).
 */
package dev.accessscope.scanner.recorder

/**
 * Risultato parse import YAML.
 */
sealed class MaestroImportResult {
    data class Success(
        val appId: String,
        val name: String,
        val actions: List<RecordedAction>,
        val rawYaml: String,
    ) : MaestroImportResult()

    data class Failure(val message: String) : MaestroImportResult()
}

/**
 * Parser **subset** compatibile con export AccessScope / comandi Maestro comuni.
 *
 * Supportati: `launchApp`, `tapOn`, `doubleTapOn`, `longPressOn`, `inputText`, `eraseText`,
 * `scroll`, `scrollUntilVisible`, `swipe`, `back`, `pressKey`, `assertVisible`,
 * `assertNotVisible`, `openLink`, `stopApp`, `hideKeyboard`, `waitForAnimationToEnd`,
 * `extendedWaitUntil`.
 */
object MaestroYamlImporter {

    /**
     * Analizza il testo YAML completo.
     *
     * @param yaml Contenuto file.
     * @return [MaestroImportResult.Success] o [MaestroImportResult.Failure] senza salvataggio parziale.
     */
    fun parse(yaml: String): MaestroImportResult {
        val lines = yaml.lines()
        var appId: String? = null
        var name: String? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("appId:") -> appId = unquote(line.substringAfter("appId:").trim())
                line.startsWith("name:") -> name = unquote(line.substringAfter("name:").trim())
                line == "---" -> {
                    i++
                    break
                }
            }
            i++
        }
        if (appId.isNullOrBlank()) {
            return MaestroImportResult.Failure("Campo appId mancante o non valido")
        }
        val pkg = appId
        val actions = mutableListOf<RecordedAction>()
        try {
            while (i < lines.size) {
                val raw = lines[i]
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    i++
                    continue
                }
                if (!line.startsWith("- ")) {
                    return MaestroImportResult.Failure("Riga ${i + 1}: atteso comando '- …', trovato: $line")
                }
                val cmd = line.removePrefix("- ").trim()
                when {
                    cmd == "launchApp" -> {
                        actions += RecordedAction.LaunchApp(pkg)
                        i++
                    }
                    cmd == "scroll" -> {
                        actions += RecordedAction.Scroll(pkg)
                        i++
                    }
                    cmd == "back" -> {
                        actions += RecordedAction.Back(pkg)
                        i++
                    }
                    cmd == "hideKeyboard" -> {
                        actions += RecordedAction.HideKeyboard(pkg)
                        i++
                    }
                    cmd == "eraseText" -> {
                        actions += RecordedAction.EraseText(pkg)
                        i++
                    }
                    cmd.startsWith("eraseText:") -> {
                        val (block, next) = readBlock(lines, i)
                        actions += RecordedAction.EraseText(
                            packageName = pkg,
                            viewId = block["id"]?.let { unquote(it) },
                        )
                        i = next
                    }
                    cmd == "stopApp" -> {
                        actions += RecordedAction.StopApp(pkg)
                        i++
                    }
                    cmd.startsWith("openLink:") -> {
                        actions += RecordedAction.OpenLink(
                            pkg,
                            unquote(cmd.substringAfter("openLink:").trim()),
                        )
                        i++
                    }
                    cmd.startsWith("pressKey:") -> {
                        actions += RecordedAction.PressKey(
                            pkg,
                            unquote(cmd.substringAfter("pressKey:").trim()).ifBlank { "Enter" },
                        )
                        i++
                    }
                    cmd == "waitForAnimationToEnd" -> {
                        actions += RecordedAction.WaitForAnimation(pkg)
                        i++
                    }
                    cmd.startsWith("waitForAnimationToEnd:") -> {
                        val (block, next) = readBlock(lines, i)
                        val timeout = block["timeout"]?.toLongOrNull()
                        actions += RecordedAction.WaitForAnimation(pkg, timeout)
                        i = next
                    }
                    cmd.startsWith("inputText:") -> {
                        val text = unquote(cmd.substringAfter("inputText:").trim())
                        val isPwd = text == "****"
                        actions += RecordedAction.InputText(pkg, text, isPassword = isPwd)
                        i++
                    }
                    cmd.startsWith("tapOn:") || cmd == "tapOn" ||
                        cmd.startsWith("doubleTapOn:") || cmd == "doubleTapOn" ||
                        cmd.startsWith("longPressOn:") || cmd == "longPressOn" -> {
                        val kind = when {
                            cmd.startsWith("doubleTapOn") -> "double"
                            cmd.startsWith("longPressOn") -> "long"
                            else -> "tap"
                        }
                        val hasInlineValue = (cmd.startsWith("tapOn:") ||
                            cmd.startsWith("doubleTapOn:") ||
                            cmd.startsWith("longPressOn:")) &&
                            cmd.substringAfter(":").trim().isNotEmpty()
                        if (hasInlineValue) {
                            val value = unquote(cmd.substringAfter(":").trim())
                            actions += when (kind) {
                                "double" -> RecordedAction.DoubleTap(pkg, text = value)
                                "long" -> RecordedAction.LongPress(pkg, text = value)
                                else -> RecordedAction.Tap(pkg, text = value)
                            }
                            i++
                        } else {
                            val (block, next) = readBlock(lines, i)
                            val id = block["id"]?.let { unquote(it) }
                            val point = block["point"]?.let { unquote(it) }
                            val text = block["text"]?.let { unquote(it) }
                            val (px, py) = parsePoint(point)
                            actions += when (kind) {
                                "double" -> RecordedAction.DoubleTap(
                                    packageName = pkg,
                                    viewId = id,
                                    text = text,
                                    pointPercentX = px,
                                    pointPercentY = py,
                                )
                                "long" -> RecordedAction.LongPress(
                                    packageName = pkg,
                                    viewId = id,
                                    text = text,
                                    pointPercentX = px,
                                    pointPercentY = py,
                                )
                                else -> RecordedAction.Tap(
                                    packageName = pkg,
                                    viewId = id,
                                    text = text,
                                    pointPercentX = px,
                                    pointPercentY = py,
                                )
                            }
                            i = next
                        }
                    }
                    cmd.startsWith("assertVisible:") || cmd == "assertVisible" ||
                        cmd.startsWith("assertNotVisible:") || cmd == "assertNotVisible" -> {
                        val notVisible = cmd.startsWith("assertNotVisible")
                        val hasInline = cmd.contains(":") &&
                            cmd.substringAfter(":").trim().isNotEmpty()
                        if (hasInline) {
                            val value = unquote(cmd.substringAfter(":").trim())
                            actions += if (notVisible) {
                                RecordedAction.AssertNotVisible(pkg, text = value)
                            } else {
                                RecordedAction.AssertVisible(pkg, text = value)
                            }
                            i++
                        } else {
                            val (block, next) = readBlock(lines, i)
                            val id = block["id"]?.let { unquote(it) }
                            val text = block["text"]?.let { unquote(it) }
                            actions += if (notVisible) {
                                RecordedAction.AssertNotVisible(pkg, viewId = id, text = text)
                            } else {
                                RecordedAction.AssertVisible(pkg, viewId = id, text = text)
                            }
                            i = next
                        }
                    }
                    cmd.startsWith("scrollUntilVisible:") || cmd == "scrollUntilVisible" -> {
                        val (block, next) = readBlock(lines, i)
                        val direction = runCatching {
                            ScrollDirection.valueOf(
                                (block["direction"] ?: "DOWN").trim().uppercase(),
                            )
                        }.getOrDefault(ScrollDirection.DOWN)
                        actions += RecordedAction.ScrollUntilVisible(
                            packageName = pkg,
                            visibleId = block["id"]?.let { unquote(it) },
                            visibleText = block["text"]?.let { unquote(it) },
                            direction = direction,
                        )
                        i = next
                    }
                    cmd.startsWith("swipe:") || cmd == "swipe" -> {
                        val (block, next) = readBlock(lines, i)
                        val (sx, sy) = parsePoint(block["start"]?.let { unquote(it) })
                        val (ex, ey) = parsePoint(block["end"]?.let { unquote(it) })
                        actions += RecordedAction.Swipe(
                            packageName = pkg,
                            startPercentX = sx ?: 50f,
                            startPercentY = sy ?: 80f,
                            endPercentX = ex ?: 50f,
                            endPercentY = ey ?: 20f,
                        )
                        i = next
                    }
                    cmd.startsWith("extendedWaitUntil:") || cmd == "extendedWaitUntil" -> {
                        val (block, next) = readBlock(lines, i)
                        val timeout = block["timeout"]?.toLongOrNull() ?: 10_000L
                        val visibleId = block["id"]?.let { unquote(it) }
                        val visibleText = block["text"]?.let { unquote(it) }
                        actions += RecordedAction.Wait(
                            packageName = pkg,
                            timeoutMs = timeout,
                            visibleId = visibleId,
                            visibleText = visibleText,
                        )
                        i = next
                    }
                    else -> {
                        return MaestroImportResult.Failure(
                            "Riga ${i + 1}: comando non supportato «${cmd.take(40)}»",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return MaestroImportResult.Failure("Parse fallito: ${e.message}")
        }
        if (actions.isEmpty()) {
            return MaestroImportResult.Failure("Nessun comando trovato dopo ---")
        }
        if (actions.none { it is RecordedAction.LaunchApp }) {
            actions.add(0, RecordedAction.LaunchApp(pkg))
        }
        return MaestroImportResult.Success(
            appId = pkg,
            name = name?.ifBlank { null } ?: "Import $pkg",
            actions = actions,
            rawYaml = yaml,
        )
    }

    /**
     * Legge un blocco indentato sotto un comando `- foo:`.
     *
     * @return Mappa chiavi piatte (anche sotto `visible:`) e indice riga successivo.
     */
    private fun readBlock(lines: List<String>, startIndex: Int): Pair<Map<String, String>, Int> {
        val map = linkedMapOf<String, String>()
        var i = startIndex + 1
        while (i < lines.size) {
            val raw = lines[i]
            if (raw.trim().isEmpty() || raw.trim().startsWith("#")) {
                i++
                continue
            }
            val indent = raw.takeWhile { it == ' ' }.length
            if (indent == 0 && raw.trim().startsWith("- ")) break
            if (indent < 2 && raw.trim().isNotEmpty() && !raw.trim().startsWith("#")) break
            val trimmed = raw.trim()
            if (trimmed.endsWith(":") && !trimmed.contains(": ")) {
                // chiave nested senza valore (es. visible:)
                i++
                continue
            }
            val key = trimmed.substringBefore(":").trim()
            val value = trimmed.substringAfter(":").trim()
            if (key.isNotBlank()) map[key] = value
            i++
        }
        return map to i
    }

    private fun parsePoint(point: String?): Pair<Float?, Float?> {
        if (point.isNullOrBlank()) return null to null
        val parts = point.replace("%", "").split(",")
        if (parts.size != 2) return null to null
        val x = parts[0].trim().toFloatOrNull()
        val y = parts[1].trim().toFloatOrNull()
        return x to y
    }

    private fun unquote(value: String): String {
        var v = value.trim()
        if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
            v = v.substring(1, v.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return v
    }
}
