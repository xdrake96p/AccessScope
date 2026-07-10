package dev.accessscope.plugin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

data class DeviceInfo(
    val serial: String,
    val model: String,
    val state: String,
    val isEmulator: Boolean,
)

@Service(Service.Level.APP)
class CliExecutor {
    private val log = Logger.getInstance(CliExecutor::class.java)
    private val gson = Gson()

    fun run(vararg args: String): String {
        val javaPath = resolveJavaPath()
        val jarPath = resolveJarPath()
        val command = listOf(javaPath, "-jar", jarPath, *args)
        log.info("Running CLI: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val completed = process.waitFor(10, TimeUnit.MINUTES)
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
        if (!completed) {
            process.destroyForcibly()
            error("CLI timed out")
        }
        if (process.exitValue() != 0) {
            error(output.ifBlank { "CLI failed with exit code ${process.exitValue()}" })
        }
        return output
    }

    fun listDevices(): List<DeviceInfo> {
        val type = object : TypeToken<List<DeviceInfo>>() {}.type
        return gson.fromJson(run("devices", "list"), type)
    }

    fun install(deviceSerial: String): String = run("install", "--device", deviceSerial)

    fun launch(deviceSerial: String): String = run("launch", "--device", deviceSerial)

    fun fetchResults(deviceSerial: String, packageName: String?): String {
        val args = mutableListOf("fetch-results", "--device", deviceSerial)
        if (!packageName.isNullOrBlank()) {
            args += listOf("--package", packageName)
        }
        return run(*args.toTypedArray())
    }

    fun setupCheck(deviceSerial: String): String = run("setup-check", "--device", deviceSerial)

    private fun resolveJavaPath(): String {
        val studioJava = "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"
        if (SystemInfo.isMac && File(studioJava).exists()) return studioJava
        return "java"
    }

    private fun resolveJarPath(): String {
        resolveBundledJarFromPluginDir()?.let { return it }
        resolveBundledJarNearPluginClasses()?.let { return it }
        resolveDevJar()?.let { return it }
        return extractCliJarFromResources()
    }

    private fun resolveBundledJarFromPluginDir(): String? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("dev.accessscope.plugin")) ?: return null
        val candidates = listOf(
            plugin.pluginPath.resolve("lib").resolve("AccessScope-cli.jar"),
            plugin.pluginPath.resolve("lib").resolve("access-scope-cli.jar"),
            plugin.pluginPath.resolve("AccessScope-cli.jar"),
        )
        return candidates.firstOrNull { Files.exists(it) }?.toAbsolutePath()?.toString()
    }

    private fun resolveBundledJarNearPluginClasses(): String? {
        val codeSource = CliExecutor::class.java.protectionDomain?.codeSource?.location ?: return null
        val sourceFile = runCatching { File(codeSource.toURI()) }.getOrNull() ?: return null
        val libDir = sourceFile.parentFile ?: return null
        val candidates = listOf(
            File(libDir, "AccessScope-cli.jar"),
            File(libDir, "access-scope-cli.jar"),
            File(libDir.parentFile, "lib/AccessScope-cli.jar"),
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }

    private fun resolveDevJar(): String? {
        val devJar = File(PathResolver.repoRoot(), "cli/build/libs/cli-1.0.0-all.jar")
        return devJar.takeIf { it.exists() }?.absolutePath
    }

    private fun extractCliJarFromResources(): String {
        val cacheDir = File(PathManager.getSystemPath(), "accessscope")
        cacheDir.mkdirs()
        val cachedJar = File(cacheDir, "AccessScope-cli.jar")
        if (cachedJar.exists() && cachedJar.length() > 0L) {
            return cachedJar.absolutePath
        }
        val resourceNames = listOf("AccessScope-cli.jar", "access-scope-cli.jar")
        val stream = resourceNames.firstNotNullOfOrNull { name ->
            CliExecutor::class.java.classLoader.getResourceAsStream(name)
        } ?: error(
            "AccessScope-cli.jar not found. Reinstall the plugin from the latest AccessScope release ZIP.",
        )
        stream.use { input ->
            cachedJar.outputStream().use { output -> input.copyTo(output) }
        }
        log.info("Extracted CLI jar to ${cachedJar.absolutePath}")
        return cachedJar.absolutePath
    }
}

object PathResolver {
    fun repoRoot(): File = File(System.getProperty("user.dir"))
}

object GradlePackageDetector {
    fun detectTargetPackage(project: Project): String? {
        val gradleFile = sequenceOf(
            File(project.basePath, "app/build.gradle"),
            File(project.basePath, "app/build.gradle.kts"),
        ).firstOrNull { it.exists() } ?: return null

        val text = gradleFile.readText()
        val baseId = Regex("""applicationId\s*[= ]\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
            ?: Regex("""applicationId\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
            ?: return null

        val suffix = Regex("""applicationIdSuffix\s*[= ]\s*'([^']+)'""")
            .findAll(text)
            .map { it.groupValues[1] }
            .lastOrNull()
            ?: Regex("""applicationIdSuffix\s*=\s*"([^"]+)"""")
                .findAll(text)
                .map { it.groupValues[1] }
                .lastOrNull()

        return if (suffix.isNullOrBlank()) baseId else "$baseId$suffix"
    }
}

fun runOnBackground(block: () -> Unit) {
    ApplicationManager.getApplication().executeOnPooledThread(block)
}

fun parseJsonObject(raw: String): JsonObject = Gson().fromJson(raw, JsonObject::class.java)
