/**
 * Costanti per l'integrazione IDE/plugin con AccessScope.
 */
package dev.accessscope.scanner.bridge

/** Authority del [ScanResultProvider] per query adb/content. */
const val RESULTS_AUTHORITY = "dev.accessscope.scanner.results"

/** Action broadcast locale inviata al termine di una scansione. */
const val ACTION_SCAN_COMPLETE = "dev.accessscope.scanner.SCAN_COMPLETE"

/** Extra broadcast: ID sessione archiviata. */
const val EXTRA_SESSION_ID = "sessionId"

/** Extra broadcast: package target principale. */
const val EXTRA_PACKAGE_NAME = "packageName"

/** Tag logcat per automazione plugin. */
const val BRIDGE_LOG_TAG = "AccessScopeBridge"
