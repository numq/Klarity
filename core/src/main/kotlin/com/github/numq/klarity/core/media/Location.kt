package com.github.numq.klarity.core.media

sealed interface Location {
    val path: String

    data class Local(override val path: String, val name: String) : Location

    data class Remote(override val path: String) : Location
}