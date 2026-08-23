package com.pampa.store.ui.screens


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.PendingReleaseDraft
import com.pampa.store.data.model.PendingReleaseDraftState
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.model.ReleaseAutomationTarget
import com.pampa.store.data.model.assetsSummary
import com.pampa.store.platform.rememberFilePicker
import com.pampa.store.ui.components.StatusBadge
import com.pampa.store.ui.theme.ContinuousCornerShape
import com.pampa.store.ui.theme.AccentGreen
import com.pampa.store.ui.theme.AccentOrange
import kotlinx.coroutines.launch

@Composable
internal fun AutomationOverviewSection(
    apps: List<AppEntry>,
    profiles: List<ReleaseAutomationProfile>,
    drafts: List<PendingReleaseDraft>,
    settingsEnabled: Boolean,
    publishEnabled: Boolean,
    isSaving: Boolean,
    onSaveProfile: (ReleaseAutomationProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onToggleProfile: (String, Boolean) -> Unit,
    onPublishDraft: (String, Channel) -> Unit,
    onDeferDraft: (String) -> Unit,
    onDismissDraft: (String) -> Unit,
    onRetryDraft: (String) -> Unit
) {
    val filePicker = rememberFilePicker()
    val scope = rememberCoroutineScope()
    var editingProfile by remember { mutableStateOf<ReleaseAutomationProfile?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(
            title = "Automazione release",
            icon = Icons.Filled.AutoAwesome,
            accent = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "Collega una volta le cartelle di output e lascia che Pampa Store prepari da solo le bozze di publish.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!publishEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Puoi gia' configurare i profili. La pubblicazione restera' disattivata finche' non configuri le credenziali GitHub.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    editingProfile = ReleaseAutomationProfile(
                        id = "",
                        name = "",
                        target = ReleaseAutomationTarget.Store,
                        enabled = true
                    )
                },
                enabled = settingsEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Nuovo profilo")
            }
            Spacer(Modifier.height(14.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = "Nessun profilo configurato.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                profiles.forEach { profile ->
                    AutomationProfileCard(
                        profile = profile,
                        enabled = settingsEnabled,
                        onEdit = { editingProfile = profile },
                        onDelete = { onDeleteProfile(profile.id) },
                        onToggle = { onToggleProfile(profile.id, it) }
                    )
                }
            }
        }

        SectionCard(
            title = "Bozze rilevate",
            icon = Icons.Filled.Sync,
            accent = AccentOrange
        ) {
            if (drafts.isEmpty()) {
                Text(
                    text = "Nessuna bozza in coda.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                drafts.forEach { draft ->
                    AutomationDraftCard(
                        draft = draft,
                        settingsEnabled = settingsEnabled,
                        publishEnabled = publishEnabled,
                        isSaving = isSaving,
                        onPublishDraft = onPublishDraft,
                        onDeferDraft = onDeferDraft,
                        onDismissDraft = onDismissDraft,
                        onRetryDraft = onRetryDraft
                    )
                }
            }
        }
    }

    editingProfile?.let { profile ->
        AutomationProfileDialog(
            initialProfile = profile,
            apps = apps,
            enabled = settingsEnabled,
            onDismiss = { editingProfile = null },
            onSave = {
                onSaveProfile(it)
                editingProfile = null
            },
            onPickApkDirectory = { onPicked ->
                scope.launch {
                    onPicked(filePicker.pickDirectory("Seleziona cartella APK"))
                }
            },
            onPickWindowsDirectory = { onPicked ->
                scope.launch {
                    onPicked(filePicker.pickDirectory("Seleziona cartella installer Windows"))
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent)
                Text(title, fontWeight = FontWeight.Bold, color = accent)
            }
            content()
        }
    }
}

@Composable
private fun AutomationProfileCard(
    profile: ReleaseAutomationProfile,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ContinuousCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold)
                    Text(
                        text = profileTargetLabel(profile.target),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = profile.enabled,
                    onCheckedChange = { onToggle(it) },
                    enabled = enabled
                )
                IconButton(onClick = onEdit, enabled = enabled) {
                    Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                }
            }

            profile.apkDirectory.takeIf { it.isNotBlank() }?.let {
                InfoLine("APK", it)
            }
            profile.windowsDirectory.takeIf { it.isNotBlank() }?.let {
                InfoLine("Windows", it)
            }
            profile.lastDetectedVersion.takeIf { it.isNotBlank() }?.let {
                StatusBadge(
                    text = "Ultima versione $it",
                    background = AccentGreen.copy(alpha = 0.16f),
                    contentColor = AccentGreen
                )
            }
            profile.lastError.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AutomationDraftCard(
    draft: PendingReleaseDraft,
    settingsEnabled: Boolean,
    publishEnabled: Boolean,
    isSaving: Boolean,
    onPublishDraft: (String, Channel) -> Unit,
    onDeferDraft: (String) -> Unit,
    onDismissDraft: (String) -> Unit,
    onRetryDraft: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ContinuousCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${draft.profileName} v${draft.version}", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${profileTargetLabel(draft.target)} · ${draft.assetsSummary()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (draft.state) {
                    PendingReleaseDraftState.READY -> StatusBadge(
                        text = "Pronta",
                        background = AccentGreen.copy(alpha = 0.16f),
                        contentColor = AccentGreen
                    )

                    PendingReleaseDraftState.DEFERRED -> StatusBadge(
                        text = "Più tardi",
                        background = AccentOrange.copy(alpha = 0.16f),
                        contentColor = AccentOrange
                    )

                    PendingReleaseDraftState.FAILED -> StatusBadge(
                        text = "Errore",
                        background = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = draft.changelog,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            draft.errorMessage.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onPublishDraft(draft.id, Channel.STABLE) },
                    enabled = publishEnabled && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Stable")
                }
                Button(
                    onClick = { onPublishDraft(draft.id, Channel.BETA) },
                    enabled = publishEnabled && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Beta")
                }
                if (draft.state == PendingReleaseDraftState.READY) {
                    OutlinedButton(
                        onClick = { onDeferDraft(draft.id) },
                        enabled = settingsEnabled && !isSaving
                    ) {
                        Icon(Icons.Filled.HourglassBottom, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Più tardi")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onRetryDraft(draft.id) },
                        enabled = settingsEnabled && !isSaving
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Riprendi")
                    }
                }
                OutlinedButton(
                    onClick = { onDismissDraft(draft.id) },
                    enabled = settingsEnabled && !isSaving
                ) {
                    Text("Scarta")
                }
            }
        }
    }
}

@Composable
private fun AutomationProfileDialog(
    initialProfile: ReleaseAutomationProfile,
    apps: List<AppEntry>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReleaseAutomationProfile) -> Unit,
    onPickApkDirectory: (((String?) -> Unit)) -> Unit,
    onPickWindowsDirectory: (((String?) -> Unit)) -> Unit
) {
    var name by remember(initialProfile.id) { mutableStateOf(initialProfile.name) }
    var targetType by remember(initialProfile.id) {
        mutableStateOf(
            when (initialProfile.target) {
                ReleaseAutomationTarget.Store -> ReleaseAutomationTarget.Store
                is ReleaseAutomationTarget.App -> ReleaseAutomationTarget.App("")
            }
        )
    }
    var targetAppId by remember(initialProfile.id) {
        mutableStateOf((initialProfile.target as? ReleaseAutomationTarget.App)?.appId.orEmpty())
    }
    var apkDirectory by remember(initialProfile.id) { mutableStateOf(initialProfile.apkDirectory) }
    var windowsDirectory by remember(initialProfile.id) { mutableStateOf(initialProfile.windowsDirectory) }
    var profileEnabled by remember(initialProfile.id) { mutableStateOf(initialProfile.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProfile.id.isBlank()) "Nuovo profilo automazione" else "Modifica profilo")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AutomationTextField("Nome", name, { name = it }, "es. Store release")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = targetType == ReleaseAutomationTarget.Store,
                        onClick = { targetType = ReleaseAutomationTarget.Store },
                        label = { Text("Store") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = targetType is ReleaseAutomationTarget.App,
                        onClick = { targetType = ReleaseAutomationTarget.App(targetAppId) },
                        label = { Text("App") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (targetType is ReleaseAutomationTarget.App) {
                    AutomationTextField("App ID", targetAppId, { targetAppId = it }, "es. codex")
                    if (apps.isNotEmpty()) {
                        Text(
                            text = "App disponibili: ${apps.take(6).joinToString(", ") { it.id }}${if (apps.size > 6) "..." else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DirectoryPickerField(
                    label = "Cartella APK",
                    value = apkDirectory,
                    enabled = enabled,
                    onPick = { onPickApkDirectory { picked -> apkDirectory = picked.orEmpty() } }
                )
                DirectoryPickerField(
                    label = "Cartella Windows",
                    value = windowsDirectory,
                    enabled = enabled,
                    onPick = { onPickWindowsDirectory { picked -> windowsDirectory = picked.orEmpty() } }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(checked = profileEnabled, onCheckedChange = { profileEnabled = it }, enabled = enabled)
                    Text("Profilo attivo")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialProfile.copy(
                            name = name,
                            target = if (targetType == ReleaseAutomationTarget.Store) {
                                ReleaseAutomationTarget.Store
                            } else {
                                ReleaseAutomationTarget.App(targetAppId)
                            },
                            apkDirectory = apkDirectory,
                            windowsDirectory = windowsDirectory,
                            enabled = profileEnabled
                        )
                    )
                },
                enabled = enabled
            ) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun DirectoryPickerField(
    label: String,
    value: String,
    enabled: Boolean,
    onPick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = onPick, enabled = enabled) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (value.isBlank()) "Seleziona cartella" else "Cambia cartella")
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AutomationTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        shape = ContinuousCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", fontWeight = FontWeight.Medium)
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun profileTargetLabel(target: ReleaseAutomationTarget): String {
    return when (target) {
        ReleaseAutomationTarget.Store -> "Store"
        is ReleaseAutomationTarget.App -> "App ${target.appId}"
    }
}
