package io.github.numq.klarity.renderer.compose

import io.github.numq.klarity.renderer.Renderer

data class Foreground(val renderer: Renderer, val imageScale: ImageScale = ImageScale.Fit)