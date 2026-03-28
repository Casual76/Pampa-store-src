package com.pampa.store.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.onFileDrop(
    extensions: List<String>,
    onFileDrop: (String) -> Unit
): Modifier {
    // No-op on Android, no built-in Desktop-like Drag and Drop API available without complex View interop
    return this
}
