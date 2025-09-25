package org.noblecow.hrservice

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Heart Rate Monitor"
    ) {
        App()
    }
}
