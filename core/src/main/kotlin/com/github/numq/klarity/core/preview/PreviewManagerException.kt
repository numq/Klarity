package com.github.numq.klarity.core.preview

data class PreviewManagerException(override val cause: Throwable) : Exception(cause)