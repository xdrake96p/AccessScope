/**
 * Logger su file per diagnostica e analisi crash post-mortem.
 *
 * Mantiene anche un buffer in memoria esposto via [liveEntries] per il log checker in tempo reale.
 */
package dev.accessscope.scanner.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Singola riga di log per UI e export. */
data class LogEntry(
    val timestampMs: Long,
    val level: String,
    val tag: String,
    val message: String,
) {
    fun formatLine(): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
        return "$ts $level/$tag: $message"
    }
}

/**
 * Scrive log strutturati su file interno con rotazione semplice.
 * Thread-safe; flush immediato su errori critici.
 */
object AppFileLogger {

    private const val TAG = "AccessScope"
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "accessscope.log"
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_LIVE_ENTRIES = 500

    private val lock = ReentrantLock()
    private var logFile: File? = null
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _liveEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    /** Flusso osservabile per il log checker (aggiornamento in tempo reale). */
    val liveEntries: StateFlow<List<LogEntry>> = _liveEntries.asStateFlow()

    /** Inizializza il percorso del file di log (chiamare da [android.app.Application.onCreate]). */
    fun init(context: Context) {
        val dir = File(context.filesDir, LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, LOG_FILE)
        preloadFromDisk()
    }

    fun info(tag: String, message: String) {
        write("I", tag, message)
        Log.i(TAG, "[$tag] $message")
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        write("E", tag, full)
        Log.e(TAG, "[$tag] $message", throwable)
    }

    /**
     * Compatibilità con il vecchio [DebugTrace]: serializza i dati in una riga leggibile.
     */
    fun log(hypothesisId: String, location: String, message: String, data: Map<String, Any?> = emptyMap()) {
        val dataStr = if (data.isEmpty()) "" else " data=$data"
        info(location, "[$hypothesisId] $message$dataStr")
    }

    /** Svuota il buffer in memoria (non cancella i file su disco). */
    fun clearLiveBuffer() {
        _liveEntries.value = emptyList()
    }

    /** Ricarica le ultime righe dai file di log su disco nel buffer live. */
    fun preloadFromDisk(maxLines: Int = MAX_LIVE_ENTRIES) {
        val parsed = allLogFiles()
            .flatMap { file -> parseFileLines(file.readLines(Charsets.UTF_8)) }
            .takeLast(maxLines)
        if (parsed.isNotEmpty()) {
            _liveEntries.value = parsed
        }
    }

    /** Testo completo per export o anteprima. */
    fun liveText(): String = _liveEntries.value.joinToString("\n") { it.formatLine() }

    /**
     * Righe di log dal buffer live filtrate per tempo e tag (diagnostica sessione Maestro).
     *
     * @param sinceMs Timestamp minimo inclusivo.
     * @param tags Tag ammessi; `null` = tutti.
     */
    fun entriesSince(sinceMs: Long, tags: Set<String>? = null): List<LogEntry> =
        _liveEntries.value.filter { entry ->
            entry.timestampMs >= sinceMs && (tags == null || entry.tag in tags)
        }

    /** Restituisce il file di log corrente, se inizializzato. */
    fun currentLogFile(): File? = logFile?.takeIf { it.exists() && it.length() > 0 }

    /** Elenco di tutti i file di log (corrente + eventuali rotati). */
    fun allLogFiles(): List<File> {
        val dir = logFile?.parentFile ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("accessscope") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    private fun write(level: String, tag: String, message: String) {
        val now = System.currentTimeMillis()
        appendLive(LogEntry(now, level, tag, message))
        val file = logFile ?: return
        lock.withLock {
            runCatching {
                rotateIfNeeded(file)
                val line = "${timestampFormat.format(Date(now))} $level/$tag: $message\n"
                file.appendText(line, Charsets.UTF_8)
            }
        }
    }

    private fun appendLive(entry: LogEntry) {
        _liveEntries.update { current ->
            (current + entry).takeLast(MAX_LIVE_ENTRIES)
        }
    }

    private fun parseFileLines(lines: List<String>): List<LogEntry> {
        val regex = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) ([IE])/([^:]+): (.*)$""")
        return lines.mapNotNull { line ->
            val m = regex.matchEntire(line.trim()) ?: return@mapNotNull null
            val ts = runCatching {
                timestampFormat.parse(m.groupValues[1])?.time ?: System.currentTimeMillis()
            }.getOrDefault(System.currentTimeMillis())
            LogEntry(ts, m.groupValues[2], m.groupValues[3], m.groupValues[4])
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (file.length() < MAX_BYTES) return
        val rotated = File(file.parentFile, "accessscope_${System.currentTimeMillis()}.log")
        file.renameTo(rotated)
    }
}
