package org.julsz.smnt

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.util.Locale

fun main() {
    Locale.setDefault(Locale.forLanguageTag("pl"))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Reserveo",
        ) {
            AppRoot()
        }
    }
}
