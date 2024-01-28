package uploading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter

@Composable
fun UploadingDialog(
    visible: Boolean,
    extensions: List<String> = emptyList(),
    multipleMode: Boolean = false,
    onUpload: suspend (String) -> Unit,
    onClose: () -> Unit,
) {

    LaunchedEffect(visible) {
        FileDialog(ComposeWindow(), "Upload file", FileDialog.LOAD).apply {
            filenameFilter = FilenameFilter { file, _ ->
                extensions.map(String::lowercase).any(file.extension.lowercase()::equals)
            }
            isMultipleMode = multipleMode
            isAlwaysOnTop = true
            isVisible = visible
        }
            .files
            .mapNotNull(File::getPath)
            .forEach { path ->
                onUpload(path)
            }
    }

    DisposableEffect(Unit) {
        onDispose(onClose)
    }
}