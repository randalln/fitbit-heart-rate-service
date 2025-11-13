package org.noblecow.hrservice.data.util

import co.touchlab.kermit.Logger
import org.noblecow.hrservice.data.repository.AppState
import org.noblecow.hrservice.viewmodel.MainUiState

internal fun MainUiState.printDiff(tag: String, otherState: MainUiState) {
    val logger = Logger.withTag(tag)

    val diff = buildString {
        if (this@printDiff.bpm != otherState.bpm) {
            append("bpm: ${this@printDiff.bpm} -> ${otherState.bpm}")
        }
        if (this@printDiff.bluetoothRequested != otherState.bluetoothRequested) {
            append("isBluetoothEnabled: ${this@printDiff.bluetoothRequested} -> ")
            append("${otherState.bluetoothRequested}\n")
        }
        if (this@printDiff.isClientConnected != otherState.isClientConnected) {
            append("isClientConnected: ${this@printDiff.isClientConnected} -> ")
            append("${otherState.isClientConnected}\n")
        }
        if (this@printDiff.permissionsRequested != otherState.permissionsRequested) {
            append("permissionsRequested: ${this@printDiff.permissionsRequested} -> ")
            append("${otherState.permissionsRequested}\n")
        }
        if (this@printDiff.servicesState != otherState.servicesState) {
            append("servicesState: ${this@printDiff.servicesState} -> ")
            append("${otherState.servicesState}\n")
        }
        if (this@printDiff.userMessage != otherState.userMessage) {
            append(
                "userMessage: ${this@printDiff.userMessage} -> ${otherState.userMessage}\n"
            )
        }
    }
    if (diff.isNotEmpty()) {
        logger.d("state diff -- $diff")
    }
}

internal fun AppState.printDiff(tag: String, otherState: AppState) {
    val logger = Logger.withTag(tag)

    val diff = buildString {
        if (this@printDiff.bpm != otherState.bpm) {
            append("bpm: ${this@printDiff.bpm} -> ${otherState.bpm}")
        }
        if (this@printDiff.isClientConnected != otherState.isClientConnected) {
            append("isClientConnected: ${this@printDiff.isClientConnected} ")
            append("-> ${otherState.isClientConnected}")
        }
        if (this@printDiff.servicesState != otherState.servicesState) {
            append("servicesState: ${this@printDiff.servicesState} -> ${otherState.servicesState}")
        }
    }
    if (diff.isNotEmpty()) {
        logger.d("state diff -- $diff")
    }
}
