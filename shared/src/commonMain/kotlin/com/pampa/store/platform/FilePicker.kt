package com.pampa.store.platform

expect class FilePicker {
    suspend fun pickFile(title: String, allowedExtensions: List<String> = emptyList()): String?
    suspend fun pickDirectory(title: String): String?
}

@androidx.compose.runtime.Composable
expect fun rememberFilePicker(): FilePicker
