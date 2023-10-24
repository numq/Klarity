package application

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.window.singleWindowApplication
import main.MainScreen
import java.awt.Dimension

const val APP_NAME = "JCMP"

fun main() = singleWindowApplication(title = APP_NAME) {

    window.minimumSize = Dimension(500, 500)

    MaterialTheme {
        MainScreen()
    }
}