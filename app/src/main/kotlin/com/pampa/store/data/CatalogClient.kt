package com.pampa.store.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

data class CatalogFetchResult(
    val apps: List<AppEntry>,
    val storeReleases: StoreReleaseChannels,
    val warnings: List<String>,
)

class CatalogClient(
    private val storeRepository: RepositoryRef = RepositoryRef(
        repoOwner = STORE_OWNER,
        repoName = STORE_REPOSITORY,
    ),
    private val httpClient: HttpClient = defaultHttpClient(),
) : AutoCloseable {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun fetchCatalog(): CatalogFetchResult {
        val rootText = fetchText(storeRepository.rawUrl())
        val root = json.parseToJsonElement(rootText).jsonObject
        val appsNode = root["apps"] as? JsonArray ?: JsonArray(emptyList())
        val isIndex = appsNode.firstOrNull()
            ?.let { it as? JsonObject }
            ?.containsKey("repoOwner") == true || root.containsKey("storeReleases")

        return if (isIndex) fetchIndexedCatalog(root) else fetchLegacyCatalog(rootText)
    }

    private suspend fun fetchIndexedCatalog(root: JsonObject): CatalogFetchResult = coroutineScope {
        val index = json.decodeFromJsonElement<StoreIndexManifest>(root)
        val warnings = mutableListOf<String>()
        val apps = index.apps.map { indexEntry ->
            async {
                runCatching { fetchApp(indexEntry) }
                    .onFailure { warnings += "${indexEntry.id}: ${it.message ?: "manifest non disponibile"}" }
                    .getOrNull()
            }
        }.awaitAll().filterNotNull()

        CatalogFetchResult(
            apps = apps,
            storeReleases = index.storeReleases,
            warnings = warnings,
        )
    }

    private suspend fun fetchLegacyCatalog(text: String): CatalogFetchResult {
        val manifest = json.decodeFromString<StoreManifest>(text)
        return CatalogFetchResult(
            apps = manifest.apps.map { app ->
                if (app.repository.isConfigured) app else app.copy(repository = storeRepository)
            },
            storeReleases = StoreReleaseChannels(),
            warnings = manifest.warnings,
        )
    }

    private suspend fun fetchApp(indexEntry: StoreIndexEntry): AppEntry {
        val repository = indexEntry.repository()
        val text = fetchText(repository.rawUrl())
        val root = json.parseToJsonElement(text).jsonObject
        val decoded = root["app"]?.let { json.decodeFromJsonElement<AppEntry>(it) }
            ?: json.decodeFromJsonElement<AppEntry>(root)
        require(decoded.id.isNotBlank()) { "id app mancante" }
        // L'indice è la sorgente canonica dell'identificatore. Alcuni manifest storici usano il
        // nome visuale o una diversa capitalizzazione: normalizzarli evita di perdere app sane.
        return decoded.copy(id = indexEntry.id, repository = repository)
    }

    suspend fun download(
        url: String,
        destination: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")
        if (partial.exists()) partial.delete()

        try {
            httpClient.prepareGet(url) {
                header(HttpHeaders.UserAgent, "PampaStore/${BuildVersion.name}")
            }.execute { response ->
                check(response.status.isSuccess()) { "Download non riuscito: HTTP ${response.status.value}" }
                val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                val channel = response.bodyAsChannel()
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read == -1) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
                if (total > 0 && partial.length() != total) {
                    error("Download incompleto: ${partial.length()} di $total byte")
                }
            }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "Impossibile finalizzare il download" }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private suspend fun fetchText(url: String): String {
        val response = httpClient.get(url) {
            header(HttpHeaders.UserAgent, "PampaStore/${BuildVersion.name}")
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return response.bodyAsText()
    }

    override fun close() {
        httpClient.close()
    }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

object BuildVersion {
    var name: String = "dev"
}
