/**
 * Costruzione prompt multimodal per revisione flusso Maestro (Gemini Flash).
 */
package dev.accessscope.scanner.recorder.review

import dev.accessscope.scanner.recorder.ActionJsonCodec
import dev.accessscope.scanner.recorder.RecordedAction
import dev.accessscope.scanner.recorder.model.CompactA11yNode
import dev.accessscope.scanner.recorder.model.FlowTelemetry
import dev.accessscope.scanner.recorder.model.RecordingVisualContext
import dev.accessscope.scanner.recorder.model.TransitionKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Genera testo strutturato + metadati immagini per audit QA Maestro vs draft AccessScope.
 */
object MaestroFlowReviewPromptBuilder {

    private const val MAX_YAML_SYNTAX_CHARS = 8_000
    private const val MAX_SCREENSHOTS = 36
    private const val LONG_FLOW_STEP_THRESHOLD = 30

    /**
     * Template prompt QA (placeholder sostituiti in [build]).
     */
    private const val PROMPT_TEMPLATE = """
Sei un Senior QA Automation Engineer esperto in Maestro. Il tuo incarico è un AUDIT RIGOROSO e la CORREZIONE di un flusso di test generato automaticamente (Sezione B).

ATTENZIONE CRITICA: Il Draft B (Sezione B) è INCOMPLETO. Manca di stabilità temporale (wait mancanti) e usa selettori fragili. SE RESTITUISCI IL DRAFT B IDENTICO A COME TI È STATO DATO, HAI FALLITO IL TASK E IL TEST CRASHERÀ IN PRODUZIONE.

## REGOLE DI CORREZIONE (Devi applicarle rigorosamente step-by-step)
1. INIEZIONE DI STABILITÀ (WAIT): È il difetto più comune del draft. Usa la "Sezione A3" (Telemetria) e le evidenze visive degli Screenshot. Se noti una `ScreenTransition` lenta (es. `deltaMs > 800`) o un `contentChanges` elevato (burst UI che indica loader/caricamenti), DEVI obbligatoriamente inserire un'azione `"type": "WaitForAnimation"` o `"Wait"` subito dopo il Tap che l'ha scatenata.
2. RAFFORZAMENTO SELETTORI: Cerca in B le azioni etichettate con `weakSelector=true` o basate esclusivamente su coordinate (`pointPercentX/Y`). Incrocia l'indice dello step con la "Sezione A4" (Alberi a11y) per trovare il nodo UI corrispondente. Sostituisci le coordinate usando il `viewId` (id) o la `contentDescription` (cd). Gerarchia di affidabilità: 1° id, 2° contentDescription, 3° text.
3. RECUPERO AZIONI PERSE: Se la "Sezione A1/A2" (la registrazione reale) mostra Tap intenzionali che sono stati totalmente rimossi in B, reinseriscili.
4. ROBUSTEZZA POPUP: Se l'azione riguarda un permesso di sistema (es. location), un popup di rating o un overlay non bloccante, imposta l'azione con `"executionMode": "Optional"`.
5. SICUREZZA DATI: Sostituisci sempre i valori reali di password, PIN o OTP con le costanti Maestro (es. `"${'$'}{PASSWORD}"`, `"${'$'}{PIN}"`).

## OTTIMIZZAZIONE ESECUZIONE (obiettivo: Play al primo tentativo senza edit manuale)
- Minimizza step totali SENZA rimuovere tap/input necessari da A1.
- Un solo WaitForAnimation per transizione lenta; no wait duplicati consecutivi.
- Tap submit-like (continua/conferma/accedi) → wait obbligatorio prima del prossimo target.
- Schermate protette: usa A6 + wireframe + albero A4; non saltare input PIN/password.

## IL TUO PIANO D'AZIONE (DIFF CRITICO)
{CHUNK_HEADER}
Il sistema ha pre-calcolato le discrepanze. DEVI risolvere attivamente questi alert generando modifiche per ciascuno di essi:
- Step grezzi in A: {RAW_COUNT} | Step ottimizzati in B: {OPTIMIZED_COUNT}
- Tap presenti in REC ma mancanti in B: {MISSING_TAPS}
- Indici con transizioni lente (RICHIEDONO WAIT): {LONG_TRANSITIONS}
- Indici con selettori deboli (RICHIEDONO FIX DA A4): {WEAK_STEPS}

==== SEZIONE A: VERITÀ ASSOLUTA ====
appId: {APP_ID}
flowName: {FLOW_NAME}

[A1] Azioni grezze REC (JSON - Fonte primaria per azioni mancanti):
{RAW_ACTIONS_JSON}

[A2] Timeline per step:
{TIMELINE_TXT}

[A3] Telemetria transizioni (Usa per determinare dove mancano i Wait):
{TELEMETRY_TXT}

[A4] Alberi a11y compatti (Usa per trovare viewId robusti):
{A11Y_TREES_TXT}

[A6] Transcript semantico per step (obbligatorio su schermate protette / screenshot nero):
{A6_TRANSCRIPT_TXT}

{EXTRA_SECTIONS_A5_B2_B3}

==== SEZIONE B: DRAFT DA CORREGGERE (Sospetto, da modificare) ====
[B1] Azioni ottimizzate grezze (JSON):
{OPTIMIZED_ACTIONS_JSON}

==== SEZIONE C: SCREENSHOT ====
Indici step con JPEG allegati: {SCREENSHOT_INDICES}
(Usali come conferma visiva: se uno step ha uno screenshot, verifica attentamente se serve un Wait per un caricamento o se l'elemento è un popup).

## FORMATO DI RISPOSTA (SOLO JSON VALIDO)
Devi restituire un singolo oggetto JSON.
REGOLE JSON:
1. Inserisci la chiave `changes` PER PRIMA. Questo ti serve per dichiarare le anomalie che hai trovato prima di scrivere le azioni. L'array `changes` NON DEVE MAI ESSERE VUOTO (B non è mai perfetto).
2. Poi, inserisci la chiave `corrected_actions` con l'array completo e corretto nel formato `ActionJsonCodec`.

Modello di risposta atteso:
{
  "changes": [
    {
      "stepIndex": 2,
      "code": "INSERT_WAIT",
      "message": "Rilevata transizione di 1200ms in telemetria (A3). Aggiunto WaitForAnimation per stabilizzare il rendering."
    },
    {
      "stepIndex": 5,
      "code": "FIX_SELECTOR",
      "message": "Il draft B usava un weakSelector a coordinate. Sostituito con viewId 'com.app:id/btn_login' trovato nell'albero a11y (A4)."
    }
  ],
  "corrected_actions": [
  ]
}
"""

    /**
     * Parti testuali del prompt (prima parte del payload Gemini).
     *
     * @param text Prompt principale in italiano.
     * @param imageStepIndices Indici azione per cui inviare JPEG inline (ordine immagini).
     */
    data class PromptParts(
        val text: String,
        val imageStepIndices: List<Int>,
    )

    /**
     * Costruisce prompt e indici screenshot da allegare.
     *
     * @param request Contesto completo registrazione + draft.
     * @return [PromptParts] pronti per [GeminiFlashFlowReviewer].
     */
    fun build(request: FlowReviewRequest): PromptParts {
        val diff = FlowReviewDiffAnalyzer.analyze(
            request.rawActions,
            request.optimizedActions,
            request.telemetry,
            request.visualContext,
        )
        val maxImages = request.budget?.maxImagesPerCall ?: MAX_SCREENSHOTS
        val maxYaml = request.budget?.maxYamlSyntaxChars ?: MAX_YAML_SYNTAX_CHARS
        val imageIndices = selectScreenshotIndices(request, diff, maxImages)
        val criticalTreeIndices = buildCriticalTreeIndices(diff, imageIndices, request.rawActions.size)
        val chunkHeader = request.chunk?.let { c ->
            "Stai correggendo SOLO gli step ${c.fromActionIndex}–${c.toActionIndexInclusive} " +
                "del flusso completo (${request.rawActions.size} step in questo chunk). " +
                "Chunk ${c.chunkIndex + 1}/${c.totalChunks}."
        }.orEmpty()
        val text = PROMPT_TEMPLATE
            .replace("{CHUNK_HEADER}", chunkHeader)
            .replace("{RAW_COUNT}", diff.rawCount.toString())
            .replace("{OPTIMIZED_COUNT}", diff.optimizedCount.toString())
            .replace("{MISSING_TAPS}", FlowReviewDiffAnalyzer.formatList(diff.missingTapLabels))
            .replace("{LONG_TRANSITIONS}", FlowReviewDiffAnalyzer.formatList(diff.longTransitions))
            .replace("{WEAK_STEPS}", FlowReviewDiffAnalyzer.formatList(diff.weakSelectorSteps))
            .replace("{APP_ID}", request.appId)
            .replace("{FLOW_NAME}", request.flowName)
            .replace("{RAW_ACTIONS_JSON}", ActionJsonCodec.toJson(request.rawActions))
            .replace("{TIMELINE_TXT}", buildTimelineText(request.rawActions, request.telemetry, request.visualContext))
            .replace("{TELEMETRY_TXT}", buildTelemetryText(request.telemetry, request.visualContext))
            .replace(
                "{A11Y_TREES_TXT}",
                buildA11yTreesText(request.visualContext, criticalTreeIndices, request.rawActions.size),
            )
            .replace("{A6_TRANSCRIPT_TXT}", buildTranscriptText(request.visualContext))
            .replace("{EXTRA_SECTIONS_A5_B2_B3}", buildExtraSections(request, maxYaml))
            .replace("{OPTIMIZED_ACTIONS_JSON}", ActionJsonCodec.toJson(request.optimizedActions))
            .replace("{SCREENSHOT_INDICES}", imageIndices.joinToString(", ").ifBlank { "(nessuno)" })
            .trim()
        val a0Block = buildString {
            appendLine("[A0] Step persi da B (DEVI reinserirli):")
            appendLine(request.lostStepsSummary ?: diff.lostStepsSummary)
        }
        return PromptParts(text = a0Block + "\n" + text, imageStepIndices = imageIndices)
    }

    private fun buildTranscriptText(visual: RecordingVisualContext?): String {
        if (visual == null) return "(nessun transcript)"
        return visual.snapshots.sortedBy { it.actionIndex }.joinToString("\n") { snap ->
            "step ${snap.actionIndex} protection=${snap.protectionReason.name}: ${snap.semanticTranscript}"
        }.ifBlank { "(nessun transcript)" }
    }

    private fun buildExtraSections(request: FlowReviewRequest, maxYaml: Int): String = buildString {
        if (request.scanIntel != null && request.scanIntel.screens.isNotEmpty()) {
            appendLine("[A5] Scan intelligence WCAG (viewId noti da scan precedente):")
            appendLine("mainPath: ${request.scanIntel.mainPathFingerprints.take(20).joinToString()}")
            appendLine("hotViewIds: ${request.scanIntel.elements.keys.take(40).joinToString()}")
            appendLine()
        }
        if (!request.draftLintSummary.isNullOrBlank()) {
            appendLine("[B2] Lint draft AccessScope (difetti già rilevati — devi correggerli):")
            appendLine(request.draftLintSummary.trim())
            appendLine()
        }
        val yamlRef = request.yamlSyntaxReference?.take(maxYaml)?.trim().orEmpty()
        if (yamlRef.isNotEmpty()) {
            appendLine("[B3] YAML Maestro (solo riferimento sintassi comandi, NON fonte di verità):")
            appendLine(yamlRef)
            appendLine()
        }
    }.toString()

    private fun buildCriticalTreeIndices(
        diff: FlowReviewDiffReport,
        imageIndices: List<Int>,
        rawStepCount: Int,
    ): Set<Int> = buildSet {
        addAll(diff.longTransitions)
        addAll(diff.weakSelectorSteps)
        addAll(imageIndices)
        if (rawStepCount <= LONG_FLOW_STEP_THRESHOLD) {
            (0 until rawStepCount).forEach { add(it) }
        }
    }

    /**
     * Sceglie screenshot più informativi (transizioni, tap deboli, input) entro limite API.
     */
    private fun selectScreenshotIndices(
        request: FlowReviewRequest,
        diff: FlowReviewDiffReport,
        maxImages: Int,
    ): List<Int> {
        val withImage = request.visualContext?.snapshots
            ?.filter { it.bestImageBytes() != null }
            ?.map { it.actionIndex }
            ?.toSet()
            .orEmpty()
        if (withImage.isEmpty()) return emptyList()
        val priority = linkedSetOf<Int>()
        request.visualContext?.snapshots?.filter {
            it.protectionReason.name != "NONE" && it.actionIndex in withImage
        }?.map { it.actionIndex }?.forEach { priority += it }
        diff.longTransitions.filter { it in withImage }.forEach { priority += it }
        diff.weakSelectorSteps.filter { it in withImage }.forEach { priority += it }
        request.telemetry?.transitions
            ?.filter { it.kind == TransitionKind.ScreenTransition && it.deltaMs >= 800 }
            ?.map { it.toIndex }
            ?.filter { it in withImage }
            ?.forEach { priority += it }
        request.rawActions.forEachIndexed { index, action ->
            if (index !in withImage) return@forEachIndexed
            when (action) {
                is RecordedAction.InputText -> priority += index
                is RecordedAction.Tap -> if (action.weakSelector) priority += index
                else -> Unit
            }
        }
        withImage.sorted().forEach { if (priority.size < maxImages) priority += it }
        return priority.take(maxImages).sorted()
    }

    private fun buildTimelineText(
        actions: List<RecordedAction>,
        telemetry: FlowTelemetry?,
        visual: RecordingVisualContext?,
    ): String = buildString {
        actions.forEachIndexed { index, action ->
            val delta = if (index == 0) 0L else action.timestampMs - actions[index - 1].timestampMs
            val transition = telemetry?.transitions?.find { it.toIndex == index }
            val kind = transition?.kind?.name ?: "?"
            val snap = visual?.snapshots?.find { it.actionIndex == index }
            val contentChanges = visual?.contentChangeCountPerGap?.getOrNull(index - 1)
            appendLine(
                "step $index: ${action::class.simpleName} pkg=${action.packageName} " +
                    "deltaMs=$delta transition=$kind " +
                    "contentChanges=${contentChanges ?: "-"} " +
                    "title=${snap?.windowTitle ?: "-"} secure=${snap?.secureWindow == true} " +
                    summarizeAction(action),
            )
        }
    }.trimEnd()

    private fun summarizeAction(action: RecordedAction): String = when (action) {
        is RecordedAction.Tap ->
            "tap id=${action.viewId} text=${action.text} cd=${action.contentDescription} " +
                "point=${action.pointPercentX},${action.pointPercentY} optional=${action.executionMode.name} weak=${action.weakSelector}"
        is RecordedAction.InputText ->
            "input id=${action.viewId} pwd=${action.isPassword} len=${action.text.length}"
        is RecordedAction.Scroll -> "scroll ${action.direction}"
        is RecordedAction.Wait -> "wait ${action.timeoutMs}ms target=${action.visibleId ?: action.visibleText}"
        is RecordedAction.AssertVisible -> "assert visible ${action.viewId ?: action.text}"
        else -> action::class.simpleName.orEmpty()
    }

    private fun buildTelemetryText(
        telemetry: FlowTelemetry?,
        visual: RecordingVisualContext?,
    ): String = buildString {
        if (telemetry == null) {
            appendLine("(nessuna telemetria)")
            return@buildString
        }
        telemetry.transitions.forEach { t ->
            appendLine(
                "transition ${t.fromIndex}->${t.toIndex} kind=${t.kind.name} deltaMs=${t.deltaMs} " +
                    "fp ${t.fromFingerprint?.take(12)} -> ${t.toFingerprint?.take(12)}",
            )
        }
        telemetry.quiescenceGaps.forEach { g ->
            val cc = visual?.contentChangeCountPerGap?.getOrNull(g.afterActionIndex) ?: g.contentChangeCount
            appendLine(
                "quiescence after=${g.afterActionIndex} quietMs=${g.quietMs} burstMs=${g.contentBurstMs} contentChanges=$cc",
            )
        }
        telemetry.snapshots.forEach { s ->
            appendLine("snapshot idx=${s.actionIndex} fp=${s.fingerprint.take(16)} title=${s.title}")
        }
    }.trimEnd()

    private fun buildA11yTreesText(
        visual: RecordingVisualContext?,
        criticalIndices: Set<Int>,
        rawStepCount: Int,
    ): String {
        if (visual == null) return "(nessun albero a11y)"
        val longFlow = rawStepCount > LONG_FLOW_STEP_THRESHOLD
        return buildString {
            visual.snapshots.sortedBy { it.actionIndex }.forEach { snap ->
                if (longFlow && snap.actionIndex !in criticalIndices) {
                    appendLine(
                        "step ${snap.actionIndex}: summary nodes=${snap.treeSummary.size} " +
                            "title=${snap.windowTitle ?: "-"} secure=${snap.secureWindow}",
                    )
                    return@forEach
                }
                appendLine("--- tree step ${snap.actionIndex} (nodes=${snap.treeSummary.size}) ---")
                snap.treeSummary.forEach { node ->
                    appendLine(formatNode(node))
                }
            }
        }.trimEnd().ifBlank { "(nessun albero a11y)" }
    }

    private fun formatNode(node: CompactA11yNode): String {
        val flags = buildList {
            if (node.clickable) add("click")
            if (node.editable) add("edit")
            if (node.password) add("pwd")
            node.checked?.let { add(if (it) "checked" else "unchecked") }
        }.joinToString(",")
        return "  d${node.depth} id=${node.viewId} text=${node.text} cd=${node.contentDescription} " +
            "cls=${node.className} role=${node.role} bounds=${node.boundsPx} [$flags]"
    }

    /** Schema JSON atteso in risposta (documentazione / test). */
    fun responseSchemaHint(): JSONObject = JSONObject().apply {
        put(
            "changes",
            JSONArray().put(
                JSONObject().apply {
                    put("stepIndex", 0)
                    put("code", "INSERT_WAIT")
                    put("message", "Aggiunto waitForAnimationToEnd dopo tap submit")
                },
            ),
        )
        put(
            "corrected_actions",
            JSONArray().put(
                JSONObject().apply {
                    put("type", "Tap")
                    put("packageName", "com.example.app")
                    put("timestampMs", 0L)
                },
            ),
        )
    }
}
