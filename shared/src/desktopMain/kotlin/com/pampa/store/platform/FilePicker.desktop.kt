package com.pampa.store.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser

actual class FilePicker {
    actual suspend fun pickFile(title: String, allowedExtensions: List<String>): String? {
        return withContext(Dispatchers.IO) {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            val normalizedExtensions = normalizeExtensions(allowedExtensions)
            if (normalizedExtensions.isNotEmpty()) {
                dialog.filenameFilter = FilenameFilter { _, name ->
                    isAllowedExtension(name, normalizedExtensions)
                }
            }
            dialog.isVisible = true

            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                File(dir, file).absolutePath
            } else {
                null
            }
        }
    }

    actual suspend fun pickDirectory(title: String): String? {
        return withContext(Dispatchers.IO) {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath
            } else {
                null
            }
        }
    }
}

@Composable
actual fun rememberFilePicker(): FilePicker {
    return remember { FilePicker() }
}

internal fun normalizeExtensions(extensions: List<String>): Set<String> {
    return extensions
        .map { it.trim().lowercase().removePrefix(".") }
        .filter { it.isNotBlank() }
        .toSet()
}

internal fun isAllowedExtension(fileName: String, allowedExtensions: Set<String>): Boolean {
    if (allowedExtensions.isEmpty()) return true
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in allowedExtensions
}
