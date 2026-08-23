package com.pampa.store.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pampa.store.ui.theme.ContinuousCornerShape

@Composable
actual fun PlatformFileDropButton(
    emptyLabel: String,
    readyLabel: String,
    selectedPath: String?,
    extensions: List<String>,
    enabled: Boolean,
    onSelectClick: () -> Unit,
    onFileDropped: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = onSelectClick,
        modifier = modifier.heightIn(min = 84.dp),
        enabled = enabled,
        shape = ContinuousCornerShape(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (selectedPath == null) emptyLabel else readyLabel,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = selectedPath?.let(::fileNameOnly) ?: "Fai clic per selezionare",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun fileNameOnly(path: String): String {
    return path.substringAfterLast('/').substringAfterLast('\\')
}
