package com.pampa.store.data.repository

import com.pampa.store.data.api.AdminPublishingService
import com.pampa.store.data.api.EnsureRepositoryResult
import com.pampa.store.data.api.GitHubRelease
import com.pampa.store.data.api.RepositoryBootstrapMetadata
import com.pampa.store.data.model.AppManifest
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.StoreIndexManifest
import com.pampa.store.domain.repository.PublishingRepository

class DefaultPublishingRepository(
    private val adminPublishingService: AdminPublishingService
) : PublishingRepository {
    override val isConfigured: Boolean get() = adminPublishingService.isConfigured
    override val disabledReason: String? get() = adminPublishingService.disabledReason
    override val storeRepository: GitHubRepositoryRef? get() = adminPublishingService.storeRepository

    override suspend fun getStoreIndex(): Result<StoreIndexManifest> = adminPublishingService.getStoreIndex()
    override suspend fun updateStoreIndex(index: StoreIndexManifest): Result<Unit> = adminPublishingService.updateStoreIndex(index)
    override suspend fun ensureRepository(repository: GitHubRepositoryRef, appMetadata: RepositoryBootstrapMetadata): Result<EnsureRepositoryResult> = adminPublishingService.ensureRepository(repository, appMetadata)
    override suspend fun getAppManifest(repository: GitHubRepositoryRef): Result<AppManifest> = adminPublishingService.getAppManifest(repository)
    override suspend fun updateAppManifest(repository: GitHubRepositoryRef, manifest: AppManifest): Result<Unit> = adminPublishingService.updateAppManifest(repository, manifest)
    override suspend fun getLatestStoreRelease(): Result<GitHubRelease?> = adminPublishingService.getLatestStoreRelease()
    override suspend fun createRelease(repository: GitHubRepositoryRef, tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease> = adminPublishingService.createRelease(repository, tag, name, body, prerelease)
    override suspend fun createStoreRelease(tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease> = adminPublishingService.createStoreRelease(tag, name, body, prerelease)
    override suspend fun uploadAsset(uploadUrl: String, fileBytes: ByteArray, fileName: String, contentType: String): Result<Unit> = adminPublishingService.uploadAsset(uploadUrl, fileBytes, fileName, contentType)
    
    override fun close() {
        adminPublishingService.close()
    }
}
