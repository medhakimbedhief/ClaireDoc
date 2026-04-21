package com.clairedoc.app.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.clairedoc.app.data.model.DownloadProgress
import com.clairedoc.app.data.model.ModelVariant
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
private const val KEY_DOWNLOAD_VARIANT = "download_variant"
private const val POLL_INTERVAL_MS = 500L

/** Minimum file size to consider a model file valid (guards against partial downloads). */
internal const val MIN_MODEL_SIZE_BYTES = 100_000_000L

class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ──────────────────────────────────────────────────────────
    //  Variant helpers
    // ──────────────────────────────────────────────────────────

    private fun modelsDir(): File =
        context.getExternalFilesDir("models")!!.also { it.mkdirs() }

    fun modelFileFor(variant: ModelVariant): File = File(modelsDir(), variant.filename)

    fun isVariantInstalled(variant: ModelVariant): Boolean {
        val f = modelFileFor(variant)
        return f.exists() && f.length() >= MIN_MODEL_SIZE_BYTES
    }

    // ──────────────────────────────────────────────────────────
    //  Download
    // ──────────────────────────────────────────────────────────

    /**
     * Enqueues a [DownloadManager] request for [variant].
     * Persists both the download ID and variant name so the session can be resumed.
     * Returns the system-assigned download ID.
     */
    fun startDownload(variant: ModelVariant = ModelVariant.E2B): Long {
        val destFile = modelFileFor(variant)

        val request = DownloadManager.Request(Uri.parse(variant.url))
            .setTitle("ClaireDoc — ${variant.displayName}")
            .setDescription("Downloading AI model (${variant.approximateSizeGb})")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val id = downloadManager.enqueue(request)
        prefs.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_DOWNLOAD_VARIANT, variant.name)
            .apply()
        Log.d(TAG, "Download enqueued for ${variant.name}, id=$id → ${destFile.absolutePath}")
        return id
    }

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

    fun getSavedDownloadId(): Long = prefs.getLong(KEY_DOWNLOAD_ID, -1L)

    /** Returns the variant that was being downloaded in the last session, or null. */
    fun getSavedDownloadVariant(): ModelVariant? {
        val name = prefs.getString(KEY_DOWNLOAD_VARIANT, null) ?: return null
        return runCatching { ModelVariant.valueOf(name) }.getOrNull()
    }

    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        prefs.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_VARIANT)
            .apply()
        Log.d(TAG, "Download cancelled, id=$downloadId")
    }

    // ──────────────────────────────────────────────────────────
    //  Delete
    // ──────────────────────────────────────────────────────────

    /**
     * Deletes the model file for [variant] from disk.
     * Also cancels any in-progress download for the same variant.
     * Returns true if the file no longer exists afterwards.
     */
    fun deleteModel(variant: ModelVariant): Boolean {
        val savedId = getSavedDownloadId()
        if (savedId != -1L && getSavedDownloadVariant() == variant) {
            cancelDownload(savedId)
        }
        val file = modelFileFor(variant)
        if (!file.exists()) return true
        return file.delete().also { deleted ->
            if (deleted) Log.d(TAG, "Deleted ${variant.filename}")
            else Log.w(TAG, "Failed to delete ${variant.filename}")
        }
    }
}
