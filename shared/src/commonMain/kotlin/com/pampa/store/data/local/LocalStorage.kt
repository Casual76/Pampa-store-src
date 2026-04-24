package com.pampa.store.data.local

import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.InstalledAppInfo
import com.pampa.store.data.model.PendingReleaseDraft
import com.pampa.store.data.model.ReleaseAutomationProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class StorePreferences(
    val defaultChannel: String = "STABLE",
    val storeUpdateChannel: String = "STABLE",
    val darkMode: Boolean = true,
    val downloadPath: String = "",
    val installedApps: Map<String, InstalledAppData> = emptyMap(),
    val adminPanelEnabled: Boolean = false,
    val storeUpdateAutoCheckEnabled: Boolean = false,
    val releaseAutomationProfiles: List<ReleaseAutomationProfile> = emptyList(),
    val pendingReleaseDrafts: List<PendingReleaseDraft> = emptyList(),
    val handledReleaseArtifacts: Map<String, Long> = emptyMap(),
    val githubToken: String = "",
    val repoOwner: String = "",
    val repoName: String = ""
)

@Serializable
data class InstalledAppData(
    val version: String,
    val channel: String,
    val installPath: String = "",
    val installedAt: Long = 0L
)

expect class PreferencesStorage {
    suspend fun load(): StorePreferences
    suspend fun save(preferences: StorePreferences)
}

class LocalStorage(private val storage: PreferencesStorage) {
    private val _preferences = MutableStateFlow(StorePreferences())
    val preferences: StateFlow<StorePreferences> = _preferences.asStateFlow()

    suspend fun init() {
        _preferences.value = storage.load()
    }

    suspend fun savePreferences(prefs: StorePreferences) {
        _preferences.value = prefs
        storage.save(prefs)
    }

    suspend fun setDarkMode(dark: Boolean) {
        savePreferences(_preferences.value.copy(darkMode = dark))
    }

    suspend fun setDefaultChannel(channel: Channel) {
        savePreferences(_preferences.value.copy(defaultChannel = channel.name))
    }

    suspend fun setStoreUpdateChannel(channel: Channel) {
        savePreferences(_preferences.value.copy(storeUpdateChannel = channel.name))
    }

    suspend fun setAdminPanelEnabled(enabled: Boolean) {
        savePreferences(_preferences.value.copy(adminPanelEnabled = enabled))
    }

    suspend fun setGitHubCredentials(token: String, owner: String, repo: String) {
        savePreferences(
            _preferences.value.copy(
                githubToken = token,
                repoOwner = owner,
                repoName = repo,
                adminPanelEnabled = token.isNotBlank()
            )
        )
    }

    suspend fun setStoreUpdateAutoCheckEnabled(enabled: Boolean) {
        savePreferences(_preferences.value.copy(storeUpdateAutoCheckEnabled = enabled))
    }

    suspend fun saveReleaseAutomationProfiles(profiles: List<ReleaseAutomationProfile>) {
        savePreferences(
            _preferences.value.copy(
                releaseAutomationProfiles = profiles.sortedBy { it.name.lowercase() }
            )
        )
    }

    suspend fun savePendingReleaseDrafts(drafts: List<PendingReleaseDraft>) {
        savePreferences(
            _preferences.value.copy(
                pendingReleaseDrafts = drafts.sortedWith(
                    compareByDescending<PendingReleaseDraft> { it.updatedAt }
                        .thenBy { it.profileName.lowercase() }
                )
            )
        )
    }

    suspend fun markReleaseArtifactsHandled(signatures: Collection<String>) {
        if (signatures.isEmpty()) return

        val current = _preferences.value
        val updatedHistory = current.handledReleaseArtifacts.toMutableMap()
        val now = currentTimeMillis()
        signatures
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { updatedHistory[it] = now }

        val normalizedHistory = updatedHistory.entries
            .sortedByDescending { it.value }
            .take(MAX_HANDLED_RELEASE_ARTIFACTS)
            .associate { it.toPair() }

        savePreferences(current.copy(handledReleaseArtifacts = normalizedHistory))
    }

    fun isReleaseArtifactHandled(signature: String): Boolean {
        return signature.isNotBlank() && signature in _preferences.value.handledReleaseArtifacts
    }

    suspend fun markInstalled(appId: String, version: String, channel: Channel, path: String) {
        val current = _preferences.value
        val updatedApps = current.installedApps.toMutableMap()
        updatedApps[appId] = InstalledAppData(
            version = version,
            channel = channel.name,
            installPath = path,
            installedAt = currentTimeMillis()
        )
        savePreferences(current.copy(installedApps = updatedApps))
    }

    suspend fun markUninstalled(appId: String) {
        val current = _preferences.value
        val updatedApps = current.installedApps.toMutableMap()
        updatedApps.remove(appId)
        savePreferences(current.copy(installedApps = updatedApps))
    }

    fun getInstalledInfo(appId: String): InstalledAppInfo? {
        val data = _preferences.value.installedApps[appId] ?: return null
        val installedChannel = Channel.entries.firstOrNull { it.name == data.channel } ?: Channel.STABLE
        return InstalledAppInfo(
            appId = appId,
            version = data.version,
            channel = installedChannel,
            installPath = data.installPath,
            installedAt = data.installedAt
        )
    }

    fun getDefaultChannel(): Channel {
        return Channel.entries.firstOrNull { it.name == _preferences.value.defaultChannel } ?: Channel.STABLE
    }

    fun getStoreUpdateChannel(): Channel {
        return Channel.entries.firstOrNull { it.name == _preferences.value.storeUpdateChannel } ?: Channel.STABLE
    }
}

expect fun currentTimeMillis(): Long

private const val MAX_HANDLED_RELEASE_ARTIFACTS = 500
