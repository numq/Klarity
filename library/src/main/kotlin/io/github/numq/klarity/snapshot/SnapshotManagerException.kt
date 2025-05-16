package io.github.numq.klarity.snapshot

data class SnapshotManagerException(override val cause: Throwable) : Exception(cause)