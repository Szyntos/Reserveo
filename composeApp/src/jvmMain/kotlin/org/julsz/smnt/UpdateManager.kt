package org.julsz.smnt

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.URI

private const val GITHUB_REPO_OWNER = "Szyntos"
private const val GITHUB_REPO_NAME = "Reserveo"

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo, val manualOnly: Boolean) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

@Serializable
private data class GithubReleaseAsset(val name: String, val browser_download_url: String)

@Serializable
private data class GithubRelease(
    val tag_name: String,
    val html_url: String,
    val body: String? = null,
    val published_at: String? = null,
    val assets: List<GithubReleaseAsset> = emptyList()
)

// Separate from the client used to talk to the Reserveo server — that one injects the
// Basic-auth Authorization header via DefaultRequest, which must never be sent to GitHub.
fun createGithubHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private fun parseVersionCode(tagName: String): Int? =
    // Tag convention: v<versionCode>-<versionName>, e.g. "v7-1.3.0"
    tagName.removePrefix("v").substringBefore("-").toIntOrNull()

private fun isWindows(): Boolean =
    System.getProperty("os.name")?.lowercase()?.contains("win") == true

suspend fun checkForUpdate(githubClient: HttpClient, currentVersionCode: Int): UpdateCheckResult =
    try {
        val response = githubClient.get(
            "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
        )
        if (!response.status.isSuccess()) {
            UpdateCheckResult.Error("GitHub returned ${response.status}")
        } else {
            val release = response.body<GithubRelease>()
            val versionCode = parseVersionCode(release.tag_name)
            if (versionCode == null) {
                UpdateCheckResult.Error("Latest release is missing a parseable tag")
            } else if (versionCode <= currentVersionCode) {
                UpdateCheckResult.UpToDate
            } else {
                // Only Windows has a real in-place upgrade path (MSI + fixed upgradeUuid).
                // macOS/Linux, or a Windows release cut without an MSI, fall back to
                // pointing the user at the release page.
                val msiAsset = release.assets.firstOrNull { it.name.endsWith(".msi") }
                val manualOnly = !(isWindows() && msiAsset != null)
                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        latestVersionCode = versionCode,
                        latestVersionName = release.tag_name.substringAfter("-"),
                        releaseNotes = release.body.orEmpty(),
                        downloadUrl = if (manualOnly) release.html_url else msiAsset!!.browser_download_url,
                        publishedAt = release.published_at.orEmpty()
                    ),
                    manualOnly = manualOnly
                )
            }
        }
    } catch (e: Exception) {
        UpdateCheckResult.Error(e.message ?: "Update check failed")
    }

suspend fun downloadUpdate(
    githubClient: HttpClient,
    info: AppUpdateInfo,
    onProgress: (Float) -> Unit
): File = withContext(Dispatchers.IO) {
    // Downloads (not a hidden app-data folder) so the file is somewhere the user would
    // actually think to look if the auto-launch below doesn't visibly do anything.
    val downloadsDir = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
    val msiFile = File(downloadsDir, "Reserveo-${info.latestVersionName}.msi")

    githubClient.prepareGet(info.downloadUrl).execute { response ->
        val total = response.contentLength() ?: -1L
        var received = 0L
        val channel: ByteReadChannel = response.bodyAsChannel()
        FileOutputStream(msiFile).use { out ->
            val buffer = ByteArray(8 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) continue
                out.write(buffer, 0, read)
                received += read
                if (total > 0) onProgress(received.toFloat() / total)
            }
        }
    }
    msiFile
}

// A packaged jpackage app runs inside a Windows Job Object that kills all its child
// processes the moment it exits. Both spawning msiexec directly and java.awt.Desktop.open()
// create the new process as a direct child of this JVM, so it was getting silently killed
// before it could even show its UAC prompt. Delegating to the already-running explorer.exe
// (the same way double-clicking a file works) escapes that job, since explorer launches the
// installer itself rather than us launching it as our own child.
fun installUpdate(msiFile: File) {
    ProcessBuilder("explorer.exe", msiFile.absolutePath).start()
    kotlin.system.exitProcess(0)
}

fun openReleasePage(url: String) {
    Desktop.getDesktop().browse(URI(url))
}
