package io.github.numq.example.renderer

import io.github.numq.klarity.renderer.Renderer

data class RegisteredRenderer(val id: String, val location: String, val renderer: Renderer)