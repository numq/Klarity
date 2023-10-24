package uploading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import java.awt.FileDialog
import java.io.FilenameFilter

@Composable
fun UploadingDialog(extensions: List<String> = emptyList(), onUpload: (String) -> Unit) {
    LaunchedEffect(Unit) {
        FileDialog(
            ComposeWindow(),
            "Upload file",
            FileDialog.LOAD
        ).apply {
            filenameFilter = FilenameFilter { file, _ ->
                extensions.map(String::lowercase).any(file.extension.lowercase()::equals)
            }
            isAlwaysOnTop = true
            isMultipleMode = false
            isVisible = true
        }
            .run { "${directory ?: ""}${file ?: ""}" }
            .also(onUpload)
    }
}