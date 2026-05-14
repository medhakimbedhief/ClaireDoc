package com.clairedoc.app.rag

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
data class DocumentChunk(
    @Id var id: Long = 0,
    var sessionId: String = "",
    var documentTitle: String = "",
    var chunkIndex: Int = 0,
    var totalChunks: Int = 0,
    var text: String = "",
    var pageNumber: Int? = null,
    var createdAt: Long = System.currentTimeMillis(),
    @HnswIndex(
        dimensions = 256,
        distanceType = VectorDistanceType.COSINE,
        neighborsPerNode = 30,
        indexingSearchCount = 200
    )
    var embedding: FloatArray? = null
)
