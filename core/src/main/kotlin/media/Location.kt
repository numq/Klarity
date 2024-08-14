package media

import java.io.File
import java.net.URI

sealed interface Location {
    val value: String

    data class Local(val fileName: String, val path: String) : Location {
        override val value = path
    }

    data class Remote(val url: String) : Location {
        override val value = url
    }

    companion object {
        fun create(location: String): Result<Location> = runCatching {
            checkNotNull(
                File(location).takeIf(File::exists)?.run { Local(fileName = name, path = path) }
                    ?: URI(location).takeIf(URI::isAbsolute)?.run { Remote(url = location) }
            ) { "Unable to create location" }
        }
    }
}