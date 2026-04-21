package com.clairedoc.app.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.clairedoc.app.data.model.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

private const val TAG = "ClaireDoc_Download"
private const val PREFS_NAME = "clairedoc_prefs"
private const val KEY_DOWNLOAD_ID = "download_id"
private const val POLL_INTERVAL_MS = 500L

class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    }

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Enqueues a new [DownloadManager] request and persists the download ID.
     * Returns the download ID assigned by the system.
     *
     * The destination directory is created here — DownloadManager does not
     * create parent directories automatically.
     */
    fun startDownload(): Long {
        val destDir = context.getExternalFilesDir("models")!!.also { it.mkdirs() }
        val destFile = File(destDir, MODEL_FILENAME)

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("ClaireDoc — Gemma 4 E2B")
            .setDescription("Downloading AI model (2.58 GB)")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val id = downloadManager.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        Log.d(TAG, "Download enqueued, id=$id, dest=${destFile.absolutePath}")
        return id
    }

    /**
     * Polls [DownloadManager] every [POLL_INTERVAL_MS] ms and emits
     * [DownloadProgress] until the download completes or fails.
     */
    fun observeProgress(downloadId: Long): Flow<DownloadProgress> = flow {
        while (true) {
            val progress = queryProgress(downloadId)
            emit(progress)
            if (progress.isComplete || progress.isFailed) break
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    private fun queryProgress(downloadId: Long): DownloadProgress {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        return cursor?.use { c ->
            if (!c.moveToFirst()) {
                return DownloadProgress(0L, 0L, isComplete = false, isFailed = true)
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = c.getLong(
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            ).coerceAtLeast(0L)
            val total = c.getLong(
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            ).coerceAtLeast(0L)

            DownloadProgress(
                bytesDownloaded = downloaded,
                totalBytes = total,
                isComplete = status == DownloadManager.STATUS_SUCCESSFUL,
                isFailed = status == DownloadManager.STATUS_FAILED,
                isPaused = status == DownloadManager.STATUS_PAUSED
            )
        } ?: DownloadProgress(0L, 0L, isComplete = false, isFailed = true)
    }

    /** Returns the persisted download ID, or -1L if none has been started. */
    fun getSavedDownloadId(): Long = prefs.getLong(KEY_DOWNLOAD_ID, -1L)

    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        Log.d(TAG, "Download cancelled, id=$downloadId")
    }
}
