package com.pampa.store.domain.repository

import com.pampa.store.data.api.EnsureRepositoryResult
import com.pampa.store.data.api.GitHubRelease
import com.pampa.store.data.api.RepositoryBootstrapMetadata
import com.pampa.store.data.model.AppManifest
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.StoreIndexManifest

interface PublishingRepository {
    val isConfigured: Boolean
    val disabledReason: String?
    val storeRepository: GitHubRepositoryRef?

    suspend fun getStoreIndex(): Result<StoreIndexManifest>
    suspend fun updateStoreIndex(index: StoreIndexManifest): Result<Unit>
    suspend fun ensureRepository(repository: GitHubRepositoryRef, appMetadata: RepositoryBootstrapMetadata): Result<EnsureRepositoryResult>
    suspend fun getAppManifest(repository: GitHubRepositoryRef): Result<AppManifest>
    suspend fun updateAppManifest(repository: GitHubRepositoryRef, manifest: AppManifest): Result<Unit>
    suspend fun getLatestStoreRelease(): Result<GitHubRelease?>
    suspend fun createRelease(repository: GitHubRepositoryRef, tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease>
    suspend fun createStoreRelease(tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease>
    suspend fun uploadAsset(uploadUrl: String, fileBytes: ByteArray, fileName: String, contentType: String): Result<Unit>
    fun close()
}
