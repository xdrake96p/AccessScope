package dev.accessscope.plugin

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

private const val PLUGIN_ID = "dev.accessscope.plugin"
private const val GITHUB_REPO = "xdrake96p/AccessScope"
private const val RELEASES_PATH = "access-scope-plugin/releases"
private const val RELEASE_BRANCH = "develop"

sealed class PluginUpdateResult {
    data class UpToDate(val version: String) : PluginUpdateResult()
    data class Installed(val version: String) : PluginUpdateResult()
}

@Service(Service.Level.APP)
class PluginUpdateChecker {
    private val log = Logger.getInstance(PluginUpdateChecker::class.java)
    private val gson = Gson()

    fun checkAndInstall(onProgress: (String) -> Unit): PluginUpdateResult {
        val current = currentVersion()
        onProgress("Current plugin version: $current")

        val latest = fetchLatestVersion()
        onProgress("Latest version available: $latest")

        if (compareVersions(latest, current) <= 0) {
            return PluginUpdateResult.UpToDate(current)
        }

        onProgress("Downloading AccessScope-$latest.zip...")
        val zip = downloadPluginZip(latest)
        onProgress("Installing update...")
        installPluginZip(zip)
        onProgress("Update to v$latest ready. Restarting IDE...")
        return PluginUpdateResult.Installed(latest)
    }

    fun currentVersion(): String =
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "0.0.0"

    private fun fetchLatestVersion(): String {
        val url = "https://api.github.com/repos/$GITHUB_REPO/contents/$RELEASES_PATH?ref=$RELEASE_BRANCH"
        val json = downloadText(url)
        val entries = gson.fromJson(json, Array<GitHubContentEntry>::class.java)
        return entries
            .asSequence()
            .filter { it.type == "dir" && VERSION_DIR.matches(it.name) }
            .map { it.name }
            .maxWithOrNull(::compareVersions)
            ?: error("No plugin release found on GitHub ($RELEASES_PATH).")
    }

    private fun downloadPluginZip(version: String): File {
        val fileName = "AccessScope-$version.zip"
        val url =
            "https://github.com/$GITHUB_REPO/raw/$RELEASE_BRANCH/$RELEASES_PATH/$version/$fileName"
        val target = Files.createTempFile("accessscope-plugin-", "-$version.zip").toFile()
        target.deleteOnExit()
        downloadFile(url, target)
        if (!target.exists() || target.length() == 0L) {
            error("Download failed for $fileName")
        }
        return target
    }

    private fun installPluginZip(zip: File) {
        val pluginId = PluginId.getId(PLUGIN_ID)
        val existing = PluginManagerCore.getPlugin(pluginId)
        val zipPath = zip.toPath()
        val descriptor = loadDescriptorFromZip(zipPath, pluginId)
        val oldPath = if (existing != null && !existing.isBundled) existing.pluginPath else null
        PluginInstaller.installAfterRestart(
            descriptor,
            zipPath,
            oldPath,
            true,
        )
        InstalledPluginsState.getInstance().onPluginInstall(descriptor, existing != null, true)
        log.info("Scheduled plugin update to ${descriptor.version} from ${zip.absolutePath}")
    }

    private fun loadDescriptorFromZip(zipPath: Path, pluginId: PluginId): IdeaPluginDescriptor {
        val loaderClass = Class.forName("com.intellij.ide.plugins.PluginDescriptorLoader")
        val loadMethod = loaderClass.getMethod(
            "loadDescriptorFromArtifact",
            Path::class.java,
            Class.forName("com.intellij.ide.plugins.PluginXmlPathResolver"),
        )
        val descriptor = loadMethod.invoke(null, zipPath, null) as? IdeaPluginDescriptor
            ?: error("Invalid plugin ZIP.")
        if (descriptor.pluginId != pluginId) {
            error("Invalid plugin ZIP: expected $PLUGIN_ID, found ${descriptor.pluginId.idString}.")
        }
        return descriptor
    }

    private fun downloadText(url: String): String {
        val connection = openConnection(url)
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadFile(url: String, target: File) {
        val connection = openConnection(url)
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "accessscope-plugin")
        val token = System.getenv("GITHUB_TOKEN")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            error(
                when (connection.responseCode) {
                    403 -> "GitHub API rate limit. Retry in a few minutes or set GITHUB_TOKEN."
                    404 -> "Plugin release not found on GitHub."
                    else -> "HTTP ${connection.responseCode} downloading $url"
                },
            )
        }
        return connection
    }

    private data class GitHubContentEntry(
        val name: String,
        val type: String,
        @SerializedName("download_url") val downloadUrl: String?,
    )

    companion object {
        private val VERSION_DIR = Regex("""\d+\.\d+\.\d+""")

        fun compareVersions(left: String, right: String): Int {
            val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
            val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
            val max = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until max) {
                val diff = (leftParts.getOrElse(index) { 0 }) - (rightParts.getOrElse(index) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
