package com.nutritareas.app.ui.update

import android.net.Uri
import com.nutritareas.app.data.update.GitHubRelease

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Downloading : DownloadState
    data class ReadyToInstall(val apkUri: Uri) : DownloadState
    data object NeedsInstallPermission : DownloadState
    data object Failed : DownloadState
}

data class UpdateUiState(
    val isChecking: Boolean = false,
    val availableRelease: GitHubRelease? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)
