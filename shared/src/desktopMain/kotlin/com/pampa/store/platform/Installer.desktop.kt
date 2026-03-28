package com.pampa.store.platform

import com.pampa.store.data.model.AppPlatform
import com.pampa.store.data.model.InstallEvent
import com.pampa.store.data.model.STORE_APP_ID
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual class AppInstaller : InstallerService {
    actual override fun install(filePath: String, appId: String): Flow<InstallEvent> = flow {
        emit(InstallEvent.Preparing)

        try {
            val file = File(filePath)
            if (!file.exists()) {
                emit(InstallEvent.Failure("missing_file", "File not found: $filePath"))
                return@flow
            }

            val extension = file.extension.lowercase()
            if (extension !in setOf("exe", "msi")) {
                emit(
                    InstallEvent.Failure(
                        "unsupported_windows_asset",
                        "Su Windows sono supportati solo installer .exe o .msi."
                    )
                )
                return@flow
            }

            val awaitingMessage = if (appId == STORE_APP_ID) {
                "Avvio installer di Pampa Store. Conferma UAC e chiudi lo store se l'installer lo richiede."
            } else {
                "Avvio installer Windows. Conferma UAC e completa l'installazione nella finestra di setup."
            }
            emit(InstallEvent.AwaitingUserAction(awaitingMessage))
            emit(InstallEvent.Installing("Installer Windows in esecuzione..."))

            val process = ProcessBuilder(buildWindowsInstallerCommand(file))
                .directory(file.parentFile)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (!isSuccessfulWindowsInstallerExitCode(exitCode)) {
                emit(InstallEvent.Failure("installer_exit", windowsInstallerFailureMessage(exitCode)))
                return@flow
            }

            emit(InstallEvent.Success)
        } catch (e: Exception) {
            emit(InstallEvent.Failure("launcher_error", e.message ?: "Installation failed"))
        }
    }
}

actual fun getCurrentPlatform(): AppPlatform = AppPlatform.WINDOWS

actual fun openFileManager(path: String) {
    try {
        Desktop.getDesktop().open(File(path))
    } catch (_: Exception) {
        Runtime.getRuntime().exec(arrayOf("explorer.exe", path))
    }
}

private fun buildWindowsInstallerCommand(file: File): List<String> {
    return when (file.extension.lowercase()) {
        "msi" -> listOf("msiexec.exe", "/i", file.absolutePath)
        else -> listOf(file.absolutePath)
    }
}

private fun isSuccessfulWindowsInstallerExitCode(exitCode: Int): Boolean {
    return exitCode in setOf(0, 1641, 3010)
}

private fun windowsInstallerFailureMessage(exitCode: Int): String {
    return when (exitCode) {
        1602 -> "Installazione annullata dall'utente."
        1618 -> "Un'altra installazione Windows e' gia' in corso. Attendi e riprova."
        else -> "Installer terminato con codice $exitCode."
    }
}
