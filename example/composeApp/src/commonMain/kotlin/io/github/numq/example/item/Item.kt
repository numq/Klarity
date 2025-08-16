package io.github.numq.example.item

import kotlin.time.Duration

sealed interface Item {
    val id: String

    val location: String

    data class Loading(override val id: String, override val location: String) : Item

    data class Loaded(
        override val id: String,
        override val location: String,
        val width: Int,
        val height: Int,
        val duration: Duration
    ) : Item

    data class Failed(override val id: String, override val location: String, val throwable: Throwable) : Item
}