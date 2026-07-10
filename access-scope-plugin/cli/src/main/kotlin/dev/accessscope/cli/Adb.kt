package dev.accessscope.cli

import java.io.File
import java.util.concurrent.TimeUnit

class Adb(private val deviceSerial: String? = null) {

    private val adbExecutable = resolveAdbPath()

    fun run(vararg args: String, timeoutSeconds: Long = 120): AdbResult {
        val command = buildList {
            add(adbExecutable)
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

fun resolveAdbPath(): String = defaultAdbPath()

fun defaultAdbPath(): String {
    System.getenv("ACCESS_SCOPE_ADB")?.takeIf { File(it).canExecute() }?.let { return it }

    sdkRootsForAdb().forEach { sdkRoot ->
        adbInPlatformTools(sdkRoot)?.let { return it }
    }

    val fromPath = runCatching {
        val lookup = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            arrayOf("where", "adb")
        } else {
            arrayOf("which", "adb")
        }
        ProcessBuilder(*lookup).start().let { process ->
            process.waitFor(5, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText().trim().lineSequence().firstOrNull().orEmpty()
        }
    }.getOrNull().orEmpty()
    if (fromPath.isNotBlank() && File(fromPath).canExecute()) return fromPath

    return "adb"
}

private fun sdkRootsForAdb(): List<String> = buildList {
    listOf("ACCESS_SCOPE_SDK_ROOT", "ANDROID_SDK_ROOT", "ANDROID_HOME")
        .forEach { key -> System.getenv(key)?.takeIf { it.isNotBlank() }?.let { add(it) } }
    val home = System.getProperty("user.home")
    add("$home/Library/Android/sdk")
    add("$home/Android/Sdk")
    System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { add("$it/Android/Sdk") }
}.distinct()

private fun adbInPlatformTools(sdkRoot: String): String? {
    val root = File(sdkRoot)
    if (!root.isDirectory) return null
    val candidates = listOf(
        File(root, "platform-tools/adb"),
        File(root, "platform-tools/adb.exe"),
    )
    return candidates.firstOrNull { it.canExecute() }?.absolutePath
}

fun ensureAdbAvailable() {
    val adbPath = defaultAdbPath()
    if (adbPath == "adb" || !File(adbPath).canExecute()) {
        error(
            "adb not found. Install Android platform-tools or set ACCESS_SCOPE_SDK_ROOT " +
                "to your Android SDK path (e.g. ~/Library/Android/sdk).",
        )
    }
    runCatching {
        ProcessBuilder(adbPath, "version").start().let { process ->
            process.waitFor(10, TimeUnit.SECONDS)
            if (process.exitValue() != 0) error("adb not available")
        }
    }.getOrElse {
        error(
            "adb not found at $adbPath. Install Android platform-tools or set ACCESS_SCOPE_SDK_ROOT.",
        )
    }
}
