package com.pampa.store.automation

import com.pampa.store.data.model.DetectedArtifact
import com.pampa.store.data.model.ReleaseArtifactKind
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.model.ApkValidationResult
import com.pampa.store.data.model.isConfigured
import com.pampa.store.platform.ApkValidationService
import com.pampa.store.platform.createApkValidationService
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopReleaseAutomationService internal constructor(
    private val apkValidationService: ApkValidationService = createApkValidationService(),
    private val windowsVersionReader: WindowsVersionReader = PowerShellWindowsVersionReader(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scanIntervalMs: Long = 3000L,
    private val stableWindowMs: Long = 1500L,
    private val watchServiceFactory: () -> WatchService = { FileSystems.getDefault().newWatchService() }
) : ReleaseAutomationService {

    constructor() : this(
        apkValidationService = createApkValidationService(),
        windowsVersionReader = PowerShellWindowsVersionReader()
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableEvents = MutableSharedFlow<ReleaseAutomationEvent>(extraBufferCapacity = 64)
    private val scanRequests = Channel<Unit>(capacity = Channel.CONFLATED)

    override val events: Flow<ReleaseAutomationEvent> = mutableEvents.asSharedFlow()

    private var started = false
    private var profiles: List<ReleaseAutomationProfile> = emptyList()
    private var watchService: WatchService? = null
    private var watchJob: Job? = null
    private var scanJob: Job? = null
    private var periodicJob: Job? = null

    private val observations = mutableMapOf<String, CandidateObservation>()
    private val emittedFingerprints = mutableMapOf<String, String>()
    private val emittedProfileErrors = mutableMapOf<String, String?>()

    override fun start() {
        if (started) return
        started = true

        scanJob = serviceScope.launch {
            for (@Suppress("ControlFlowWithEmptyBody") ignored in scanRequests) {
                scanProfiles()
            }
        }

        periodicJob = serviceScope.launch {
            while (true) {
                delay(scanIntervalMs)
                requestScan()
            }
        }

        serviceScope.launch {
            rebuildWatchService()
            requestScan()
        }
    }

    override fun updateProfiles(profiles: List<ReleaseAutomationProfile>) {
        this.profiles = profiles.filter { it.isConfigured() }
        if (!started) return

        serviceScope.launch {
            rebuildWatchService()
            requestScan()
        }
    }

    override fun stop() {
        watchJob?.cancel()
        scanJob?.cancel()
        periodicJob?.cancel()
        runCatching { watchService?.close() }
        serviceScope.cancel()
    }

    private suspend fun rebuildWatchService() = withContext(dispatcher) {
        watchJob?.cancel()
        runCatching { watchService?.close() }

        val newWatchService = watchServiceFactory()
        val directories = profiles
            .filter { it.enabled }
            .flatMap { profile ->
                listOf(profile.apkDirectory, profile.windowsDirectory)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }
            .map(::File)
            .filter { it.exists() && it.isDirectory }
            .distinctBy { it.absolutePath.lowercase() }

        directories.forEach { directory ->
            runCatching {
                directory.toPath().register(
                    newWatchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
            }
        }

        watchService = newWatchService
        watchJob = serviceScope.launch {
            while (true) {
                try {
                    val key = newWatchService.take()
                    key.pollEvents()
                    key.reset()
                    requestScan()
                } catch (_: ClosedWatchServiceException) {
                    break
                }
            }
        }
    }

    private fun requestScan() {
        scanRequests.trySend(Unit)
    }

    private suspend fun scanProfiles() {
        profiles
            .filter { it.enabled && it.isConfigured() }
            .forEach { profile ->
                val errors = buildList {
                    scanDirectory(profile, ReleaseArtifactKind.APK, profile.apkDirectory, setOf("apk"))?.let(::add)
                    scanDirectory(profile, ReleaseArtifactKind.WINDOWS, profile.windowsDirectory, setOf("exe", "msi"))?.let(::add)
                }
                emitProfileError(
                    profileId = profile.id,
                    message = errors.joinToString("\n").ifBlank { null }
                )
            }
    }

    private suspend fun scanDirectory(
        profile: ReleaseAutomationProfile,
        kind: ReleaseArtifactKind,
        directoryPath: String,
        allowedExtensions: Set<String>
    ): String? {
        val normalizedDirectory = directoryPath.trim()
        if (normalizedDirectory.isBlank()) return null

        val directory = File(normalizedDirectory)
        if (!directory.exists() || !directory.isDirectory) {
            return "Cartella non trovata per ${profile.name}: $normalizedDirectory"
        }

        val candidateFile = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.lowercase() in allowedExtensions }
            .maxWithOrNull(compareBy<File>({ it.lastModified() }, { it.name.lowercase() }))
            ?: return null

        val observationKey = "${profile.id}|${kind.name}"
        val fingerprint = candidateFile.fingerprint()
        val now = System.currentTimeMillis()
        val currentObservation = observations[observationKey]
        if (currentObservation?.fingerprint != fingerprint) {
            observations[observationKey] = CandidateObservation(
                fingerprint = fingerprint,
                firstSeenAt = now
            )
            return null
        }
        if (now - currentObservation.firstSeenAt < stableWindowMs) {
            return null
        }
        if (emittedFingerprints[observationKey] == fingerprint) {
            return null
        }

        return when (val inspected = inspectArtifact(candidateFile, kind)) {
            is ArtifactInspection.Success -> {
                mutableEvents.emit(
                    ReleaseAutomationEvent.ArtifactDetected(
                        profileId = profile.id,
                        artifact = inspected.artifact
                    )
                )
                emittedFingerprints[observationKey] = fingerprint
                null
            }

            is ArtifactInspection.Failure -> inspected.message
        }
    }

    private suspend fun inspectArtifact(
        file: File,
        kind: ReleaseArtifactKind
    ): ArtifactInspection {
        val signature = file.fingerprint()
        return when (kind) {
            ReleaseArtifactKind.APK -> inspectApk(file, signature)
            ReleaseArtifactKind.WINDOWS -> inspectWindowsArtifact(file, signature)
        }
    }

    private suspend fun inspectApk(file: File, signature: String): ArtifactInspection {
        return when (val validation = apkValidationService.validate(file.absolutePath)) {
            is ApkValidationResult.Valid -> {
                val versionName = validation.metadata.versionName.trim()
                if (versionName.isBlank()) {
                    ArtifactInspection.Failure("Versione APK non leggibile in ${file.name}.")
                } else {
                    ArtifactInspection.Success(
                        DetectedArtifact(
                            kind = ReleaseArtifactKind.APK,
                            path = file.absolutePath,
                            fileName = file.name,
                            version = versionName,
                            signature = signature,
                            sizeBytes = file.length(),
                            lastModifiedAt = file.lastModified(),
                            packageName = validation.metadata.packageName
                        )
                    )
                }
            }

            is ApkValidationResult.Invalid -> ArtifactInspection.Failure(
                validation.issues.firstOrNull() ?: "APK non valido: ${file.name}"
            )

            is ApkValidationResult.Unsupported -> ArtifactInspection.Failure(validation.message)
        }
    }

    private suspend fun inspectWindowsArtifact(file: File, signature: String): ArtifactInspection {
        val version = when {
            file.extension.equals("exe", ignoreCase = true) ->
                windowsVersionReader.readVersion(file).orEmpty().ifBlank {
                    extractVersionFromFileName(file.name).orEmpty()
                }

            else -> extractVersionFromFileName(file.name).orEmpty()
        }.trim()

        if (version.isBlank()) {
            return ArtifactInspection.Failure("Versione Windows non ricavabile da ${file.name}.")
        }

        return ArtifactInspection.Success(
            DetectedArtifact(
                kind = ReleaseArtifactKind.WINDOWS,
                path = file.absolutePath,
                fileName = file.name,
                version = version,
                signature = signature,
                sizeBytes = file.length(),
                lastModifiedAt = file.lastModified()
            )
        )
    }

    private suspend fun emitProfileError(profileId: String, message: String?) {
        val normalizedMessage = message?.trim()?.ifBlank { null }
        if (emittedProfileErrors[profileId] == normalizedMessage) {
            return
        }
        emittedProfileErrors[profileId] = normalizedMessage
        mutableEvents.emit(
            ReleaseAutomationEvent.ProfileError(
                profileId = profileId,
                message = normalizedMessage
            )
        )
    }
}

internal interface WindowsVersionReader {
    suspend fun readVersion(file: File): String?
}

internal class PowerShellWindowsVersionReader : WindowsVersionReader {
    override suspend fun readVersion(file: File): String? = withContext(Dispatchers.IO) {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return@withContext null
        }

        val escapedPath = file.absolutePath.replace("'", "''")
        val command = listOf(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-Command",
            "(Get-Item -LiteralPath '$escapedPath').VersionInfo.ProductVersion"
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            return@withContext null
        }
        output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    }
}

internal fun extractVersionFromFileName(fileName: String): String? {
    val match = VERSION_REGEX.find(fileName)?.value ?: return null
    return match
        .removeSuffix(".msi")
        .removeSuffix(".exe")
        .removeSuffix(".apk")
}

private fun File.fingerprint(): String {
    return buildString {
        append(absolutePath.lowercase())
        append('|')
        append(length())
        append('|')
        append(lastModified())
    }
}

private data class CandidateObservation(
    val fingerprint: String,
    val firstSeenAt: Long
)

private sealed interface ArtifactInspection {
    data class Success(val artifact: DetectedArtifact) : ArtifactInspection

    data class Failure(val message: String) : ArtifactInspection
}

private val VERSION_REGEX = Regex("""\d+\.\d+\.\d+(?:[-._][0-9A-Za-z.-]+)?""")
