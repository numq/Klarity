package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.compose.scale.ImageScale
import com.github.numq.klarity.core.renderer.Renderer

data class Foreground(val renderer: Renderer, val imageScale: ImageScale = ImageScale.Fit)