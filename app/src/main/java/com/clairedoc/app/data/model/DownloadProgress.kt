package com.clairedoc.app.data.model

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean,
    val isFailed: Boolean,
    val isPaused: Boolean = false
) {
    /** Download fraction in [0f, 1f]. Returns 0 if total size is unknown. */
    val fraction: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                else 0f

    /** Human-readable "downloaded MB / total MB" string. */
    val displayMb: String
        get() {
            val dlMb = bytesDownloaded / 1_048_576
            val totalMb = if (totalBytes > 0) totalBytes / 1_048_576 else 2_641L
            return "$dlMb MB / $totalMb MB"
        }

    /** Percentage string for accessibility labels. */
    val displayPercent: String
        get() = "${(fraction * 100).toInt()}%"
}
