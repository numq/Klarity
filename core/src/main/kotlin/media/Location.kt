package media

import java.io.File
import java.net.URI

sealed interface Location {
    data class Local(val fileName: String, val path: String) : Location

    data class Remote(val url: String) : Location

    companion object {
        fun create(location: String): Result<Location> = runCatching {
            checkNotNull(
                File(location).takeIf(File::exists)?.run { Local(fileName = name, path = path) }
                    ?: URI(location).takeIf(URI::isAbsolute)?.run { Remote(url = location) }
            ) { "Unable to create location" }
        }
    }
}