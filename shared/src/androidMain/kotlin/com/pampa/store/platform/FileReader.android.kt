package com.pampa.store.platform

import java.io.File

actual fun readFileBytes(path: String): ByteArray {
    return File(path).readBytes()
}
