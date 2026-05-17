package com.clairedoc.app.rag

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clairedoc.app.data.repository.DocumentSessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "IndexingWorker"
private const val KEY_SESSION_ID = "session_id"
private const val KEY_IS_REINDEX = "is_reindex"
private const val MAX_RETRY_ATTEMPTS = 3

/** WorkManager tag used to observe all indexing jobs from HomeViewModel. */
const val TAG_RAG_INDEXING = "rag_indexing"

/**
 * Background worker that embeds a [DocumentSession] into the ObjectBox HNSW vector store.
 *
 * Uses the [EntryPoint] pattern to obtain Hilt-managed singletons without requiring
 * the `hilt-work` artifact — avoids an extra dependency and its version-conflict risks.
 *
 * Scheduling:
 * - Enqueued with a unique work name (`rag_index_<sessionId>`) so duplicate calls are
 *   de-duplicated by WorkManager.
 * - Retries up to [MAX_RETRY_ATTEMPTS] times with exponential backoff (15 s base).
 * - Requires storage not low — prevents writing to nearly-full devices.
 */
class IndexingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface IndexingWorkerEntryPoint {
        fun chunkRepository(): ChunkRepository
        fun sessionRepository(): DocumentSessionRepository
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID)
            ?: return@withContext Result.failure()

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            IndexingWorkerEntryPoint::class.java
        )
        val chunkRepo = ep.chunkRepository()
        val sessionRepo = ep.sessionRepository()

        // Guard: TFLite embedder model file must be present before we try to embed
        if (!chunkRepo.isEmbedderReady()) {
            Log.w(TAG, "Embedder not ready — retrying later for session $sessionId")
            return@withContext if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry()
            else Result.failure()
        }

        val isReindex = inputData.getBoolean(KEY_IS_REINDEX, false)

        // Skip already-indexed sessions unless this is an explicit re-index request
        if (!isReindex && chunkRepo.isIndexed(sessionId)) {
            Log.d(TAG, "Session $sessionId already indexed — skipping")
            return@withContext Result.success()
        }

        val session = sessionRepo.getAllSessionsSnapshot()
            .firstOrNull { it.id == sessionId }
            ?: run {
                Log.w(TAG, "Session $sessionId not found in DB — skipping")
                return@withContext Result.failure()
            }

        return@withContext runCatching {
            if (isReindex) chunkRepo.deleteChunksForSession(sessionId)
            chunkRepo.indexSession(session)
            Log.d(TAG, "Session $sessionId indexed successfully")
            Result.success()
        }.getOrElse { ex ->
            Log.e(TAG, "Indexing failed for session $sessionId", ex)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {

        /**
         * Enqueues a background indexing job for [sessionId].
         *
         * Default policy [ExistingWorkPolicy.KEEP] is safe for post-save calls — it won't
         * replace an already-queued or running job for the same session.
         */
        fun enqueue(
            context: Context,
            sessionId: String,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
        ) {
            val request = OneTimeWorkRequestBuilder<IndexingWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .setConstraints(
                    Constraints.Builder().setRequiresStorageNotLow(true).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG_RAG_INDEXING)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("rag_index_$sessionId", policy, request)
        }

        /**
         * Enqueues a forced re-index for [sessionId] — deletes existing chunks first,
         * then re-embeds from scratch. Always uses [ExistingWorkPolicy.REPLACE] so that
         * any previously queued or running job for this session is cancelled and replaced.
         */
        fun enqueueReindex(context: Context, sessionId: String) {
            val request = OneTimeWorkRequestBuilder<IndexingWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SESSION_ID to sessionId,
                        KEY_IS_REINDEX to true
                    )
                )
                .setConstraints(
                    Constraints.Builder().setRequiresStorageNotLow(true).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG_RAG_INDEXING)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("rag_index_$sessionId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
