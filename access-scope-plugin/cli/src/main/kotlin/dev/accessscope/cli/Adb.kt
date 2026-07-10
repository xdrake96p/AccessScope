package dev.accessscope.cli

import java.io.File
import java.util.concurrent.TimeUnit

class Adb(private val deviceSerial: String? = null) {

    fun run(vararg args: String, timeoutSeconds: Long = 120): AdbResult {
        val command = buildList {
            add("adb")
            if (!deviceSerial.isNullOrBlank()) {
                add("-s")
                add(deviceSerial)
            }
            addAll(args)
        }
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            error("adb command timed out: ${command.joinToString(" ")}")
        }
        val output = process.inputStream.bufferedReader().readText()
        if (process.exitValue() != 0) {
            error("adb failed (${process.exitValue()}): ${command.joinToString(" ")}\n$output")
        }
        return AdbResult(output.trim(), process.exitValue())
    }

    fun runOrNull(vararg args: String): String? =
        runCatching { run(*args).output }.getOrNull()
}

data class AdbResult(val output: String, val exitCode: Int)

fun defaultAdbPath(): String {
    val fromPath = runCatching {
        ProcessBuilder("which", "adb").start().let { p ->
            p.waitFor()
            p.inputStream.bufferedReader().readText().trim()
        }
    }.getOrNull().orEmpty()
    if (fromPath.isNotBlank() && File(fromPath).canExecute()) return fromPath

    val home = System.getProperty("user.home")
    val candidates = listOf(
        "$home/Library/Android/sdk/platform-tools/adb",
        "$home/Android/Sdk/platform-tools/adb",
    )
    return candidates.firstOrNull { File(it).canExecute() } ?: "adb"
}

fun ensureAdbAvailable() {
    defaultAdbPath()
    runCatching {
        ProcessBuilder(defaultAdbPath(), "version").start().let { p ->
            p.waitFor(10, TimeUnit.SECONDS)
            if (p.exitValue() != 0) error("adb not available")
        }
    }.getOrElse {
        error("adb not found in PATH or Android SDK. Install Android platform-tools.")
    }
}
