package com.pampa.store.platform

expect fun computeFileSha256(path: String): String?

expect fun getFileSize(path: String): Long?

expect fun deleteFileIfExists(path: String): Boolean

expect fun moveFileReplacing(sourcePath: String, targetPath: String)
