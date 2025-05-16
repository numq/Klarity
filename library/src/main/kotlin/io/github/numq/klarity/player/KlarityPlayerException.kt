package io.github.numq.klarity.player

data class KlarityPlayerException(override val cause: Throwable) : Exception(cause)