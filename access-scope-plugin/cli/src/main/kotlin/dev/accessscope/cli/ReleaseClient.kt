package dev.accessscope.cli

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class ReleaseManifest(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("apkFileName") val apkFileName: String?,
    @SerializedName("apkUrl") val apkUrl: String?,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("minPluginVersion") val minPluginVersion: String?,
)

data class GitHubReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
)

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val assets: List<GitHubReleaseAsset>,
)

class ReleaseClient(
    private val githubRepo: String = "xdrake96p/AccessScope",
    private val cacheDir: File = File(System.getProperty("user.home"), ".access-scope/cache"),
) {
    private val gson = Gson()

    init {
        cacheDir.mkdirs()
    }

    fun fetchLatestManifest(): ReleaseManifest {
        val release = fetchLatestRelease()
        val manifestAsset = release.assets.firstOrNull { it.name == "release-manifest.json" }
            ?: error("release-manifest.json not found in latest GitHub release")
        val manifestJson = downloadText(manifestAsset.browserDownloadUrl)
        val manifest = gson.fromJson(manifestJson, ReleaseManifest::class.java)
        val apkAsset = release.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk") &&
                (manifest.apkFileName == null || asset.name == manifest.apkFileName)
        } ?: error("APK asset not found in latest GitHub release")
        return manifest.copy(
            apkFileName = apkAsset.name,
            apkUrl = apkAsset.browserDownloadUrl,
        )
    }

    fun downloadApk(manifest: ReleaseManifest): File {
        val url = manifest.apkUrl ?: error("APK URL missing in manifest")
        val target = File(cacheDir, manifest.apkFileName ?: "access-scope.apk")
        if (target.exists()) {
            val existingHash = sha256(target)
            if (existingHash.equals(manifest.sha256, ignoreCase = true)) return target
        }
        downloadFile(url, target)
        val hash = sha256(target)
        if (!hash.equals(manifest.sha256, ignoreCase = true)) {
            target.delete()
            error("SHA256 mismatch for downloaded APK (expected ${manifest.sha256}, got $hash)")
        }
        return target
    }

    private fun fetchLatestRelease(): GitHubRelease {
        val url = "https://api.github.com/repos/$githubRepo/releases/latest"
        val json = downloadText(url)
        return gson.fromJson(json, GitHubRelease::class.java)
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
        connection.setRequestProperty("User-Agent", "access-scope-cli")
        val token = System.getenv("GITHUB_TOKEN")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode} for $url")
        }
        return connection
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
