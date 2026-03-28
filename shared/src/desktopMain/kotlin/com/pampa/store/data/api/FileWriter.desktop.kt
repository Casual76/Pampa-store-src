package com.pampa.store.data.api

import java.io.File
import java.io.FileOutputStream

actual class FileWriter actual constructor(path: String) {
    private val file = File(path)
    private val outputStream: FileOutputStream

    init {
        file.parentFile?.mkdirs()
        outputStream = FileOutputStream(file)
    }

    actual fun write(bytes: ByteArray) {
        outputStream.write(bytes)
    }

    actual fun close() {
        outputStream.flush()
        outputStream.close()
    }
}
