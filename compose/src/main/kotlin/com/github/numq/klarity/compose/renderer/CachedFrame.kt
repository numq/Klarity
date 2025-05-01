package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.frame.Frame
import org.jetbrains.skia.Pixmap

data class CachedFrame(val frame: Frame.Content.Video, val pixmap: Pixmap)