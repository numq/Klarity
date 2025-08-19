package io.github.numq.klarity.pipeline

internal interface CloseablePipeline {
    suspend fun close(): Result<Unit>
}