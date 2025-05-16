package io.github.numq.klarity.preview

data class PreviewManagerException(override val cause: Throwable) : Exception(cause)