package com.github.numq.klarity.core.snapshot

data class SnapshotException(override val cause: Throwable) : Exception(cause)