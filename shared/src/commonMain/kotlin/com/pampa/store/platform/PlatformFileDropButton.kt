package com.pampa.store.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformFileDropButton(
    emptyLabel: String,
    readyLabel: String,
    selectedPath: String?,
    extensions: List<String>,
    enabled: Boolean,
    onSelectClick: () -> Unit,
    onFileDropped: (String) -> Unit,
    modifier: Modifier = Modifier
)
