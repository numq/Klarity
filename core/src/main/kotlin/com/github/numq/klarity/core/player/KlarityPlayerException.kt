package com.github.numq.klarity.core.player

data class KlarityPlayerException(override val cause: Throwable) : Exception(cause)