package com.github.numq.klarity.core.media

import java.io.File
import java.net.URI

sealed interface Location {
    val path: String

    data class Local(override val path: String, val name: String) : Location

    data class Remote(override val path: String) : Location

    companion object {
        fun create(location: String): Result<Location> = runCatching {
            checkNotNull(
                File(location).takeIf(File::exists)?.run { Local(path = path, name = name) }
                    ?: URI(location).takeIf(URI::isAbsolute)?.run { Remote(path = location) }
            ) { "Unable to create location" }
        }
    }
}