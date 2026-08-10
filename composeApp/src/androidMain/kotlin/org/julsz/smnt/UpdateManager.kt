package org.julsz.smnt

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.FileOutputStream

private const val GITHUB_REPO_OWNER = "Szyntos"
private const val GITHUB_REPO_NAME = "Reserveo"

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

@Serializable
private data class GithubReleaseAsset(val name: String, val browser_download_url: String)

@Serializable
private data class GithubRelease(
    val tag_name: String,
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
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (versionCode == null || apkAsset == null) {
                UpdateCheckResult.Error("Latest release is missing a parseable tag or APK asset")
            } else if (versionCode <= currentVersionCode) {
                UpdateCheckResult.UpToDate
            } else {
                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        latestVersionCode = versionCode,
                        latestVersionName = release.tag_name.substringAfter("-"),
                        releaseNotes = release.body.orEmpty(),
                        downloadUrl = apkAsset.browser_download_url,
                        publishedAt = release.published_at.orEmpty()
                    )
                )
            }
        }
    } catch (e: Exception) {
        UpdateCheckResult.Error(e.message ?: "Update check failed")
    }

suspend fun downloadUpdate(
    githubClient: HttpClient,
    context: Context,
    info: AppUpdateInfo,
    onProgress: (Float) -> Unit
): File = withContext(Dispatchers.IO) {
    val updatesDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
    val apkFile = File(updatesDir, "reserveo-${info.latestVersionName}.apk")

    githubClient.prepareGet(info.downloadUrl).execute { response ->
        val total = response.contentLength() ?: -1L
        var received = 0L
        val channel: ByteReadChannel = response.bodyAsChannel()
        FileOutputStream(apkFile).use { out ->
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
    apkFile
}

fun installUpdate(context: Context, apkFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    // Hands off to the system installer UI — the user still has to tap "Install" there.
    // There is no way to skip that OS-level confirmation for a sideloaded app without
    // device-owner/MDM privileges.
    context.startActivity(intent)
}
