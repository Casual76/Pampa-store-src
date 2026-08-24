package com.pampa.store.data

import kotlinx.serialization.Serializable

const val STORE_APP_ID = "pampa-store"
const val STORE_OWNER = "Casual76"
const val STORE_REPOSITORY = "Pampa-store"
const val STORE_MANIFEST_PATH = "manifest.json"

@Serializable
data class RepositoryRef(
    val repoOwner: String = "",
    val repoName: String = "",
    val manifestPath: String = STORE_MANIFEST_PATH,
    val ref: String = "",
) {
    val branch: String get() = ref.ifBlank { "main" }
    val isConfigured: Boolean get() = repoOwner.isNotBlank() && repoName.isNotBlank()
}

@Serializable
data class VersionInfo(
    val version: String = "",
    val releaseDate: String = "",
    val changelog: String = "",
    val releaseTag: String = "",
    val apkAsset: String = "",
    val sizeBytes: Long = 0L,
)

@Serializable
data class AppEntry(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val packageName: String = "",
    val iconUrl: String = "",
    val category: String = "Generale",
    val developer: String = "Pampa",
    val screenshots: List<String> = emptyList(),
    val repository: RepositoryRef = RepositoryRef(),
    val stable: VersionInfo? = null,
    val beta: VersionInfo? = null,
    val history: List<VersionInfo> = emptyList(),
)

@Serializable
data class StoreManifest(
    val apps: List<AppEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class StoreIndexEntry(
    val id: String = "",
    val repoOwner: String = "",
    val repoName: String = "",
    val manifestPath: String = STORE_MANIFEST_PATH,
    val ref: String = "",
) {
    fun repository(): RepositoryRef = RepositoryRef(repoOwner, repoName, manifestPath, ref)
}

@Serializable
data class StoreReleaseChannels(
    val stable: VersionInfo? = null,
    val beta: VersionInfo? = null,
)

@Serializable
data class StoreIndexManifest(
    val apps: List<StoreIndexEntry> = emptyList(),
    val storeReleases: StoreReleaseChannels = StoreReleaseChannels(),
)

@Serializable
data class AppManifest(val app: AppEntry = AppEntry())

@Serializable
data class CachedCatalog(
    val apps: List<AppEntry> = emptyList(),
    val storeReleases: StoreReleaseChannels = StoreReleaseChannels(),
    val warnings: List<String> = emptyList(),
    val savedAt: Long = 0L,
)

enum class Channel(val label: String) {
    STABLE("Stable"),
    BETA("Beta"),
}

enum class OwnershipFilter(val label: String) {
    ALL("Tutte"),
    INSTALLED("Installate"),
    UPDATABLE("Aggiornabili"),
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Chiaro"),
    DARK("Scuro"),
}

enum class AccentSource(val label: String) {
    PRESET("Preset Pampa"),
    DYNAMIC("Material You"),
}

enum class AccentPresetId(val storageName: String, val label: String) {
    NEBULA_76("nebula76", "Nebula 76"),
    ION_BLUE("ionBlue", "Ion Blue"),
    AURORA_MINT("auroraMint", "Aurora Mint"),
    SOLAR_AMBER("solarAmber", "Solar Amber"),
    CORAL_PULSE("coralPulse", "Coral Pulse"),
}

@Serializable
data class StorePreferences(
    val themeMode: String = AppThemeMode.SYSTEM.name,
    val accentSource: String = AccentSource.PRESET.name,
    val accentPreset: String = AccentPresetId.NEBULA_76.storageName,
    val defaultChannel: String = Channel.STABLE.name,
    val storeUpdateChannel: String = Channel.STABLE.name,
    val autoCheckStoreUpdates: Boolean = true,
)

data class InstallPrerequisites(
    val canInstallPackages: Boolean = true,
    val message: String? = null,
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float, val downloaded: Long, val total: Long) : DownloadState
    data class Verifying(val message: String) : DownloadState
    data class AwaitingUser(val message: String) : DownloadState
    data class Installing(val message: String) : DownloadState
    data class Installed(val version: String) : DownloadState
    data class Error(val message: String) : DownloadState
}

sealed interface InstallEvent {
    data object Preparing : InstallEvent
    data class AwaitingUserAction(val message: String) : InstallEvent
    data class Installing(val message: String) : InstallEvent
    data object Success : InstallEvent
    data class Failure(val code: String, val message: String) : InstallEvent
}

data class StoreApp(
    val entry: AppEntry,
    val installedVersion: String? = null,
    val installedChannel: Channel? = null,
    val hasUpdate: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
)

data class StoreUpdate(
    val repository: RepositoryRef,
    val version: VersionInfo,
)

data class StoreUiState(
    val apps: List<StoreApp> = emptyList(),
    val storeReleases: StoreReleaseChannels = StoreReleaseChannels(),
    val preferences: StorePreferences = StorePreferences(),
    val prerequisites: InstallPrerequisites = InstallPrerequisites(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isUsingCache: Boolean = false,
    val lastUpdatedAt: Long? = null,
    val warning: String? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val ownershipFilter: OwnershipFilter = OwnershipFilter.ALL,
    val storeUpdate: StoreUpdate? = null,
) {
    val installedCount: Int get() = apps.count { it.installedVersion != null }
    val updateCount: Int get() = apps.count(StoreApp::hasUpdate)
    val betaCount: Int get() = apps.count { it.entry.beta != null }
    val categories: List<String> get() = apps.map { it.entry.category }.distinct().sorted()

    val visibleApps: List<StoreApp>
        get() = apps.filter { app ->
            val query = searchQuery.trim()
            val matchesQuery = query.isEmpty() ||
                app.entry.name.contains(query, ignoreCase = true) ||
                app.entry.description.contains(query, ignoreCase = true) ||
                app.entry.developer.contains(query, ignoreCase = true)
            val matchesCategory = selectedCategory == null || app.entry.category == selectedCategory
            val matchesOwnership = when (ownershipFilter) {
                OwnershipFilter.ALL -> true
                OwnershipFilter.INSTALLED -> app.installedVersion != null
                OwnershipFilter.UPDATABLE -> app.hasUpdate
            }
            matchesQuery && matchesCategory && matchesOwnership
        }
}

fun AppEntry.versionFor(channel: Channel): VersionInfo? = when (channel) {
    Channel.STABLE -> stable
    Channel.BETA -> beta ?: stable
}

fun StoreReleaseChannels.versionFor(channel: Channel): VersionInfo? = when (channel) {
    Channel.STABLE -> stable
    Channel.BETA -> beta ?: stable
}

fun RepositoryRef.rawUrl(): String =
    "https://raw.githubusercontent.com/$repoOwner/$repoName/$branch/$manifestPath"

fun RepositoryRef.releaseAssetUrl(version: VersionInfo): String =
    "https://github.com/$repoOwner/$repoName/releases/download/${version.releaseTag}/${version.apkAsset}"
