package com.github.numq.klarity.core.preview

data class PreviewException(override val cause: Throwable) : Exception(cause)