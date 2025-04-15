package com.github.numq.klarity.core.snapshot

data class SnapshotManagerException(override val cause: Throwable) : Exception(cause)