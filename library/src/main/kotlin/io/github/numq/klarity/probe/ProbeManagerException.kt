package io.github.numq.klarity.probe

data class ProbeManagerException(override val cause: Throwable) : Exception(cause)