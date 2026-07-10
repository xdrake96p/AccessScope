/**
 * Serializzazione JSON ridotta per il bridge IDE — senza path interni né testo UI sensibile.
 */
package dev.accessscope.scanner.bridge

import dev.accessscope.scanner.data.AccessibilityViolation
import dev.accessscope.scanner.data.ArchivedScanSession
import dev.accessscope.scanner.data.ScreenReaderFinding
import dev.accessscope.scanner.data.VisitedScreen
import org.json.JSONArray
import org.json.JSONObject

object BridgeSessionJson {

    fun sessionToJson(session: ArchivedScanSession): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("completedAtMs", session.completedAtMs)
        put("targetPackages", JSONArray(session.targetPackages.toList()))
        put("violations", JSONArray(session.violations.map { violationToJson(it) }))
        put("screenReaderFindings", JSONArray(session.screenReaderFindings.map { findingToJson(it) }))
        put("uniqueScreens", session.uniqueScreens)
        put("scanAnalyses", session.scanAnalyses)
        put("scanScopeLabel", session.scanScopeLabel)
        put("score", session.score)
        put("pdfPath", session.pdfPath)
        put("violationKeys", JSONArray(session.violationKeys.toList()))
        put("visitedScreens", JSONArray(session.visitedScreens.map { visitedScreenToJson(it) }))
    }

    private fun violationToJson(v: AccessibilityViolation): JSONObject = JSONObject().apply {
        put("type", v.type.name)
        put("severity", v.type.severity.name)
        put("viewClassName", v.viewClassName)
        put("screenTitle", v.screenTitle)
        put("packageName", v.packageName)
        put("details", v.details)
        put("viewId", v.viewId)
        put("bounds", v.bounds)
        put("sectionTitle", v.sectionTitle)
        put("confidence", v.confidence.toDouble())
        put("screenFingerprint", v.screenFingerprint)
        put("measuredValue", v.measuredValue)
        put("requiredValue", v.requiredValue)
        put("remediation", v.remediation)
        put("evidenceKind", v.evidenceKind.name)
    }

    private fun findingToJson(f: ScreenReaderFinding): JSONObject = JSONObject().apply {
        put("packageName", f.packageName)
        put("screenTitle", f.screenTitle)
        put("nodeClassName", f.nodeClassName)
        put("issue", f.issue)
        put("viewId", f.viewId)
        put("sectionTitle", f.sectionTitle)
    }

    private fun visitedScreenToJson(screen: VisitedScreen): JSONObject = JSONObject().apply {
        put("fingerprint", screen.fingerprint)
        put("title", screen.title)
        put("visitIndex", screen.visitIndex)
        put("protectionReason", screen.protectionReason.name)
    }
}
