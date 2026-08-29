package com.nutritareas.app.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
) {
    val versionName: String get() = tagName.removePrefix("v")

    val apkAsset: GitHubReleaseAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
)
