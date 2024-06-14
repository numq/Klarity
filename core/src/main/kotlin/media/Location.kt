package media

sealed interface Location {
    data class Local(val fileName: String, val path: String) : Location

    data class Remote(val url: String) : Location
}