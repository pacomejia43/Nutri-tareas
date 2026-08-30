package com.nutritareas.app.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Downloads and installs an update APK from a GitHub release asset. The app isn't on Play Store,
 * so this drives the standard "download via DownloadManager, then hand off to the system package
 * installer" sideload flow, gated by the user granting "install unknown apps" for this app.
 */
class UpdateInstaller(private val context: Context) {

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Enqueues the download with no explicit destination, so DownloadManager manages the file
     *  itself and [awaitDownload] can later resolve it to a content:// Uri via
     *  [DownloadManager.getUriForDownloadedFile] without needing any storage permission. */
    fun enqueueDownload(downloadUrl: String, title: String): Long {
        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle(title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
        return downloadManager.enqueue(request)
    }

    /** Suspends until [downloadId] finishes, returning its content Uri on success or null on failure. */
    suspend fun awaitDownload(downloadId: Long): Uri? = suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (finishedId != downloadId) return
                runCatching { context.unregisterReceiver(this) }
                val result = runCatching {
                    downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            downloadManager.getUriForDownloadedFile(downloadId)
                        } else {
                            null
                        }
                    }
                }.getOrNull()
                if (continuation.isActive) continuation.resume(result)
            }
        }
        // DownloadManager is a system service running outside this app's process, so this
        // broadcast is sent from a different UID - RECEIVER_NOT_EXPORTED would silently drop it
        // (that flag only allows broadcasts from this app's own package), leaving the download
        // stuck at "Downloading" forever even though it finished.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        continuation.invokeOnCancellation {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    fun buildInstallIntent(apkUri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
}
