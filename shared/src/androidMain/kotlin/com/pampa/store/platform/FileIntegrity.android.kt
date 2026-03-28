package com.pampa.store.platform

import java.io.File
import java.security.MessageDigest

actual fun computeFileSha256(path: String): String? {
    val file = File(path)
    if (!file.exists()) return null

    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

actual fun getFileSize(path: String): Long? {
    val file = File(path)
    return if (file.exists()) file.length() else null
}

actual fun deleteFileIfExists(path: String): Boolean {
    val file = File(path)
    return !file.exists() || file.delete()
}

actual fun moveFileReplacing(sourcePath: String, targetPath: String) {
    val source = File(sourcePath)
    val target = File(targetPath)
    target.parentFile?.mkdirs()
    if (target.exists() && !target.delete()) {
        throw IllegalStateException("Unable to replace existing file at $targetPath")
    }
    if (!source.renameTo(target)) {
        source.copyTo(target, overwrite = true)
        if (!source.delete()) {
            throw IllegalStateException("Unable to delete temporary file at $sourcePath")
        }
    }
}
