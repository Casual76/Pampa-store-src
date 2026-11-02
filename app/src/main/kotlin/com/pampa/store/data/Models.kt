package com.pampa.store.data

import kotlinx.serialization.Serializable

const val STORE_APP_ID = "pampa-store"
const val STORE_APP_NAME = "Pampa Store"
const val STORE_INDEX_OWNER = "Casual76"
const val STORE_INDEX_REPOSITORY = "Pampa-store"
const val STORE_RELEASE_OWNER = STORE_INDEX_OWNER
const val STORE_RELEASE_REPOSITORY = STORE_INDEX_REPOSITORY
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
    val repository: RepositoryRef = RepositoryRef(
        repoOwner = STORE_RELEASE_OWNER,
        repoName = STORE_RELEASE_REPOSITORY,
    ),
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

/**
 * I preset dell'accento, semi compresi.
 *
 * I colori stanno qui e non accanto al tema perche' la lista esisteva in due posti — l'enum che la
 * preferenza salva e la lista che il tema legge — e due liste che devono restare uguali sono una
 * lista che diverge al primo preset aggiunto. Sono `Long` e non `Color` di proposito: questo strato
 * non conosce Compose, e non e' il momento di fargliela conoscere per cinque costanti.
 *
 * `FluidTheme` deriva l'intera palette dal seme, quindi alzare la croma alza la saturazione di tutta
 * l'app senza toccare i rapporti di contrasto, che li garantisce la scala tonale.
 */
enum class AccentPresetId(
    val storageName: String,
    val label: String,
    val lightSeed: Long,
    val darkSeed: Long,
) {
    NEBULA_76("nebula76", "Nebula 76", 0xFF5B3FF0, 0xFFA98CFF),
    ION_BLUE("ionBlue", "Ion Blue", 0xFF0A6BFF, 0xFF5FB0FF),
    AURORA_MINT("auroraMint", "Aurora Mint", 0xFF00A383, 0xFF3BEFC0),
    SOLAR_AMBER("solarAmber", "Solar Amber", 0xFFE07800, 0xFFFFC44D),
    CORAL_PULSE("coralPulse", "Coral Pulse", 0xFFE83F5E, 0xFFFF7A93),
    PLASMA_MAGENTA("plasmaMagenta", "Plasma Magenta", 0xFFD10F86, 0xFFFF6ED0),
    CYAN_TIDE("cyanTide", "Cyan Tide", 0xFF0093B8, 0xFF3FDCF5),
}

@Serializable
data class StorePreferences(
    val themeMode: String = AppThemeMode.SYSTEM.name,
    val accentSource: String = AccentSource.PRESET.name,
    val accentPreset: String = AccentPresetId.NEBULA_76.storageName,
    val defaultChannel: String = Channel.STABLE.name,
    val storeUpdateChannel: String = Channel.STABLE.name,
    val autoCheckStoreUpdates: Boolean = true,
    /** Sfondi neri nel tema scuro, per gli schermi OLED. Dalla 1.9.0; spento di serie. */
    val pureBlack: Boolean = false,
    /** Le vibrazioni dell'engine, tutte. Dalla 1.9.0; accese di serie, com'erano prima dell'interruttore. */
    val hapticsEnabled: Boolean = true,
)

data class InstallPrerequisites(
    val canInstallPackages: Boolean = true,
    val message: String? = null,
)

sealed interface DownloadState {
    data object Idle : DownloadState

    /**
     * Accodata, in attesa del proprio turno.
     *
     * Esiste perche' gli aggiornamenti si eseguono uno alla volta: il PackageInstaller non
     * privilegiato apre un prompt di sistema per ogni APK, e due prompt sovrapposti sono
     * un'installazione persa. Un'app in attesa non e' [Idle] — la differenza si vede sulla riga.
     */
    data object Queued : DownloadState
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
    /**
     * L'ultima versione sul canale da cui l'app e' installata.
     *
     * Ricavabile da [entry] e [installedChannel], ma ricavarla ogni volta significa che la
     * schermata degli aggiornamenti, la notifica di sistema e la coda la ricalcolano ognuna per
     * conto suo — e la definizione di "a cosa aggiorno" e' precisamente la cosa che non deve
     * esistere in tre copie.
     */
    val availableVersion: String? = null,
    val hasUpdate: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
)

data class StoreUpdate(
    val repository: RepositoryRef,
    val version: VersionInfo,
)

/**
 * Un'installazione che aspetta che l'app venga tolta di mezzo.
 *
 * Android rifiuta di sovrascrivere un pacchetto con uno di versione inferiore
 * (`INSTALL_FAILED_VERSION_DOWNGRADE`), e la deroga è riservata al sistema: per un installer come
 * questo l'unica strada verso una versione precedente passa dalla disinstallazione. Quindi il
 * ritorno indietro è **due** operazioni, con in mezzo un dialogo di sistema e i dati dell'app che se
 * ne vanno — e questo è ciò che se lo ricorda mentre la persona è dentro quel dialogo.
 */
data class PendingReinstall(
    val appId: String,
    val appName: String,
    val packageName: String,
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
    val updateQueue: List<QueueEntry> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val pendingReinstall: PendingReinstall? = null,
) {
    val installedCount: Int get() = apps.count { it.installedVersion != null }
    val updateCount: Int get() = apps.count(StoreApp::hasUpdate)
    val betaCount: Int get() = apps.count { it.entry.beta != null }
    val categories: List<String> get() = apps.map { it.entry.category }.distinct().sorted()

    val updatableApps: List<StoreApp> get() = apps.filter(StoreApp::hasUpdate)

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
