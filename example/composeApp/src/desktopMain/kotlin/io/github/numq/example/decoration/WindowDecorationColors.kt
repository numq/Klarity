package io.github.numq.example.decoration

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class WindowDecorationColors(
    val decoration: @Composable () -> Color = {
        MaterialTheme.colorScheme.surface
    },
    val content: @Composable () -> Color = {
        MaterialTheme.colorScheme.background
    },
    val switchSchemeButton: @Composable () -> Color = {
        MaterialTheme.colorScheme.primary
    },
    val minimizeButton: @Composable () -> Color = {
        MaterialTheme.colorScheme.primary
    },
    val fullscreenButton: @Composable () -> Color = {
        MaterialTheme.colorScheme.primary
    },
    val closeButton: @Composable () -> Color = {
        MaterialTheme.colorScheme.primary
    },
)