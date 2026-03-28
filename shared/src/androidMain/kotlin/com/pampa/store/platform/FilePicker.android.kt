package com.pampa.store.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

actual class FilePicker(
    private val pickFileAction: (List<String>, (String?) -> Unit) -> Unit
) {
    actual suspend fun pickFile(title: String, allowedExtensions: List<String>): String? {
        return suspendCancellableCoroutine { continuation ->
            pickFileAction(allowedExtensions) { path ->
                if (continuation.isActive) {
                    continuation.resume(path)
                }
            }
        }
    }

    actual suspend fun pickDirectory(title: String): String? = null
}

@Composable
actual fun rememberFilePicker(): FilePicker {
    val context = LocalContext.current
    var callback: ((String?) -> Unit)? = null
    var latestTempPath by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            callback?.invoke(null)
            return@rememberLauncherForActivityResult
        }

        // Copy to an app-owned temp file so the upload flow always gets a local readable path.
        val tempDir = pickerTempDirectory(context.cacheDir)
        cleanupStalePickerTempFiles(tempDir)
        val tempFile = File.createTempFile("upload-", ".tmp", tempDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                tempFile.delete()
                callback?.invoke(null)
                return@rememberLauncherForActivityResult
            }

            latestTempPath?.takeIf { it != tempFile.absolutePath }?.let { previousPath ->
                File(previousPath).takeIf { it.exists() }?.delete()
            }
            latestTempPath = tempFile.absolutePath
            callback?.invoke(tempFile.absolutePath)
        } catch (e: Exception) {
            tempFile.delete()
            callback?.invoke(null)
        }
    }

    return remember(launcher) {
        FilePicker { allowedExtensions, cb ->
            callback = cb
            val mimeType = when {
                allowedExtensions.contains("apk") -> "application/vnd.android.package-archive"
                else -> "*/*"
            }
            launcher.launch(mimeType)
        }
    }
}

private fun pickerTempDirectory(cacheDir: File): File {
    return File(cacheDir, "pampa-file-picker").also { it.mkdirs() }
}

internal fun cleanupStalePickerTempFiles(
    tempDir: File,
    nowMillis: Long = System.currentTimeMillis(),
    maxAgeMillis: Long = 24L * 60L * 60L * 1000L
) {
    if (!tempDir.exists() || !tempDir.isDirectory) return
    tempDir.listFiles().orEmpty().forEach { file ->
        if (!file.isFile) return@forEach
        val fileAge = nowMillis - file.lastModified()
        if (fileAge > maxAgeMillis) {
            file.delete()
        }
    }
}
