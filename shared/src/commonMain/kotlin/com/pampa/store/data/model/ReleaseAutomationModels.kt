package com.pampa.store.data.model

import kotlinx.serialization.Serializable

@Serializable
sealed class ReleaseAutomationTarget {
    @Serializable
    data object Store : ReleaseAutomationTarget()

    @Serializable
    data class App(val appId: String = "") : ReleaseAutomationTarget()
}

@Serializable
enum class ReleaseArtifactKind {
    APK,
    WINDOWS
}

@Serializable
enum class PendingReleaseDraftState {
    READY,
    DEFERRED,
    FAILED
}

@Serializable
data class ReleaseAutomationProfile(
    val id: String,
    val name: String,
    val target: ReleaseAutomationTarget = ReleaseAutomationTarget.Store,
    val apkDirectory: String = "",
    val windowsDirectory: String = "",
    val enabled: Boolean = true,
    val lastError: String = "",
    val lastDetectedVersion: String = "",
    val lastDetectedAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class DetectedArtifact(
    val kind: ReleaseArtifactKind,
    val path: String,
    val fileName: String,
    val version: String,
    val signature: String,
    val sizeBytes: Long = 0L,
    val lastModifiedAt: Long = 0L,
    val packageName: String = ""
)

@Serializable
data class PendingReleaseDraft(
    val id: String,
    val profileId: String,
    val profileName: String,
    val target: ReleaseAutomationTarget = ReleaseAutomationTarget.Store,
    val version: String,
    val changelog: String = "",
    val apkArtifact: DetectedArtifact? = null,
    val windowsArtifact: DetectedArtifact? = null,
    val state: PendingReleaseDraftState = PendingReleaseDraftState.READY,
    val errorMessage: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

fun ReleaseAutomationProfile.isConfigured(): Boolean {
    return apkDirectory.isNotBlank() || windowsDirectory.isNotBlank()
}

fun ReleaseAutomationTarget.displayName(): String {
    return when (this) {
        ReleaseAutomationTarget.Store -> "Store"
        is ReleaseAutomationTarget.App -> "App ${appId.ifBlank { "sconosciuta" }}"
    }
}

fun PendingReleaseDraft.hasSignature(signature: String): Boolean {
    if (signature.isBlank()) return false
    return apkArtifact?.signature == signature || windowsArtifact?.signature == signature
}

fun PendingReleaseDraft.assetsSummary(): String {
    val labels = buildList {
        if (apkArtifact != null) add("APK")
        if (windowsArtifact != null) add("Windows")
    }
    return if (labels.isEmpty()) "Nessun asset" else labels.joinToString(" + ")
}
