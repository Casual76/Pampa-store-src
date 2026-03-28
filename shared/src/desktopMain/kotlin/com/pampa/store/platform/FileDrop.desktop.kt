package com.pampa.store.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.onFileDrop(
    extensions: List<String>,
    onFileDrop: (String) -> Unit
): Modifier {
    return this
}
