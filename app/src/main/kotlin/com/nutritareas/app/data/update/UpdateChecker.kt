package com.nutritareas.app.data.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Available(val release: GitHubRelease) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * Checks GitHub Releases for a newer version, since the app is distributed only from its GitHub
 * repo rather than an app store. No auth token is used: the repo is public and this only ever
 * makes one unauthenticated call, well under GitHub's anonymous rate limit.
 */
class UpdateChecker(
    private val owner: String,
    private val repo: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Failed("HTTP ${response.code}")
                }
                val bodyString = response.body?.string()
                    ?: return@withContext UpdateCheckResult.Failed("Respuesta vacía")
                val release = json.decodeFromString<GitHubRelease>(bodyString)
                if (isNewerVersion(currentVersionName, release.versionName)) {
                    UpdateCheckResult.Available(release)
                } else {
                    UpdateCheckResult.UpToDate(currentVersionName)
                }
            }
        } catch (e: IOException) {
            UpdateCheckResult.Failed(e.message ?: "Error de red")
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: "Error desconocido")
        }
    }

    /** Compares dotted version strings ("1.2.0") component by component; non-numeric parts count as 0. */
    private fun isNewerVersion(current: String, remote: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(currentParts.size, remoteParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
