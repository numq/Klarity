package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.compose.scale.ImageScale

data class Foreground(val renderer: SkiaRenderer, val imageScale: ImageScale = ImageScale.Fit)