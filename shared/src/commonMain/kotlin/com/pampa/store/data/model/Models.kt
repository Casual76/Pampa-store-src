package com.pampa.store.data.model

import kotlinx.serialization.Serializable

const val STORE_APP_ID = "pampa-store"
const val DEFAULT_GITHUB_MANIFEST_PATH = "manifest.json"
const val DEFAULT_STORE_OWNER = "Casual76"
const val DEFAULT_STORE_REPO = "Pampa-store"

@Serializable
data class StoreManifest(
    val apps: List<AppEntry> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class StoreIndexManifest(
    val apps: List<StoreIndexEntry> = emptyList(),
    val storeReleases: StoreReleaseChannels = StoreReleaseChannels()
)

@Serializable
data class StoreIndexEntry(
    val id: String,
    val repoOwner: String,
    val repoName: String,
    val manifestPath: String = DEFAULT_GITHUB_MANIFEST_PATH,
    val ref: String = ""
)

@Serializable
data class GitHubRepositoryRef(
    val repoOwner: String = "",
    val repoName: String = "",
    val manifestPath: String = DEFAULT_GITHUB_MANIFEST_PATH,
    val ref: String = ""
)

@Serializable
data class AppManifest(
    val app: AppEntry
)

@Serializable
data class AppEntry(
    val id: String,
    val name: String,
    val description: String,
    val packageName: String = "",
    val iconUrl: String = "",
    val category: String = "General",
    val developer: String = "Pampa",
    val screenshots: List<String> = emptyList(),
    val repository: GitHubRepositoryRef = GitHubRepositoryRef(),
    val stable: VersionInfo? = null,
    val beta: VersionInfo? = null,
    val history: List<VersionInfo> = emptyList()  // Previous versions (kept for changelog)
)

@Serializable
data class VersionInfo(
    val version: String,
    val releaseDate: String,
    val changelog: String = "",
    val releaseTag: String,
    val apkAsset: String = "",
    val exeAsset: String = "",
    val sizeBytes: Long = 0L
)

@Serializable
data class StoreReleaseChannels(
    val stable: VersionInfo? = null,
    val beta: VersionInfo? = null
)

enum class Channel(val displayName: String) {
    STABLE("Stable"),
    BETA("Beta")
}

enum class OwnershipFilter {
    ALL,
    INSTALLED,
    UPDATABLE
}

enum class AppPlatform {
    ANDROID,
    WINDOWS
}

data class InstalledAppInfo(
    val appId: String,
    val version: String,
    val channel: Channel,
    val installPath: String = "",
    val installedAt: Long = 0L
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Verifying(val message: String = "Verifying download") : DownloadState()
    data class AwaitingUserAction(val message: String) : DownloadState()
    data class Installing(val message: String) : DownloadState()
    data class Installed(val filePath: String) : DownloadState()
    data class Error(
        val message: String,
        val details: DownloadFailureDetails? = null
    ) : DownloadState()
}

data class DownloadFailureDetails(
    val statusCode: Int? = null,
    val contentType: String = "",
    val sourceUrl: String = "",
    val responsePreview: String = "",
    val downloadedBytes: Long? = null,
    val expectedBytes: Long? = null
)

sealed class InstallEvent {
    data object Preparing : InstallEvent()
    data class AwaitingUserAction(val message: String) : InstallEvent()
    data class Installing(val message: String) : InstallEvent()
    data object Success : InstallEvent()
    data class Failure(val code: String, val message: String) : InstallEvent()
}

data class ApkValidationMetadata(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val signerDn: String = "",
    val signerSha256: String = "",
    val supportedAbis: List<String> = emptyList(),
    val compressedSizeBytes: Long = 0L,
    val installHint: String = "",
    val debuggable: Boolean = false,
    val testOnly: Boolean = false
)

sealed class ApkValidationResult {
    data class Valid(val metadata: ApkValidationMetadata) : ApkValidationResult()
    data class Invalid(
        val issues: List<String>,
        val metadata: ApkValidationMetadata? = null
    ) : ApkValidationResult()
    data class Unsupported(val message: String) : ApkValidationResult()
}

data class AppDisplayInfo(
    val entry: AppEntry,
    val installedVersion: String? = null,
    val installedChannel: Channel? = null,
    val hasUpdate: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle
)

data class ReleaseAssetSource(
    val repository: GitHubRepositoryRef,
    val releaseTag: String,
    val assetName: String
)

data class StoreUpdateInfo(
    val repository: GitHubRepositoryRef,
    val versionInfo: VersionInfo
)

data class StoreUpdateState(
    val isChecking: Boolean = false,
    val availableUpdate: StoreUpdateInfo? = null,
    val lastCheckedAt: Long? = null,
    val error: String? = null
)

fun ApkValidationResult.isPublishable(): Boolean = this is ApkValidationResult.Valid

fun GitHubRepositoryRef.isConfigured(): Boolean {
    return repoOwner.isNotBlank() && repoName.isNotBlank()
}

fun GitHubRepositoryRef.normalized(defaultPath: String = DEFAULT_GITHUB_MANIFEST_PATH): GitHubRepositoryRef {
    return copy(
        repoOwner = repoOwner.trim(),
        repoName = repoName.trim(),
        manifestPath = manifestPath.trim().ifBlank { defaultPath },
        ref = ref.trim()
    )
}

fun StoreIndexEntry.toRepositoryRef(): GitHubRepositoryRef {
    return GitHubRepositoryRef(
        repoOwner = repoOwner,
        repoName = repoName,
        manifestPath = manifestPath,
        ref = ref
    ).normalized()
}

fun AppEntry.toStoreIndexEntry(): StoreIndexEntry {
    val normalizedRepository = repository.normalized()
    return StoreIndexEntry(
        id = id.trim(),
        repoOwner = normalizedRepository.repoOwner,
        repoName = normalizedRepository.repoName,
        manifestPath = normalizedRepository.manifestPath,
        ref = normalizedRepository.ref
    )
}

fun AppEntry.withRepositoryFallback(
    defaultOwner: String,
    defaultRepo: String,
    defaultManifestPath: String = DEFAULT_GITHUB_MANIFEST_PATH
): AppEntry {
    val repositoryRef = if (repository.isConfigured()) {
        repository.normalized(defaultManifestPath)
    } else {
        GitHubRepositoryRef(
            repoOwner = defaultOwner,
            repoName = defaultRepo,
            manifestPath = defaultManifestPath
        )
    }
    return copy(repository = repositoryRef)
}

fun StoreReleaseChannels.versionFor(channel: Channel): VersionInfo? {
    return when (channel) {
        Channel.STABLE -> stable
        Channel.BETA -> beta ?: stable
    }
}
