package com.nutritareas.app.ui.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutritareas.app.BuildConfig
import com.nutritareas.app.NutriTareasApp
import com.nutritareas.app.R
import com.nutritareas.app.data.settings.SettingsRepository
import com.nutritareas.app.data.update.UpdateCheckResult
import com.nutritareas.app.data.update.UpdateChecker
import com.nutritareas.app.data.update.UpdateInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val updateChecker: UpdateChecker,
    private val updateInstaller: UpdateInstaller,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        checkForUpdate(silent = true)
    }

    /** [silent]: only surface an available update the user hasn't already dismissed, and stay
     *  quiet on "up to date" / failures - used for the automatic check on app start. */
    fun checkForUpdate(silent: Boolean) {
        val app = getApplication<Application>()
        _uiState.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            val lastSeenTag = settingsRepository.currentSettings().lastSeenReleaseTag
            when (val result = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.Available -> {
                    val shouldShow = !silent || result.release.tagName != lastSeenTag
                    _uiState.update {
                        it.copy(isChecking = false, availableRelease = if (shouldShow) result.release else null)
                    }
                }

                is UpdateCheckResult.UpToDate -> _uiState.update {
                    it.copy(
                        isChecking = false,
                        infoMessage = if (silent) null else app.getString(R.string.up_to_date, result.currentVersion),
                    )
                }

                is UpdateCheckResult.Failed -> _uiState.update {
                    it.copy(isChecking = false, errorMessage = if (silent) null else app.getString(R.string.error_generic))
                }
            }
        }
    }

    fun dismissUpdate() {
        val release = _uiState.value.availableRelease ?: return
        viewModelScope.launch { settingsRepository.saveLastSeenReleaseTag(release.tagName) }
        _uiState.update { it.copy(availableRelease = null, downloadState = DownloadState.Idle) }
    }

    fun canInstallPackages(): Boolean = updateInstaller.canInstallPackages()

    fun installPermissionSettingsIntent(): Intent = updateInstaller.installPermissionSettingsIntent()

    fun buildInstallIntent(apkUri: Uri): Intent = updateInstaller.buildInstallIntent(apkUri)

    fun startDownload() {
        val app = getApplication<Application>()
        val release = _uiState.value.availableRelease ?: return
        val asset = release.apkAsset
        if (asset == null) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.update_no_asset)) }
            return
        }
        if (!updateInstaller.canInstallPackages()) {
            _uiState.update { it.copy(downloadState = DownloadState.NeedsInstallPermission) }
            return
        }
        _uiState.update { it.copy(downloadState = DownloadState.Downloading) }
        viewModelScope.launch {
            val downloadId = updateInstaller.enqueueDownload(asset.browserDownloadUrl, asset.name)
            val uri = updateInstaller.awaitDownload(downloadId)
            _uiState.update {
                it.copy(
                    downloadState = if (uri != null) DownloadState.ReadyToInstall(uri) else DownloadState.Failed,
                    errorMessage = if (uri == null) app.getString(R.string.update_download_failed) else null,
                )
            }
        }
    }

    fun onReturnedFromPermissionSettings() {
        if (updateInstaller.canInstallPackages()) {
            startDownload()
        } else {
            _uiState.update { it.copy(downloadState = DownloadState.Idle) }
        }
    }

    fun onInfoMessageShown() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            val container = (application as NutriTareasApp).container
            return viewModelFactory {
                initializer {
                    UpdateViewModel(
                        application = application,
                        settingsRepository = container.settingsRepository,
                        updateChecker = container.updateChecker,
                        updateInstaller = container.updateInstaller,
                    )
                }
            }
        }
    }
}
