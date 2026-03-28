package com.pampa.store.data.api

import com.pampa.store.data.model.AppEntry
import com.pampa.store.data.model.AppManifest
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.DEFAULT_GITHUB_MANIFEST_PATH
import com.pampa.store.data.model.DEFAULT_STORE_OWNER
import com.pampa.store.data.model.DEFAULT_STORE_REPO
import com.pampa.store.data.model.DownloadFailureDetails
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.ReleaseAssetSource
import com.pampa.store.data.model.STORE_APP_ID
import com.pampa.store.data.model.StoreIndexEntry
import com.pampa.store.data.model.StoreIndexManifest
import com.pampa.store.data.model.StoreManifest
import com.pampa.store.data.model.StoreUpdateInfo
import com.pampa.store.data.model.VersionInfo
import com.pampa.store.data.model.isConfigured
import com.pampa.store.data.model.normalized
import com.pampa.store.data.model.toRepositoryRef
import com.pampa.store.data.model.withRepositoryFallback
import com.pampa.store.data.model.versionFor
import com.pampa.store.platform.computeFileSha256
import com.pampa.store.platform.deleteFileIfExists
import com.pampa.store.platform.moveFileReplacing
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface CatalogService {
    suspend fun getManifest(): Result<StoreManifest>
    suspend fun getLatestStoreUpdate(channel: Channel): Result<StoreUpdateInfo?>
    fun downloadAsset(source: ReleaseAssetSource, savePath: String, onProgress: (Float) -> Unit): Flow<DownloadEvent>
    fun close()
}

interface AdminPublishingService {
    val isConfigured: Boolean
    val disabledReason: String?
    val storeRepository: GitHubRepositoryRef?
    suspend fun getStoreIndex(): Result<StoreIndexManifest>
    suspend fun updateStoreIndex(index: StoreIndexManifest): Result<Unit>
    suspend fun ensureRepository(repository: GitHubRepositoryRef, appMetadata: RepositoryBootstrapMetadata): Result<EnsureRepositoryResult>
    suspend fun getAppManifest(repository: GitHubRepositoryRef): Result<AppManifest>
    suspend fun updateAppManifest(repository: GitHubRepositoryRef, manifest: AppManifest): Result<Unit>
    suspend fun getLatestStoreRelease(): Result<GitHubRelease?>
    suspend fun createRelease(repository: GitHubRepositoryRef, tag: String, name: String, body: String, prerelease: Boolean = false): Result<GitHubRelease>
    suspend fun createStoreRelease(tag: String, name: String, body: String, prerelease: Boolean = false): Result<GitHubRelease>
    suspend fun uploadAsset(uploadUrl: String, fileBytes: ByteArray, fileName: String, contentType: String = "application/octet-stream"): Result<Unit>
    fun close()
}

data class RepositoryBootstrapMetadata(
    val appId: String,
    val appName: String,
    val description: String,
    val category: String = "General",
    val developer: String = "Pampa"
)

data class EnsureRepositoryResult(
    val created: Boolean = false,
    val alreadyExists: Boolean = false,
    val failed: Boolean = false,
    val reason: String = ""
)

class GitHubApiException(
    val statusCode: Int,
    val requestUrl: String,
    val responsePreview: String = "",
    message: String
) : IllegalStateException(message)

data class GitHubAdminConfig(
    val storeRepository: GitHubRepositoryRef,
    val token: String
)

private data class CatalogRuntime(val json: Json, val apiClient: HttpClient, val assetClient: HttpClient)
private data class AdminPublishingRuntime(val json: Json, val apiClient: HttpClient)
private data class LoadedIndexedApp(val app: AppEntry?, val warnings: List<String>)
private data class RepositoryVisibility(val accessible: Boolean)
private data class RepositoryExistence(val exists: Boolean)
private sealed interface DecodedStoreRepositoryManifest {
    data class Index(val index: StoreIndexManifest) : DecodedStoreRepositoryManifest
    data class Legacy(val manifest: StoreManifest) : DecodedStoreRepositoryManifest
}

class GitHubCatalogService private constructor(
    private val storeRepository: GitHubRepositoryRef,
    runtime: CatalogRuntime
) : CatalogService {
    private val json = runtime.json
    private val apiClient = runtime.apiClient
    private val assetClient = runtime.assetClient
    private val baseUrl = "https://api.github.com"

    constructor(
        storeRepository: GitHubRepositoryRef = GitHubRepositoryRef(
            repoOwner = DEFAULT_STORE_OWNER,
            repoName = DEFAULT_STORE_REPO,
            manifestPath = DEFAULT_GITHUB_MANIFEST_PATH
        ),
        token: String = ""
    ) : this(storeRepository.normalized(), createCatalogRuntime(token))

    constructor(owner: String = DEFAULT_STORE_OWNER, repo: String = DEFAULT_STORE_REPO, token: String = "") : this(
        GitHubRepositoryRef(owner, repo, DEFAULT_GITHUB_MANIFEST_PATH),
        createCatalogRuntime(token)
    )

    internal constructor(
        storeRepository: GitHubRepositoryRef,
        json: Json,
        apiClient: HttpClient,
        assetClient: HttpClient
    ) : this(storeRepository, CatalogRuntime(json, apiClient, assetClient))

    internal constructor(
        owner: String,
        repo: String,
        json: Json,
        apiClient: HttpClient,
        assetClient: HttpClient
    ) : this(
        GitHubRepositoryRef(owner, repo, DEFAULT_GITHUB_MANIFEST_PATH),
        CatalogRuntime(json, apiClient, assetClient)
    )

    override suspend fun getManifest(): Result<StoreManifest> = runCatching {
        val rawManifest = fetchRawFile(storeRepository).getOrElse { error ->
            if (error is GitHubApiException && error.statusCode == HttpStatusCode.NotFound.value) {
                val visibility = checkRepositoryVisibility(storeRepository).getOrElse { throw it }
                if (!visibility.accessible) {
                    return@runCatching StoreManifest(
                        warnings = listOf(
                            "Repository store ${repositoryLabel(storeRepository)} non accessibile pubblicamente. " +
                                "Per i client mobile rendi pubblico il repo oppure pubblica l'indice su una repository pubblica."
                        )
                    )
                }
                return@runCatching StoreManifest(
                    warnings = listOf("Indice store non trovato nella repository ${repositoryLabel(storeRepository)}.")
                )
            }
            throw error
        }

        when (val parsed = decodeStoreRepositoryManifest(rawManifest)) {
            is DecodedStoreRepositoryManifest.Index -> loadAppsFromStoreIndex(parsed.index)
            is DecodedStoreRepositoryManifest.Legacy -> parsed.manifest.copy(
                apps = parsed.manifest.apps.map {
                    it.withRepositoryFallback(storeRepository.repoOwner, storeRepository.repoName)
                }
            )
        }
    }

    override suspend fun getLatestStoreUpdate(channel: Channel): Result<StoreUpdateInfo?> = runCatching {
        val indexVersion = getStoreReleaseFromIndex(channel).getOrElse { throw it }
        if (indexVersion != null) {
            return@runCatching StoreUpdateInfo(
                repository = storeRepository.normalized(),
                versionInfo = indexVersion
            )
        }

        when (channel) {
            Channel.STABLE -> latestStableStoreUpdateFromReleasesApi().getOrElse { throw it }
            Channel.BETA -> latestBetaStoreUpdateFromReleasesApi().getOrElse { throw it }
        }
    }

    override fun downloadAsset(
        source: ReleaseAssetSource,
        savePath: String,
        onProgress: (Float) -> Unit
    ): Flow<DownloadEvent> = flow {
        emit(DownloadEvent.Started)
        val release = getReleaseByTag(source.repository, source.releaseTag).getOrElse { error ->
            emit(DownloadEvent.Error(error.message ?: "Impossibile leggere la release richiesta."))
            return@flow
        }
        val asset = release.assets.firstOrNull { it.name == source.assetName }
        if (asset == null) {
            emit(DownloadEvent.Error("Asset '${source.assetName}' non trovato nella release '${source.releaseTag}'."))
            return@flow
        }

        val assetDownloadUrl = asset.apiUrl.ifEmpty { asset.browserDownloadUrl }
        val tempPath = "$savePath.part"
        deleteFileIfExists(tempPath)
        val response = try {
            assetClient.get(assetDownloadUrl)
        } catch (error: Exception) {
            emit(DownloadEvent.Error(error.message ?: "Richiesta download fallita."))
            return@flow
        }

        val contentType = response.contentType()?.withoutParameters()?.toString().orEmpty()
        val responseUrl = assetDownloadUrl
        if (!response.status.isSuccess()) {
            val preview = response.bodyAsText().toResponsePreview()
            val details = DownloadFailureDetails(response.status.value, contentType, responseUrl, preview, expectedBytes = asset.size)
            emit(DownloadEvent.Error(formatDownloadErrorMessage("Download asset fallito.", details), details))
            return@flow
        }
        if (isSuspiciousAssetContentType(contentType)) {
            val preview = response.bodyAsText().toResponsePreview()
            val baseMessage = if (preview.looksLikeStructuredTextPayload()) {
                "La risposta del download sembra testo o JSON invece di un file binario."
            } else {
                "Il contenuto scaricato ha un Content-Type inatteso."
            }
            val details = DownloadFailureDetails(response.status.value, contentType, responseUrl, preview, expectedBytes = asset.size)
            emit(DownloadEvent.Error(formatDownloadErrorMessage(baseMessage, details), details))
            return@flow
        }

        val expectedBytes = asset.size.takeIf { it > 0 } ?: response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val previewBytes = mutableListOf<Byte>()
        var downloadedBytes = 0L
        val channel: ByteReadChannel = response.body()
        val fileWriter = FileWriter(tempPath)
        try {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break
                if (read == 0) continue
                val chunk = buffer.copyOf(read)
                fileWriter.write(chunk)
                appendPreviewBytes(previewBytes, chunk)
                downloadedBytes += read
                val progress = if ((expectedBytes ?: 0L) > 0) downloadedBytes.toFloat() / (expectedBytes ?: 1L) else 0f
                onProgress(progress)
                emit(DownloadEvent.Progress(progress, downloadedBytes, expectedBytes ?: 0L))
            }
            fileWriter.close()
        } catch (error: Exception) {
            fileWriter.close()
            deleteFileIfExists(tempPath)
            emit(DownloadEvent.Error(error.message ?: "Errore durante la scrittura del file scaricato."))
            return@flow
        }

        val preview = previewBytes.toResponsePreview()
        val sizeMismatch = sizeMismatchMessage(contentType, preview, downloadedBytes, expectedBytes)
        if (sizeMismatch != null) {
            deleteFileIfExists(tempPath)
            val details = DownloadFailureDetails(response.status.value, contentType, responseUrl, preview, downloadedBytes, expectedBytes)
            emit(DownloadEvent.Error(formatDownloadErrorMessage(sizeMismatch, details), details))
            return@flow
        }
        val expectedDigest = normalizeSha256Digest(asset.digest)
        if (expectedDigest != null) {
            val actualDigest = computeFileSha256(tempPath)?.lowercase()
            if (actualDigest == null || actualDigest != expectedDigest) {
                deleteFileIfExists(tempPath)
                val details = DownloadFailureDetails(response.status.value, contentType, responseUrl, preview, downloadedBytes, expectedBytes)
                emit(DownloadEvent.Error(formatDownloadErrorMessage(digestMismatchMessage(contentType, preview), details), details))
                return@flow
            }
        }
        try {
            moveFileReplacing(tempPath, savePath)
        } catch (error: Exception) {
            deleteFileIfExists(tempPath)
            emit(DownloadEvent.Error(error.message ?: "Impossibile finalizzare il file scaricato."))
            return@flow
        }
        emit(DownloadEvent.Completed(savePath, asset.size, asset.digest))
    }

    override fun close() {
        apiClient.close()
        assetClient.close()
    }

    private suspend fun loadAppsFromStoreIndex(index: StoreIndexManifest): StoreManifest = coroutineScope {
        val loadResults = index.apps
            .filterNot { it.id == STORE_APP_ID }
            .map { async { loadIndexedApp(it) } }
            .awaitAll()

        StoreManifest(
            apps = loadResults.mapNotNull { it.app }.sortedBy { it.name.lowercase() },
            warnings = loadResults.flatMap { it.warnings }
        )
    }

    private suspend fun loadIndexedApp(entry: StoreIndexEntry): LoadedIndexedApp {
        val repository = entry.toRepositoryRef()
        val warnings = mutableListOf<String>()
        val rawManifest = fetchRawFile(repository).getOrElse { error ->
            warnings += "App '${entry.id}' saltata: ${error.message ?: "Manifest app non disponibile."}"
            return LoadedIndexedApp(null, warnings)
        }

        val manifest = runCatching { decodeAppManifest(rawManifest, repository) }.getOrElse { error ->
            warnings += "Manifest di '${entry.id}' non valido in ${repositoryLabel(repository)}: ${error.message}"
            return LoadedIndexedApp(null, warnings)
        }

        val normalizedId = entry.id.trim().ifBlank { manifest.app.id.trim() }
        if (manifest.app.id.isNotBlank() && normalizedId.isNotBlank() && manifest.app.id != normalizedId) {
            warnings += "L'app '${entry.id}' usa un id diverso nel proprio manifest (${manifest.app.id})."
        }

        return LoadedIndexedApp(
            app = manifest.app.copy(
                id = normalizedId.ifBlank { manifest.app.id },
                repository = repository
            ),
            warnings = warnings
        )
    }

    private suspend fun getReleaseByTag(repository: GitHubRepositoryRef, tag: String): Result<GitHubRelease> = runCatching {
        val response: HttpResponse = apiClient.get(
            "$baseUrl/repos/${repository.repoOwner}/${repository.repoName}/releases/tags/$tag"
        )
        if (!response.status.isSuccess()) {
            val preview = response.bodyAsText().toResponsePreview()
            val hint = if (response.status == HttpStatusCode.NotFound) {
                "Verifica che la release/tag esista e che la repository sia accessibile."
            } else {
                null
            }
            throw GitHubApiException(
                statusCode = response.status.value,
                requestUrl = "$baseUrl/repos/${repository.repoOwner}/${repository.repoName}/releases/tags/$tag",
                responsePreview = preview,
                message = buildString {
                    append("Release '$tag' non trovata su GitHub in ${repositoryLabel(repository)}.")
                    if (!hint.isNullOrBlank()) append("\n$hint")
                    if (preview.isNotBlank()) append("\nDettagli: $preview")
                }
            )
        }
        json.decodeFromString(GitHubRelease.serializer(), response.bodyAsText())
    }

    private suspend fun getStoreReleaseFromIndex(channel: Channel): Result<VersionInfo?> = runCatching {
        val rawManifest = fetchRawFile(storeRepository).getOrElse { error ->
            if (error is GitHubApiException && error.statusCode == HttpStatusCode.NotFound.value) {
                val visibility = checkRepositoryVisibility(storeRepository).getOrElse { throw it }
                if (!visibility.accessible) {
                    throw IllegalStateException(
                        "Repository store ${repositoryLabel(storeRepository)} non accessibile pubblicamente. " +
                            "Il controllo aggiornamenti mobile richiede una repository pubblica."
                    )
                }
                return@runCatching null
            }
            throw error
        }

        when (val parsed = decodeStoreRepositoryManifest(rawManifest)) {
            is DecodedStoreRepositoryManifest.Index -> parsed.index.storeReleases.versionFor(channel)
            is DecodedStoreRepositoryManifest.Legacy -> null
        }
    }

    private suspend fun latestStableStoreUpdateFromReleasesApi(): Result<StoreUpdateInfo?> = runCatching {
        val stableRelease = getLatestStableReleaseFromApi().getOrElse { throw it } ?: return@runCatching null
        stableRelease.toStoreUpdateInfo(storeRepository)
    }

    private suspend fun latestBetaStoreUpdateFromReleasesApi(): Result<StoreUpdateInfo?> = runCatching {
        val betaRelease = getLatestPrereleaseFromApi().getOrElse { throw it }
        val resolvedRelease = betaRelease ?: getLatestStableReleaseFromApi().getOrElse { throw it }
        resolvedRelease?.toStoreUpdateInfo(storeRepository)
    }

    private suspend fun getLatestStableReleaseFromApi(): Result<GitHubRelease?> = runCatching {
        val requestUrl = "$baseUrl/repos/${storeRepository.repoOwner}/${storeRepository.repoName}/releases/latest"
        val response: HttpResponse = apiClient.get(requestUrl)
        if (response.status == HttpStatusCode.NotFound) {
            val visibility = checkRepositoryVisibility(storeRepository).getOrElse { throw it }
            if (!visibility.accessible) {
                throw IllegalStateException(
                    "Repository store ${repositoryLabel(storeRepository)} non accessibile pubblicamente. " +
                        "Il controllo aggiornamenti mobile richiede una repository pubblica."
                )
            }
            return@runCatching null
        }
        if (!response.status.isSuccess()) {
            throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "GitHub ha risposto con ${response.status.value} durante il controllo release."
            )
        }
        json.decodeFromString(GitHubRelease.serializer(), response.bodyAsText())
    }

    private suspend fun getLatestPrereleaseFromApi(): Result<GitHubRelease?> = runCatching {
        val requestUrl = "$baseUrl/repos/${storeRepository.repoOwner}/${storeRepository.repoName}/releases"
        val response: HttpResponse = apiClient.get(requestUrl) {
            parameter("per_page", 20)
        }
        if (response.status == HttpStatusCode.NotFound) {
            val visibility = checkRepositoryVisibility(storeRepository).getOrElse { throw it }
            if (!visibility.accessible) {
                throw IllegalStateException(
                    "Repository store ${repositoryLabel(storeRepository)} non accessibile pubblicamente. " +
                        "Il controllo aggiornamenti mobile richiede una repository pubblica."
                )
            }
            return@runCatching null
        }
        if (!response.status.isSuccess()) {
            throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "GitHub ha risposto con ${response.status.value} durante il controllo release beta."
            )
        }

        val releases = json.decodeFromString(
            ListSerializer(GitHubRelease.serializer()),
            response.bodyAsText()
        )
        releases.firstOrNull { it.prerelease }
    }

    private suspend fun fetchRawFile(repository: GitHubRepositoryRef): Result<String> = runCatching {
        val normalizedRepository = repository.normalized()
        fetchRawFileFromPublicUrl(normalizedRepository).getOrElse {
            fetchRawFileFromContentsApi(normalizedRepository).getOrElse { error -> throw error }
        }
    }

    private suspend fun fetchRawFileFromPublicUrl(repository: GitHubRepositoryRef): Result<String> = runCatching {
        var lastError: Throwable? = null
        for (requestUrl in rawManifestCandidateUrls(repository)) {
            val response = assetClient.get(requestUrl) {
                header("Cache-Control", "no-cache")
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                if (body.isNotBlank()) {
                    return@runCatching body
                }
                lastError = IllegalStateException(
                    "Risposta vuota leggendo ${repository.manifestPath} da ${repositoryLabel(repository)}."
                )
                continue
            }
            lastError = response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "GitHub ha risposto con ${response.status.value} leggendo ${repository.manifestPath} in ${repositoryLabel(repository)}.",
                hint = if (response.status == HttpStatusCode.NotFound) {
                    "Il file richiesto non esiste in questo ref pubblico."
                } else {
                    null
                }
            )
        }
        throw lastError ?: IllegalStateException(
            "Impossibile leggere ${repository.manifestPath} in ${repositoryLabel(repository)}."
        )
    }

    private suspend fun fetchRawFileFromContentsApi(repository: GitHubRepositoryRef): Result<String> = runCatching {
        val requestUrl =
            "$baseUrl/repos/${repository.repoOwner}/${repository.repoName}/contents/${repository.manifestPath}"
        val response: HttpResponse = apiClient.get(
            requestUrl
        ) {
            headers.remove(HttpHeaders.Accept)
            header(HttpHeaders.Accept, "application/vnd.github.v3.raw")
            header("Cache-Control", "no-cache")
            if (repository.ref.isNotBlank()) parameter("ref", repository.ref)
        }
        if (!response.status.isSuccess()) {
            throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "GitHub ha risposto con ${response.status.value} leggendo ${repository.manifestPath} in ${repositoryLabel(repository)}.",
                hint = if (response.status == HttpStatusCode.NotFound) {
                    "Il file richiesto non esiste oppure la repository non e' accessibile."
                } else {
                    null
                }
            )
        }
        response.bodyAsText()
    }

    private suspend fun checkRepositoryVisibility(repository: GitHubRepositoryRef): Result<RepositoryVisibility> = runCatching {
        val normalizedRepository = repository.normalized()
        val requestUrl = "$baseUrl/repos/${normalizedRepository.repoOwner}/${normalizedRepository.repoName}"
        val response: HttpResponse = apiClient.get(requestUrl) {
            header("Cache-Control", "no-cache")
        }
        when {
            response.status.isSuccess() -> RepositoryVisibility(accessible = true)
            response.status == HttpStatusCode.NotFound -> RepositoryVisibility(accessible = false)
            else -> throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "Impossibile verificare la visibilita' della repository ${repositoryLabel(normalizedRepository)}."
            )
        }
    }

    private fun decodeStoreRepositoryManifest(rawManifest: String): DecodedStoreRepositoryManifest {
        runCatching {
            return DecodedStoreRepositoryManifest.Index(
                json.decodeFromString(StoreIndexManifest.serializer(), rawManifest)
            )
        }
        val legacyManifest = runCatching {
            json.decodeFromString(StoreManifest.serializer(), rawManifest)
        }.getOrElse { error ->
            throw IllegalStateException("Manifest store non valido: ${error.message}")
        }
        return DecodedStoreRepositoryManifest.Legacy(legacyManifest)
    }

    private fun decodeAppManifest(rawManifest: String, repository: GitHubRepositoryRef): AppManifest {
        runCatching {
            val manifest = json.decodeFromString(AppManifest.serializer(), rawManifest)
            return manifest.copy(app = manifest.app.copy(repository = repository.normalized()))
        }
        runCatching {
            val appEntry = json.decodeFromString(AppEntry.serializer(), rawManifest)
            return AppManifest(appEntry.copy(repository = repository.normalized()))
        }
        val legacyEntry = runCatching {
            json.decodeFromString(StoreManifest.serializer(), rawManifest)
        }.getOrNull()?.apps?.firstOrNull()
        if (legacyEntry != null) return AppManifest(legacyEntry.copy(repository = repository.normalized()))
        throw IllegalStateException("Formato manifest app non supportato.")
    }
}

class GitHubAdminPublishingService private constructor(
    private val config: GitHubAdminConfig,
    runtime: AdminPublishingRuntime
) : AdminPublishingService {
    private val json = runtime.json
    private val apiClient = runtime.apiClient
    override val isConfigured: Boolean = true
    override val disabledReason: String? = null
    override val storeRepository: GitHubRepositoryRef = config.storeRepository.normalized()
    private val baseUrl = "https://api.github.com"

    constructor(config: GitHubAdminConfig) : this(config, createAdminPublishingRuntime(config.token))

    internal constructor(config: GitHubAdminConfig, json: Json, apiClient: HttpClient) : this(
        config,
        AdminPublishingRuntime(json, apiClient)
    )

    override suspend fun getStoreIndex(): Result<StoreIndexManifest> = runCatching {
        val rawContent = getRawRepositoryFile(storeRepository).getOrElse { error ->
            if (error is GitHubApiException && error.statusCode == HttpStatusCode.NotFound.value) {
                return@runCatching StoreIndexManifest()
            }
            throw error
        }
        decodeStoreIndexManifest(rawContent)
    }

    override suspend fun updateStoreIndex(index: StoreIndexManifest): Result<Unit> {
        return updateRepositoryFile(
            repository = storeRepository,
            fileContent = json.encodeToString(StoreIndexManifest.serializer(), index),
            commitMessage = if (index.apps.isEmpty()) "Inizializza indice store" else "Aggiorna indice store"
        )
    }

    override suspend fun ensureRepository(
        repository: GitHubRepositoryRef,
        appMetadata: RepositoryBootstrapMetadata
    ): Result<EnsureRepositoryResult> = runCatching {
        val normalizedRepository = repository.normalized()
        val repositoryCheck = checkRepositoryExists(normalizedRepository).getOrThrow()

        if (!repositoryCheck.exists) {
            createRepository(normalizedRepository, appMetadata).getOrThrow()
        }

        val manifestSha = getRepositoryFileSha(normalizedRepository)
        if (manifestSha == null) {
            val initialManifest = AppManifest(
                app = AppEntry(
                    id = appMetadata.appId.trim(),
                    name = appMetadata.appName.trim(),
                    description = appMetadata.description.trim(),
                    category = appMetadata.category.trim().ifBlank { "General" },
                    developer = appMetadata.developer.trim().ifBlank { "Pampa" },
                    repository = normalizedRepository
                )
            )
            updateRepositoryFile(
                repository = normalizedRepository,
                fileContent = json.encodeToString(AppManifest.serializer(), initialManifest),
                commitMessage = "Inizializza manifest app ${appMetadata.appId.trim()}"
            ).getOrThrow()
        }

        if (repositoryCheck.exists) {
            EnsureRepositoryResult(alreadyExists = true)
        } else {
            EnsureRepositoryResult(created = true)
        }
    }.recoverCatching { error ->
        EnsureRepositoryResult(
            failed = true,
            reason = error.message ?: "Impossibile inizializzare la repository ${repositoryLabel(repository.normalized())}."
        )
    }

    override suspend fun getAppManifest(repository: GitHubRepositoryRef): Result<AppManifest> = runCatching {
        val normalizedRepository = repository.normalized()
        val rawContent = getRawRepositoryFile(normalizedRepository).getOrThrow()
        runCatching {
            val manifest = json.decodeFromString(AppManifest.serializer(), rawContent)
            manifest.copy(app = manifest.app.copy(repository = normalizedRepository))
        }.getOrElse {
            val appEntry = json.decodeFromString(AppEntry.serializer(), rawContent)
            AppManifest(appEntry.copy(repository = normalizedRepository))
        }
    }

    private fun decodeStoreIndexManifest(rawContent: String): StoreIndexManifest {
        runCatching {
            return json.decodeFromString(StoreIndexManifest.serializer(), rawContent)
        }
        val legacyManifest = runCatching {
            json.decodeFromString(StoreManifest.serializer(), rawContent)
        }.getOrElse { error ->
            throw IllegalStateException("Indice store non valido: ${error.message}")
        }
        return legacyManifest.toStoreIndexManifestFallback(storeRepository)
    }

    private fun StoreManifest.toStoreIndexManifestFallback(
        defaultRepository: GitHubRepositoryRef
    ): StoreIndexManifest {
        val normalizedDefault = defaultRepository.normalized()
        val entries = apps
            .filterNot { it.id.trim() == STORE_APP_ID }
            .mapNotNull { app ->
                val appId = app.id.trim()
                if (appId.isBlank()) return@mapNotNull null

                val appRepository = if (app.repository.isConfigured()) {
                    app.repository.normalized()
                } else {
                    normalizedDefault
                }

                StoreIndexEntry(
                    id = appId,
                    repoOwner = appRepository.repoOwner,
                    repoName = appRepository.repoName,
                    manifestPath = appRepository.manifestPath.ifBlank { DEFAULT_GITHUB_MANIFEST_PATH },
                    ref = appRepository.ref
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.id.lowercase() }
        return StoreIndexManifest(entries)
    }

    override suspend fun updateAppManifest(repository: GitHubRepositoryRef, manifest: AppManifest): Result<Unit> {
        val normalizedRepository = repository.normalized()
        val normalizedManifest = manifest.copy(app = manifest.app.copy(repository = normalizedRepository))
        return updateRepositoryFile(
            repository = normalizedRepository,
            fileContent = json.encodeToString(AppManifest.serializer(), normalizedManifest),
            commitMessage = "Aggiorna manifest app ${normalizedManifest.app.id}"
        )
    }

    override suspend fun getLatestStoreRelease(): Result<GitHubRelease?> {
        return getLatestRelease(storeRepository)
    }

    override suspend fun createRelease(
        repository: GitHubRepositoryRef,
        tag: String,
        name: String,
        body: String,
        prerelease: Boolean
    ): Result<GitHubRelease> = runCatching {
        val normalizedRepository = repository.normalized()
        val requestUrl = "$baseUrl/repos/${normalizedRepository.repoOwner}/${normalizedRepository.repoName}/releases"
        val response: HttpResponse = apiClient.post(
            requestUrl
        ) {
            contentType(ContentType.Application.Json)
            setBody(GitHubReleaseCreateRequest(tag, name, body, prerelease))
        }
        if (!response.status.isSuccess()) {
            val responsePreview = response.bodyAsText().toResponsePreview()
            val hint = when {
                response.status == HttpStatusCode.Forbidden &&
                    responsePreview.contains("rate limit", ignoreCase = true) ->
                    "Rate limit API GitHub raggiunto. Usa un token autenticato per aumentare il limite richieste."

                response.status == HttpStatusCode.Forbidden ->
                    "Token privo dei permessi richiesti per creare release/asset sulla repository."

                response.status == HttpStatusCode.Unauthorized ->
                    "Token GitHub non valido o scaduto."

                response.status == HttpStatusCode.UnprocessableEntity &&
                    (responsePreview.contains("already_exists", ignoreCase = true) ||
                        responsePreview.contains("\"tag_name\"", ignoreCase = true)) ->
                    "Il tag o la release esiste gia'. Incrementa la versione prima di pubblicare."

                response.status == HttpStatusCode.UnprocessableEntity ->
                    "GitHub ha rifiutato i dati della release."

                response.status == HttpStatusCode.NotFound ->
                    "Repository non trovata o non accessibile con il token corrente."

                else -> null
            }
            throw GitHubApiException(
                statusCode = response.status.value,
                requestUrl = requestUrl,
                responsePreview = responsePreview,
                message = buildString {
                    append("Creazione release fallita con stato ${response.status.value} in ${repositoryLabel(normalizedRepository)}.")
                    if (!hint.isNullOrBlank()) append("\n$hint")
                    if (responsePreview.isNotBlank()) append("\nDettagli: $responsePreview")
                }
            )
        }
        json.decodeFromString(GitHubRelease.serializer(), response.bodyAsText())
    }

    private suspend fun getLatestRelease(repository: GitHubRepositoryRef): Result<GitHubRelease?> = runCatching {
        val normalizedRepository = repository.normalized()
        val requestUrl = "$baseUrl/repos/${normalizedRepository.repoOwner}/${normalizedRepository.repoName}/releases/latest"
        val response: HttpResponse = apiClient.get(requestUrl)
        when {
            response.status == HttpStatusCode.NotFound -> null
            !response.status.isSuccess() -> throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "Lettura release piu' recente fallita in ${repositoryLabel(normalizedRepository)}."
            )
            else -> json.decodeFromString(GitHubRelease.serializer(), response.bodyAsText())
        }
    }

    override suspend fun createStoreRelease(
        tag: String,
        name: String,
        body: String,
        prerelease: Boolean
    ): Result<GitHubRelease> {
        return createRelease(storeRepository, tag, name, body, prerelease)
    }

    override suspend fun uploadAsset(
        uploadUrl: String,
        fileBytes: ByteArray,
        fileName: String,
        contentType: String
    ): Result<Unit> = runCatching {
        val cleanUrl = uploadUrl.replace("{?name,label}", "")
        val response = apiClient.post(cleanUrl) {
            parameter("name", fileName)
            header(HttpHeaders.ContentType, contentType)
            setBody(fileBytes)
        }
        if (!response.status.isSuccess()) {
            val preview = response.bodyAsText().toResponsePreview()
            val hint = if (
                response.status == HttpStatusCode.UnprocessableEntity &&
                preview.contains("already_exists", ignoreCase = true)
            ) {
                "Esiste gia' un asset con lo stesso nome nella release."
            } else if (
                response.status == HttpStatusCode.Forbidden &&
                preview.contains("rate limit", ignoreCase = true)
            ) {
                "Rate limit API GitHub raggiunto. Usa un token autenticato per aumentare il limite richieste."
            } else if (response.status == HttpStatusCode.Forbidden) {
                "Token privo dei permessi richiesti per upload release asset."
            } else if (response.status == HttpStatusCode.Unauthorized) {
                "Token GitHub non valido o scaduto."
            } else if (response.status == HttpStatusCode.NotFound) {
                "Upload URL non valida oppure release/repository non accessibile."
            } else {
                null
            }
            throw GitHubApiException(
                statusCode = response.status.value,
                requestUrl = cleanUrl,
                responsePreview = preview,
                message = buildString {
                    append("Upload asset fallito con stato ${response.status.value}.")
                    if (!hint.isNullOrBlank()) append("\n$hint")
                    if (preview.isNotBlank()) append("\nDettagli: $preview")
                }
            )
        }
    }

    override fun close() {
        apiClient.close()
    }

    private suspend fun getRawRepositoryFile(repository: GitHubRepositoryRef): Result<String> = runCatching {
        val normalizedRepository = repository.normalized()
        val requestUrl =
            "$baseUrl/repos/${normalizedRepository.repoOwner}/${normalizedRepository.repoName}/contents/${normalizedRepository.manifestPath}"
        val response: HttpResponse = apiClient.get(
            requestUrl
        ) {
            headers.remove(HttpHeaders.Accept)
            header(HttpHeaders.Accept, "application/vnd.github.v3.raw")
            header("Cache-Control", "no-cache")
            if (normalizedRepository.ref.isNotBlank()) parameter("ref", normalizedRepository.ref)
        }
        if (!response.status.isSuccess()) {
            throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "GitHub ha risposto con ${response.status.value} leggendo ${normalizedRepository.manifestPath} in ${repositoryLabel(normalizedRepository)}.",
                hint = if (response.status == HttpStatusCode.NotFound) {
                    "Il file non esiste oppure la repository non e' accessibile con il token."
                } else {
                    null
                }
            )
        }
        response.bodyAsText()
    }

    private suspend fun updateRepositoryFile(
        repository: GitHubRepositoryRef,
        fileContent: String,
        commitMessage: String
    ): Result<Unit> = runCatching {
        val normalizedRepository = repository.normalized()
        val requestUrl =
            "$baseUrl/repos/${normalizedRepository.repoOwner}/${normalizedRepository.repoName}/contents/${normalizedRepository.manifestPath}"
        val requestBody = buildGitHubContentUpdateRequestBody(
            message = commitMessage,
            contentBase64 = encodeBase64(fileContent.encodeToByteArray()),
            sha = getRepositoryFileSha(normalizedRepository),
            branch = normalizedRepository.ref
        )
        val putResponse = apiClient.put(
            requestUrl
        ) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        if (!putResponse.status.isSuccess()) {
            val preview = putResponse.bodyAsText().toResponsePreview()
            val hint = if (putResponse.status == HttpStatusCode.NotFound) {
                "Repository o branch non trovata, oppure token privo di permessi in scrittura."
            } else {
                null
            }
            throw GitHubApiException(
                statusCode = putResponse.status.value,
                requestUrl = requestUrl,
                responsePreview = preview,
                message = buildString {
                    append("Aggiornamento file fallito con stato ${putResponse.status.value} in ${repositoryLabel(normalizedRepository)}.")
                    if (!hint.isNullOrBlank()) append("\n$hint")
                    if (preview.isNotBlank()) append("\nDettagli: $preview")
                }
            )
        }
    }

    private suspend fun getRepositoryFileSha(repository: GitHubRepositoryRef): String? {
        val requestUrl = "$baseUrl/repos/${repository.repoOwner}/${repository.repoName}/contents/${repository.manifestPath}"
        val response: HttpResponse = apiClient.get(
            requestUrl
        ) {
            header("Cache-Control", "no-cache")
            if (repository.ref.isNotBlank()) parameter("ref", repository.ref)
        }
        return when {
            response.status == HttpStatusCode.NotFound -> null
            !response.status.isSuccess() -> throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "GitHub ha risposto con ${response.status.value} recuperando il riferimento del file.",
                hint = "Verifica i permessi del token sulla repository."
            )
            else -> json.decodeFromString(GitHubFileResponse.serializer(), response.bodyAsText()).sha
        }
    }

    private suspend fun checkRepositoryExists(repository: GitHubRepositoryRef): Result<RepositoryExistence> = runCatching {
        val requestUrl = "$baseUrl/repos/${repository.repoOwner}/${repository.repoName}"
        val response: HttpResponse = apiClient.get(requestUrl) {
            header("Cache-Control", "no-cache")
        }
        when {
            response.status.isSuccess() -> RepositoryExistence(exists = true)
            response.status == HttpStatusCode.NotFound -> RepositoryExistence(exists = false)
            else -> throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "Verifica repository ${repositoryLabel(repository)} fallita."
            )
        }
    }

    private suspend fun createRepository(
        repository: GitHubRepositoryRef,
        appMetadata: RepositoryBootstrapMetadata
    ): Result<Unit> = runCatching {
        val ownerLogin = getAuthenticatedUserLogin().getOrThrow()
        val createBody = GitHubRepositoryCreateRequest(
            name = repository.repoName,
            description = appMetadata.description.trim()
                .ifBlank { "Repository ufficiale per ${appMetadata.appName.trim().ifBlank { appMetadata.appId.trim() }}" },
            private = false,
            auto_init = true
        )

        val endpoint = if (repository.repoOwner.equals(ownerLogin, ignoreCase = true)) {
            "$baseUrl/user/repos"
        } else {
            "$baseUrl/orgs/${repository.repoOwner}/repos"
        }

        val response = apiClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(createBody)
        }

        if (!response.status.isSuccess()) {
            val preview = response.bodyAsText().toResponsePreview()
            if (response.status == HttpStatusCode.UnprocessableEntity &&
                preview.contains("already exists", ignoreCase = true)
            ) {
                return@runCatching
            }
            val hint = when (response.status) {
                HttpStatusCode.NotFound ->
                    "Owner non trovato o token senza permesso di creazione repository."

                HttpStatusCode.Forbidden ->
                    "Token privo dei permessi richiesti per creare repository."

                HttpStatusCode.Unauthorized ->
                    "Token GitHub non valido o scaduto."

                else -> null
            }
            throw GitHubApiException(
                statusCode = response.status.value,
                requestUrl = endpoint,
                responsePreview = preview,
                message = buildString {
                    append("Creazione repository ${repositoryLabel(repository)} fallita.")
                    if (!hint.isNullOrBlank()) append("\n$hint")
                    if (preview.isNotBlank()) append("\nDettagli: $preview")
                }
            )
        }
    }

    private suspend fun getAuthenticatedUserLogin(): Result<String> = runCatching {
        val requestUrl = "$baseUrl/user"
        val response: HttpResponse = apiClient.get(requestUrl)
        if (!response.status.isSuccess()) {
            throw response.toGitHubApiException(
                requestUrl = requestUrl,
                userMessage = "Impossibile leggere l'utente autenticato GitHub.",
                hint = "Verifica PAMPA_GH_TOKEN e i permessi del token."
            )
        }
        val user = json.decodeFromString(GitHubUserResponse.serializer(), response.bodyAsText())
        user.login.trim().ifBlank {
            throw IllegalStateException("Utente GitHub autenticato non valido.")
        }
    }
}

class DisabledAdminPublishingService(
    override val disabledReason: String
) : AdminPublishingService {
    override val isConfigured: Boolean = false
    override val storeRepository: GitHubRepositoryRef? = null

    override suspend fun getStoreIndex(): Result<StoreIndexManifest> = Result.failure(IllegalStateException(disabledReason))
    override suspend fun updateStoreIndex(index: StoreIndexManifest): Result<Unit> = Result.failure(IllegalStateException(disabledReason))
    override suspend fun ensureRepository(
        repository: GitHubRepositoryRef,
        appMetadata: RepositoryBootstrapMetadata
    ): Result<EnsureRepositoryResult> = Result.failure(IllegalStateException(disabledReason))
    override suspend fun getAppManifest(repository: GitHubRepositoryRef): Result<AppManifest> = Result.failure(IllegalStateException(disabledReason))
    override suspend fun updateAppManifest(repository: GitHubRepositoryRef, manifest: AppManifest): Result<Unit> = Result.failure(IllegalStateException(disabledReason))
    override suspend fun getLatestStoreRelease(): Result<GitHubRelease?> = Result.failure(IllegalStateException(disabledReason))
    override suspend fun createRelease(repository: GitHubRepositoryRef, tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease> =
        Result.failure(IllegalStateException(disabledReason))
    override suspend fun createStoreRelease(tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease> =
        Result.failure(IllegalStateException(disabledReason))
    override suspend fun uploadAsset(uploadUrl: String, fileBytes: ByteArray, fileName: String, contentType: String): Result<Unit> =
        Result.failure(IllegalStateException(disabledReason))
    override fun close() = Unit
}

sealed class DownloadEvent {
    data object Started : DownloadEvent()
    data class Progress(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadEvent()
    data class Completed(val filePath: String, val assetSize: Long, val assetDigest: String) : DownloadEvent()
    data class Error(val message: String, val details: DownloadFailureDetails? = null) : DownloadEvent()
}

@Serializable
data class GitHubFileResponse(val sha: String, val content: String = "")

@Serializable
data class GitHubRelease(
    val id: Long,
    val tag_name: String,
    val name: String = "",
    val body: String = "",
    val prerelease: Boolean = false,
    val upload_url: String = "",
    val published_at: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val url: String = "",
    val browser_download_url: String = "",
    val digest: String = ""
) {
    val apiUrl: String get() = url
    val browserDownloadUrl: String get() = browser_download_url
}

@Serializable
private data class GitHubReleaseCreateRequest(
    val tag_name: String,
    val name: String,
    val body: String,
    val prerelease: Boolean
)

@Serializable
private data class GitHubRepositoryCreateRequest(
    val name: String,
    val description: String,
    val private: Boolean,
    val auto_init: Boolean
)

@Serializable
private data class GitHubUserResponse(
    val login: String
)

private fun defaultGitHubJson() = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

private fun createCatalogRuntime(token: String = ""): CatalogRuntime {
    val json = defaultGitHubJson()
    return CatalogRuntime(
        json = json,
        apiClient = createGitHubApiClient(token = token, configJson = json),
        assetClient = createGitHubAssetClient(token = token)
    )
}

private fun createAdminPublishingRuntime(token: String): AdminPublishingRuntime {
    val json = defaultGitHubJson()
    return AdminPublishingRuntime(
        json = json,
        apiClient = createGitHubApiClient(token = token, configJson = json)
    )
}

private fun createGitHubApiClient(token: String, configJson: Json): HttpClient {
    val bearerToken = token.trim()
    return HttpClient {
        followRedirects = true
        install(ContentNegotiation) { json(configJson) }
        install(Logging) { level = LogLevel.NONE }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.UserAgent, "PampaStore/1.0")
            if (bearerToken.isNotEmpty()) header(HttpHeaders.Authorization, "Bearer $bearerToken")
        }
    }
}

private fun buildGitHubContentUpdateRequestBody(
    message: String,
    contentBase64: String,
    sha: String?,
    branch: String
) = buildJsonObject {
    put("message", message)
    put("content", contentBase64)
    sha?.takeIf { it.isNotBlank() }?.let { put("sha", it) }
    branch.takeIf { it.isNotBlank() }?.let { put("branch", it) }
}

private fun createGitHubAssetClient(token: String): HttpClient {
    val bearerToken = token.trim()
    return HttpClient {
        followRedirects = true
        install(Logging) { level = LogLevel.NONE }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest {
            header(HttpHeaders.Accept, "application/octet-stream")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.UserAgent, "PampaStore/1.0")
            if (bearerToken.isNotEmpty()) header(HttpHeaders.Authorization, "Bearer $bearerToken")
        }
    }
}

private fun rawManifestCandidateUrls(repository: GitHubRepositoryRef): List<String> {
    val refs = linkedSetOf<String>()
    repository.ref.trim().takeIf { it.isNotBlank() }?.let(refs::add)
    refs += "HEAD"
    refs += "main"
    refs += "master"
    return refs.map { ref ->
        "https://raw.githubusercontent.com/${repository.repoOwner}/${repository.repoName}/$ref/${repository.manifestPath}"
    }
}

private fun repositoryLabel(repository: GitHubRepositoryRef): String = "${repository.repoOwner}/${repository.repoName}"

private fun GitHubRelease.toStoreUpdateInfo(repository: GitHubRepositoryRef): StoreUpdateInfo? {
    val version = extractStoreVersion(tag_name, name)
    if (version.isBlank()) return null
    val windowsAsset = pickWindowsInstallerAsset(assets)
    return StoreUpdateInfo(
        repository = repository.normalized(),
        versionInfo = VersionInfo(
            version = version,
            releaseDate = published_at.takeIf { it.length >= 10 }?.take(10).orEmpty(),
            changelog = body,
            releaseTag = tag_name,
            apkAsset = assets.firstOrNull { it.name.endsWith(".apk", true) }?.name.orEmpty(),
            exeAsset = windowsAsset?.name.orEmpty(),
            sizeBytes = assets.sumOf { it.size }
        )
    )
}

private fun pickWindowsInstallerAsset(assets: List<GitHubAsset>): GitHubAsset? {
    return assets.firstOrNull { it.name.endsWith(".msi", true) }
        ?: assets.firstOrNull {
            it.name.endsWith(".exe", true) &&
                it.name.contains("installer", ignoreCase = true)
        }
        ?: assets.firstOrNull {
            it.name.endsWith(".exe", true) &&
                it.name.contains("setup", ignoreCase = true)
        }
        ?: assets.firstOrNull { it.name.endsWith(".exe", true) }
}

private fun extractStoreVersion(tagName: String, releaseName: String): String {
    val normalizedTag = tagName.trim()
    val withoutPrefix = normalizedTag
        .removePrefix("stable-")
        .removePrefix("beta-")

    return when {
        withoutPrefix.startsWith("$STORE_APP_ID-v") -> withoutPrefix.removePrefix("$STORE_APP_ID-v")
        withoutPrefix.startsWith("v") -> withoutPrefix.removePrefix("v")
        normalizedTag.startsWith("$STORE_APP_ID-v") -> normalizedTag.removePrefix("$STORE_APP_ID-v")
        normalizedTag.startsWith("v") -> normalizedTag.removePrefix("v")
        releaseName.startsWith("Pampa Store ", ignoreCase = true) -> releaseName.removePrefix("Pampa Store ").trim()
        else -> withoutPrefix
    }
}

private fun appendPreviewBytes(previewBytes: MutableList<Byte>, chunk: ByteArray, limit: Int = 256) {
    if (previewBytes.size >= limit) return
    val remaining = limit - previewBytes.size
    chunk.take(remaining).forEach { previewBytes += it }
}

private fun List<Byte>.toResponsePreview(limit: Int = 240): String {
    if (isEmpty()) return ""
    val bytes = ByteArray(size) { this[it] }
    return bytes.decodeToString().replace(Regex("\\p{Cntrl}"), " ").trim().take(limit)
}

private fun String.toResponsePreview(limit: Int = 240): String {
    return replace(Regex("\\p{Cntrl}"), " ").trim().take(limit)
}

private suspend fun HttpResponse.toGitHubApiException(
    requestUrl: String,
    userMessage: String,
    hint: String? = null
): GitHubApiException {
    val preview = bodyAsText().toResponsePreview()
    val isRateLimitError = status == HttpStatusCode.Forbidden && preview.contains("rate limit", ignoreCase = true)
    val hints = buildList {
        hint?.takeIf { it.isNotBlank() }?.let { add(it) }
        inferGitHubHint(status, preview)?.let { inferred ->
            if (none { it.equals(inferred, ignoreCase = true) }) add(inferred)
        }
    }
    return GitHubApiException(
        statusCode = status.value,
        requestUrl = requestUrl,
        responsePreview = preview,
        message = buildString {
            append(userMessage)
            hints.forEach { append("\n$it") }
            if (preview.isNotBlank() && !isRateLimitError) append("\nDettagli: $preview")
        }
    )
}

private fun inferGitHubHint(status: HttpStatusCode, preview: String): String? {
    return when {
        status == HttpStatusCode.Forbidden && preview.contains("rate limit", ignoreCase = true) ->
            "Rate limit API GitHub raggiunto. Configura PAMPA_GH_TOKEN (o GITHUB_TOKEN) per richieste autenticate."

        status == HttpStatusCode.Forbidden &&
            preview.contains("resource not accessible", ignoreCase = true) ->
            "Token privo dei permessi richiesti sulla repository."

        status == HttpStatusCode.Unauthorized &&
            (preview.contains("bad credentials", ignoreCase = true) ||
                preview.contains("requires authentication", ignoreCase = true)) ->
            "Token GitHub non valido o scaduto."

        else -> null
    }
}

private fun isSuspiciousAssetContentType(contentType: String): Boolean {
    if (contentType.isBlank()) return false
    return when {
        contentType.startsWith("application/json") -> true
        contentType.startsWith("text/") -> true
        contentType == "application/octet-stream" -> false
        contentType == "application/vnd.android.package-archive" -> false
        contentType == "application/x-msdownload" -> false
        contentType == "application/x-msi" -> false
        contentType == "application/vnd.microsoft.portable-executable" -> false
        else -> true
    }
}

private fun sizeMismatchMessage(contentType: String, preview: String, downloadedBytes: Long, expectedBytes: Long?): String? {
    if (expectedBytes == null || expectedBytes <= 0 || downloadedBytes == expectedBytes) return null
    return if (contentType.startsWith("application/json") || preview.looksLikeStructuredTextPayload()) {
        "La risposta del download sembra testo o JSON invece di un file binario."
    } else {
        "La dimensione del file scaricato non coincide con i metadati della release."
    }
}

private fun digestMismatchMessage(contentType: String, preview: String): String {
    return if (contentType.startsWith("application/json") || preview.looksLikeStructuredTextPayload()) {
        "La risposta del download sembra testo o JSON invece di un file binario."
    } else {
        "L'hash del file scaricato non coincide con i metadati della release."
    }
}

private fun normalizeSha256Digest(rawDigest: String): String? {
    if (rawDigest.isBlank()) return null
    val normalizedDigest = rawDigest.substringAfter("sha256:", rawDigest).trim().lowercase()
    return normalizedDigest.ifBlank { null }
}

private fun formatDownloadErrorMessage(baseMessage: String, details: DownloadFailureDetails): String {
    val lines = buildList {
        add(baseMessage)
        details.statusCode?.let { add("HTTP: $it") }
        if (details.contentType.isNotBlank()) add("Content-Type: ${details.contentType}")
        if (details.sourceUrl.isNotBlank()) add("URL: ${details.sourceUrl}")
        if (details.expectedBytes != null || details.downloadedBytes != null) {
            add("Byte: ${details.downloadedBytes?.toString() ?: "?"} / ${details.expectedBytes?.toString() ?: "?"}")
        }
        if (details.responsePreview.isNotBlank()) add("Anteprima: ${details.responsePreview}")
    }
    return lines.joinToString("\n")
}

private fun String.looksLikeStructuredTextPayload(): Boolean {
    val trimmed = trimStart()
    return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")
}

expect class FileWriter(path: String) {
    fun write(bytes: ByteArray)
    fun close()
}

fun encodeBase64(bytes: ByteArray): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val builder = StringBuilder()
    var index = 0
    while (index < bytes.size) {
        val b0 = bytes[index].toInt() and 0xFF
        val b1 = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xFF else 0
        val b2 = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xFF else 0
        builder.append(table[(b0 shr 2) and 0x3F])
        builder.append(table[((b0 shl 4) or (b1 shr 4)) and 0x3F])
        if (index + 1 < bytes.size) builder.append(table[((b1 shl 2) or (b2 shr 6)) and 0x3F]) else builder.append('=')
        if (index + 2 < bytes.size) builder.append(table[b2 and 0x3F]) else builder.append('=')
        index += 3
    }
    return builder.toString()
}
