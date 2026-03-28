package com.pampa.store.data.api

import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.ReleaseAssetSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class GitHubApiServiceTest {

    @Test
    fun downloadAssetRejectsJsonPayloadAndDoesNotWriteFinalFile() = runTest {
        val tempDir = Files.createTempDirectory("pampastore-download-test")
        val savePath = tempDir.resolve("codex.apk").toString()
        val apiClient = mockApiClient(
            releaseJson(
                assetUrl = "https://downloads.test/asset",
                assetName = "codex.apk",
                assetSize = 1234,
                assetDigest = "sha256:deadbeef"
            )
        )
        val assetClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"message":"metadata"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val service = GitHubCatalogService(
            owner = "test",
            repo = "test",
            json = testJson(),
            apiClient = apiClient,
            assetClient = assetClient
        )

        val events = try {
            service.downloadAsset(
                source = ReleaseAssetSource(
                    repository = GitHubRepositoryRef(repoOwner = "test", repoName = "test"),
                    releaseTag = "test-tag",
                    assetName = "codex.apk"
                ),
                savePath = savePath
            ) { }.toList()
        } finally {
            service.close()
        }

        val error = assertIs<DownloadEvent.Error>(events.last())
        assertTrue(error.message.contains("testo o JSON"))
        assertEquals("application/json", error.details?.contentType)
        assertFalse(Files.exists(tempDir.resolve("codex.apk")))
        assertFalse(Files.exists(tempDir.resolve("codex.apk.part")))
    }

    @Test
    fun downloadAssetFollowsRedirectAndFinalizesBinaryFile() = runTest {
        val tempDir = Files.createTempDirectory("pampastore-download-test")
        val savePath = tempDir.resolve("codex.apk").toString()
        val payload = "PK\u0003\u0004codex".encodeToByteArray()
        val digest = "sha256:${payload.sha256()}"
        val apiClient = mockApiClient(
            releaseJson(
                assetUrl = "https://downloads.test/asset",
                assetName = "codex.apk",
                assetSize = payload.size.toLong(),
                assetDigest = digest
            )
        )
        val assetClient = HttpClient(MockEngine) {
            followRedirects = true
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        "https://downloads.test/asset" -> respondRedirect("https://downloads.test/final")
                        "https://downloads.test/final" -> respond(
                            content = ByteReadChannel(payload),
                            status = HttpStatusCode.OK,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf("application/vnd.android.package-archive"),
                                HttpHeaders.ContentLength to listOf(payload.size.toString())
                            )
                        )
                        else -> error("Unexpected URL: ${request.url}")
                    }
                }
            }
        }

        val service = GitHubCatalogService(
            owner = "test",
            repo = "test",
            json = testJson(),
            apiClient = apiClient,
            assetClient = assetClient
        )

        val events = try {
            service.downloadAsset(
                source = ReleaseAssetSource(
                    repository = GitHubRepositoryRef(repoOwner = "test", repoName = "test"),
                    releaseTag = "test-tag",
                    assetName = "codex.apk"
                ),
                savePath = savePath
            ) { }.toList()
        } finally {
            service.close()
        }

        assertIs<DownloadEvent.Completed>(events.last())
        assertTrue(Files.exists(tempDir.resolve("codex.apk")))
        assertFalse(Files.exists(tempDir.resolve("codex.apk.part")))
        assertEquals(payload.toList(), tempDir.resolve("codex.apk").readBytes().toList())
    }

    @Test
    fun downloadAssetFailsOnSizeMismatchAndCleansTempFile() = runTest {
        val tempDir = Files.createTempDirectory("pampastore-download-test")
        val savePath = tempDir.resolve("codex.apk").toString()
        val payload = "PK\u0003\u0004codex".encodeToByteArray()
        val apiClient = mockApiClient(
            releaseJson(
                assetUrl = "https://downloads.test/asset",
                assetName = "codex.apk",
                assetSize = payload.size.toLong() + 10,
                assetDigest = "sha256:${payload.sha256()}"
            )
        )
        val assetClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel(payload),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("application/vnd.android.package-archive"),
                            HttpHeaders.ContentLength to listOf(payload.size.toString())
                        )
                    )
                }
            }
        }

        val service = GitHubCatalogService(
            owner = "test",
            repo = "test",
            json = testJson(),
            apiClient = apiClient,
            assetClient = assetClient
        )

        val events = try {
            service.downloadAsset(
                source = ReleaseAssetSource(
                    repository = GitHubRepositoryRef(repoOwner = "test", repoName = "test"),
                    releaseTag = "test-tag",
                    assetName = "codex.apk"
                ),
                savePath = savePath
            ) { }.toList()
        } finally {
            service.close()
        }

        val error = assertIs<DownloadEvent.Error>(events.last())
        assertTrue(error.message.contains("dimensione"))
        assertFalse(Files.exists(tempDir.resolve("codex.apk")))
        assertFalse(Files.exists(tempDir.resolve("codex.apk.part")))
    }

    @Test
    fun downloadAssetFailsOnDigestMismatchAndCleansTempFile() = runTest {
        val tempDir = Files.createTempDirectory("pampastore-download-test")
        val savePath = tempDir.resolve("codex.apk").toString()
        val payload = "PK\u0003\u0004codex".encodeToByteArray()
        val apiClient = mockApiClient(
            releaseJson(
                assetUrl = "https://downloads.test/asset",
                assetName = "codex.apk",
                assetSize = payload.size.toLong(),
                assetDigest = "sha256:deadbeef"
            )
        )
        val assetClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel(payload),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("application/vnd.android.package-archive"),
                            HttpHeaders.ContentLength to listOf(payload.size.toString())
                        )
                    )
                }
            }
        }

        val service = GitHubCatalogService(
            owner = "test",
            repo = "test",
            json = testJson(),
            apiClient = apiClient,
            assetClient = assetClient
        )

        val events = try {
            service.downloadAsset(
                source = ReleaseAssetSource(
                    repository = GitHubRepositoryRef(repoOwner = "test", repoName = "test"),
                    releaseTag = "test-tag",
                    assetName = "codex.apk"
                ),
                savePath = savePath
            ) { }.toList()
        } finally {
            service.close()
        }

        val error = assertIs<DownloadEvent.Error>(events.last())
        assertTrue(error.message.contains("hash"))
        assertFalse(Files.exists(tempDir.resolve("codex.apk")))
        assertFalse(Files.exists(tempDir.resolve("codex.apk.part")))
    }

    @Test
    fun latestStoreUpdateReturnsNullWhenRepositoryExistsButNoRelease() = runTest {
        val repository = GitHubRepositoryRef(repoOwner = "test", repoName = "store")
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when {
                        request.matches(HttpMethod.Get, "/repos/test/store/contents/manifest.json") ->
                            respond(
                                content = """{"message":"Not Found"}""",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        request.matches(HttpMethod.Get, "/repos/test/store/releases/latest") ->
                            respond(
                                content = """{"message":"Not Found"}""",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        request.matches(HttpMethod.Get, "/repos/test/store") ->
                            respond(
                                content = """{"id":1}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
        }

        val service = GitHubCatalogService(
            storeRepository = repository,
            json = testJson(),
            apiClient = apiClient,
            assetClient = emptyAssetClient()
        )

        val updateResult = try {
            service.getLatestStoreUpdate(Channel.STABLE)
        } finally {
            service.close()
        }

        assertTrue(updateResult.isSuccess)
        assertEquals(null, updateResult.getOrNull())
    }

    @Test
    fun latestStoreUpdateReturnsDiagnosticWhenRepositoryIsNotPublic() = runTest {
        val repository = GitHubRepositoryRef(repoOwner = "test", repoName = "private-store")
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when {
                        request.matches(HttpMethod.Get, "/repos/test/private-store/contents/manifest.json") ->
                            respond(
                                content = """{"message":"Not Found"}""",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        request.matches(HttpMethod.Get, "/repos/test/private-store/releases/latest") ->
                            respond(
                                content = """{"message":"Not Found"}""",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        request.matches(HttpMethod.Get, "/repos/test/private-store") ->
                            respond(
                                content = """{"message":"Not Found"}""",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
        }

        val service = GitHubCatalogService(
            storeRepository = repository,
            json = testJson(),
            apiClient = apiClient,
            assetClient = emptyAssetClient()
        )

        val updateResult = try {
            service.getLatestStoreUpdate(Channel.STABLE)
        } finally {
            service.close()
        }

        assertTrue(updateResult.isFailure)
        assertTrue(updateResult.exceptionOrNull()?.message?.contains("non accessibile pubblicamente") == true)
    }

    @Test
    fun latestStoreUpdateReadsChannelFromStoreIndexMetadata() = runTest {
        val repository = GitHubRepositoryRef(repoOwner = "test", repoName = "store")
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when {
                        request.matches(HttpMethod.Get, "/repos/test/store/contents/manifest.json") ->
                            respond(
                                content = """
                                    {
                                      "apps": [],
                                      "storeReleases": {
                                        "stable": {
                                          "version": "1.3.0",
                                          "releaseDate": "2026-03-10",
                                          "changelog": "Stable release",
                                          "releaseTag": "stable-pampa-store-v1.3.0",
                                          "apkAsset": "pampa-store-1.3.0.apk",
                                          "exeAsset": "pampa-store-1.3.0-installer.exe",
                                          "sizeBytes": 10
                                        },
                                        "beta": {
                                          "version": "1.4.0-beta.1",
                                          "releaseDate": "2026-03-11",
                                          "changelog": "Beta release",
                                          "releaseTag": "beta-pampa-store-v1.4.0-beta.1",
                                          "apkAsset": "pampa-store-1.4.0-beta.1.apk",
                                          "exeAsset": "pampa-store-1.4.0-beta.1-installer.exe",
                                          "sizeBytes": 11
                                        }
                                      }
                                    }
                                """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
        }

        val service = GitHubCatalogService(
            storeRepository = repository,
            json = testJson(),
            apiClient = apiClient,
            assetClient = emptyAssetClient()
        )

        val betaUpdate = try {
            service.getLatestStoreUpdate(Channel.BETA).getOrThrow()
        } finally {
            service.close()
        }

        assertEquals("1.4.0-beta.1", betaUpdate?.versionInfo?.version)
        assertEquals("beta-pampa-store-v1.4.0-beta.1", betaUpdate?.versionInfo?.releaseTag)
    }

    @Test
    fun latestStoreUpdateReadsPublicManifestFromRawGitHubWithoutApiCalls() = runTest {
        val repository = GitHubRepositoryRef(repoOwner = "test", repoName = "store")
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    error("Unexpected API request: ${request.method.value} ${request.url}")
                }
            }
        }
        val assetClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        "https://raw.githubusercontent.com/test/store/HEAD/manifest.json" -> respond(
                            content = """
                                {
                                  "apps": [],
                                  "storeReleases": {
                                    "stable": {
                                      "version": "1.1.3",
                                      "releaseDate": "2026-03-28",
                                      "changelog": "Stable release",
                                      "releaseTag": "stable-pampa-store-v1.1.3",
                                      "apkAsset": "pampa-store-1.1.3.apk",
                                      "exeAsset": "pampa-store-1.1.3-installer.exe",
                                      "sizeBytes": 109702146
                                    }
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )

                        else -> error("Unexpected asset request: ${request.method.value} ${request.url}")
                    }
                }
            }
        }

        val service = GitHubCatalogService(
            storeRepository = repository,
            json = testJson(),
            apiClient = apiClient,
            assetClient = assetClient
        )

        val stableUpdate = try {
            service.getLatestStoreUpdate(Channel.STABLE).getOrThrow()
        } finally {
            service.close()
        }

        assertNotNull(stableUpdate)
        assertEquals("1.1.3", stableUpdate.versionInfo.version)
        assertEquals("stable-pampa-store-v1.1.3", stableUpdate.versionInfo.releaseTag)
    }

    @Test
    fun latestStoreUpdateBetaFallsBackToStableWhenNoPrereleaseExists() = runTest {
        val repository = GitHubRepositoryRef(repoOwner = "test", repoName = "store")
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when {
                        request.matches(HttpMethod.Get, "/repos/test/store/contents/manifest.json") ->
                            respond(
                                content = """{"message":"Not Found"}""",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        request.matches(HttpMethod.Get, "/repos/test/store") ->
                            respond(
                                content = """{"id":1}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        request.matches(HttpMethod.Get, "/repos/test/store/releases") ->
                            respond(
                                content = "[]",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        request.matches(HttpMethod.Get, "/repos/test/store/releases/latest") ->
                            respond(
                                content = """
                                    {
                                      "id": 99,
                                      "tag_name": "stable-pampa-store-v1.2.0",
                                      "name": "Pampa Store 1.2.0",
                                      "body": "Stable release",
                                      "prerelease": false,
                                      "published_at": "2026-03-10T10:00:00Z",
                                      "assets": []
                                    }
                                """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
        }

        val service = GitHubCatalogService(
            storeRepository = repository,
            json = testJson(),
            apiClient = apiClient,
            assetClient = emptyAssetClient()
        )

        val updateResult = try {
            service.getLatestStoreUpdate(Channel.BETA)
        } finally {
            service.close()
        }

        val update = updateResult.getOrThrow()
        assertEquals("1.2.0", update?.versionInfo?.version)
        assertEquals("stable-pampa-store-v1.2.0", update?.versionInfo?.releaseTag)
    }

    @Test
    fun ensureRepositoryReturnsAlreadyExistsWhenRepositoryAndManifestArePresent() = runTest {
        var createCalls = 0
        var manifestPutCalls = 0
        val repository = GitHubRepositoryRef(repoOwner = "owner", repoName = "app-repo")
        val service = GitHubAdminPublishingService(
            config = GitHubAdminConfig(
                storeRepository = GitHubRepositoryRef(repoOwner = "owner", repoName = "store"),
                token = "token"
            ),
            json = testJson(),
            apiClient = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(testJson()) }
                engine {
                    addHandler { request ->
                        when {
                            request.matches(HttpMethod.Get, "/repos/owner/app-repo") ->
                                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

                            request.matches(HttpMethod.Get, "/repos/owner/app-repo/contents/manifest.json") ->
                                respond("""{"sha":"abc"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

                            request.matches(HttpMethod.Post, "/user/repos") -> {
                                createCalls++
                                respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            request.matches(HttpMethod.Put, "/repos/owner/app-repo/contents/manifest.json") -> {
                                manifestPutCalls++
                                respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            else -> error("Unexpected request: ${request.method.value} ${request.url}")
                        }
                    }
                }
            }
        )

        val ensureResult = try {
            service.ensureRepository(
                repository = repository,
                appMetadata = RepositoryBootstrapMetadata(
                    appId = "codex",
                    appName = "Codex",
                    description = "Messaging"
                )
            )
        } finally {
            service.close()
        }

        val payload = ensureResult.getOrThrow()
        assertTrue(payload.alreadyExists)
        assertFalse(payload.created)
        assertFalse(payload.failed)
        assertEquals(0, createCalls)
        assertEquals(0, manifestPutCalls)
    }

    @Test
    fun ensureRepositoryCreatesPublicRepoAndInitialManifestWhenMissing() = runTest {
        var createCalls = 0
        var manifestPutCalls = 0
        var manifestLookupCalls = 0
        val repository = GitHubRepositoryRef(repoOwner = "owner", repoName = "new-app")
        val service = GitHubAdminPublishingService(
            config = GitHubAdminConfig(
                storeRepository = GitHubRepositoryRef(repoOwner = "owner", repoName = "store"),
                token = "token"
            ),
            json = testJson(),
            apiClient = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(testJson()) }
                engine {
                    addHandler { request ->
                        when {
                            request.matches(HttpMethod.Get, "/repos/owner/new-app") ->
                                respond("""{"message":"Not Found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))

                            request.matches(HttpMethod.Get, "/user") ->
                                respond("""{"login":"owner"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

                            request.matches(HttpMethod.Post, "/user/repos") -> {
                                createCalls++
                                respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            request.matches(HttpMethod.Get, "/repos/owner/new-app/contents/manifest.json") -> {
                                manifestLookupCalls++
                                respond("""{"message":"Not Found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            request.matches(HttpMethod.Put, "/repos/owner/new-app/contents/manifest.json") -> {
                                manifestPutCalls++
                                respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                            }

                            else -> error("Unexpected request: ${request.method.value} ${request.url}")
                        }
                    }
                }
            }
        )

        val ensureResult = try {
            service.ensureRepository(
                repository = repository,
                appMetadata = RepositoryBootstrapMetadata(
                    appId = "codex",
                    appName = "Codex",
                    description = "Messaging"
                )
            )
        } finally {
            service.close()
        }

        val payload = ensureResult.getOrThrow()
        assertTrue(payload.created || payload.alreadyExists, payload.reason)
        assertFalse(payload.failed)
        assertEquals(1, createCalls)
        assertTrue(manifestLookupCalls >= 1)
        assertEquals(1, manifestPutCalls)
    }

    @Test
    fun ensureRepositoryReturnsFailedWhenTokenCannotCreateOrgRepository() = runTest {
        val repository = GitHubRepositoryRef(repoOwner = "org-owner", repoName = "new-app")
        val service = GitHubAdminPublishingService(
            config = GitHubAdminConfig(
                storeRepository = GitHubRepositoryRef(repoOwner = "owner", repoName = "store"),
                token = "token"
            ),
            json = testJson(),
            apiClient = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(testJson()) }
                engine {
                    addHandler { request ->
                        when {
                            request.matches(HttpMethod.Get, "/repos/org-owner/new-app") ->
                                respond("""{"message":"Not Found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))

                            request.matches(HttpMethod.Get, "/user") ->
                                respond("""{"login":"owner"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

                            request.matches(HttpMethod.Post, "/orgs/org-owner/repos") ->
                                respond("""{"message":"Not Found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))

                            else -> error("Unexpected request: ${request.method.value} ${request.url}")
                        }
                    }
                }
            }
        )

        val ensureResult = try {
            service.ensureRepository(
                repository = repository,
                appMetadata = RepositoryBootstrapMetadata(
                    appId = "codex",
                    appName = "Codex",
                    description = "Messaging"
                )
            )
        } finally {
            service.close()
        }

        val payload = ensureResult.getOrThrow()
        assertTrue(payload.failed)
        assertTrue(payload.reason.isNotBlank())
    }

    @Test
    fun getStoreIndexParsesLegacyManifestWithoutRepoFields() = runTest {
        val repository = GitHubRepositoryRef(repoOwner = "owner", repoName = "store")
        val service = GitHubAdminPublishingService(
            config = GitHubAdminConfig(
                storeRepository = repository,
                token = "token"
            ),
            json = testJson(),
            apiClient = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(testJson()) }
                engine {
                    addHandler { request ->
                        when {
                            request.matches(HttpMethod.Get, "/repos/owner/store/contents/manifest.json") ->
                                respond(
                                    content = """
                                        {
                                          "apps": [
                                            {
                                              "id": "pampa-store",
                                              "name": "Pampa Store",
                                              "description": "Store app"
                                            },
                                            {
                                              "id": "codex",
                                              "name": "Codex",
                                              "description": "Messaging app"
                                            }
                                          ]
                                        }
                                    """.trimIndent(),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )

                            else -> error("Unexpected request: ${request.method.value} ${request.url}")
                        }
                    }
                }
            }
        )

        val result = try {
            service.getStoreIndex()
        } finally {
            service.close()
        }

        val index = result.getOrThrow()
        assertEquals(1, index.apps.size)
        assertEquals("codex", index.apps.first().id)
        assertEquals("owner", index.apps.first().repoOwner)
        assertEquals("store", index.apps.first().repoName)
    }
}

private fun mockApiClient(releaseJson: String): HttpClient {
    return HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = releaseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
    }
}

private fun emptyAssetClient(): HttpClient {
    return HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
                )
            }
        }
    }
}

private fun releaseJson(
    assetUrl: String,
    assetName: String,
    assetSize: Long,
    assetDigest: String
): String {
    return """
        {
          "id": 1,
          "tag_name": "test-tag",
          "name": "Test Release",
          "assets": [
            {
              "id": 99,
              "name": "$assetName",
              "size": $assetSize,
              "url": "$assetUrl",
              "browser_download_url": "$assetUrl",
              "digest": "$assetDigest"
            }
          ]
        }
    """.trimIndent()
}

private fun testJson() = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { "%02x".format(it) }
}

private fun HttpRequestData.matches(method: HttpMethod, path: String): Boolean {
    return this.method == method && url.encodedPath == path
}
