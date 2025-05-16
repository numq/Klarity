package io.github.numq.klarity.renderer.compose

import io.github.numq.klarity.renderer.SkiaRenderer

data class Foreground(val renderer: SkiaRenderer, val imageScale: ImageScale = ImageScale.Fit)