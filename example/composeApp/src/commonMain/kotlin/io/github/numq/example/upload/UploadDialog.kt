package io.github.numq.example.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import java.awt.FileDialog
import java.io.File

@Composable
fun UploadDialog(
    isUploading: Boolean,
    onMediaUploaded: (location: String) -> Unit,
    onClose: () -> Unit
) {
    val window = remember { ComposeWindow() }

    SideEffect {
        FileDialog(window, "Upload media", FileDialog.LOAD).apply {
            isMultipleMode = true

            isVisible = isUploading
        }.files.map(File::getAbsolutePath).forEach(onMediaUploaded)

        onClose()
    }
}