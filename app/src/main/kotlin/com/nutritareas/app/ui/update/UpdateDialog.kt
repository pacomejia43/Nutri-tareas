package com.nutritareas.app.ui.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutritareas.app.BuildConfig
import com.nutritareas.app.R

/**
 * Hosts the "new version available" flow as a dialog that can appear over any screen: an
 * automatic silent check runs on app start (see UpdateViewModel.init), and this renders its
 * result. It shares its UpdateViewModel instance with SettingsScreen (same ViewModelStoreOwner,
 * same class -> same default key), which is where the manual "check for updates" feedback
 * (up to date / check failed) is shown inline - this host only ever needs to react to
 * `availableRelease`, since the silent check never populates info/error messages.
 */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    val viewModel: UpdateViewModel = viewModel(
        factory = UpdateViewModel.factory(context.applicationContext as Application),
    )
    val uiState by viewModel.uiState.collectAsState()

    val permissionSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onReturnedFromPermissionSettings()
    }

    val release = uiState.availableRelease ?: return
    val downloadState = uiState.downloadState

    AlertDialog(
        onDismissRequest = { if (downloadState == DownloadState.Idle) viewModel.dismissUpdate() },
        title = { Text(titleFor(downloadState)) },
        text = {
            when (downloadState) {
                DownloadState.Idle -> Text(
                    stringResource(R.string.update_available_body, release.versionName, BuildConfig.VERSION_NAME),
                )

                DownloadState.Downloading -> Row {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp).size(20.dp))
                    Text(stringResource(R.string.update_downloading))
                }

                is DownloadState.ReadyToInstall -> Text(stringResource(R.string.update_ready_install))
                DownloadState.NeedsInstallPermission -> Text(stringResource(R.string.update_install_permission_body))
                DownloadState.Failed -> Column {
                    Text(stringResource(R.string.update_download_failed))
                    TextButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(stringResource(R.string.update_open_in_browser)) }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                DownloadState.Idle, DownloadState.Failed -> TextButton(onClick = viewModel::startDownload) {
                    Text(stringResource(R.string.update_download))
                }

                DownloadState.NeedsInstallPermission -> TextButton(
                    onClick = { permissionSettingsLauncher.launch(viewModel.installPermissionSettingsIntent()) },
                ) { Text(stringResource(R.string.update_open_settings)) }

                is DownloadState.ReadyToInstall -> TextButton(
                    onClick = {
                        context.startActivity(viewModel.buildInstallIntent(downloadState.apkUri))
                        viewModel.dismissUpdate()
                    },
                ) { Text(stringResource(R.string.update_install)) }

                DownloadState.Downloading -> {}
            }
        },
        dismissButton = {
            if (downloadState == DownloadState.Idle || downloadState == DownloadState.Failed) {
                TextButton(onClick = viewModel::dismissUpdate) { Text(stringResource(R.string.update_later)) }
            }
        },
    )
}

@Composable
private fun titleFor(downloadState: DownloadState): String = when (downloadState) {
    DownloadState.NeedsInstallPermission -> stringResource(R.string.update_install_permission_title)
    else -> stringResource(R.string.update_available_title)
}
