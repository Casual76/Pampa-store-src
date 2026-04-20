package com.pampa.store.domain.usecase.admin

import com.pampa.store.data.api.EnsureRepositoryResult
import com.pampa.store.data.api.GitHubRelease
import com.pampa.store.data.api.RepositoryBootstrapMetadata
import com.pampa.store.data.model.AppManifest
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.data.model.StoreIndexManifest
import com.pampa.store.domain.repository.PublishingRepository

class GetStoreIndexUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(): Result<StoreIndexManifest> = repository.getStoreIndex()
}

class UpdateStoreIndexUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(index: StoreIndexManifest): Result<Unit> = repository.updateStoreIndex(index)
}

class EnsureRepositoryUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(repo: GitHubRepositoryRef, metadata: RepositoryBootstrapMetadata): Result<EnsureRepositoryResult> = 
        repository.ensureRepository(repo, metadata)
}

class UpdateAppManifestUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(repo: GitHubRepositoryRef, manifest: AppManifest): Result<Unit> = 
        repository.updateAppManifest(repo, manifest)
}

class GetLatestStoreReleaseUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(): Result<GitHubRelease?> = repository.getLatestStoreRelease()
}

class CreateReleaseUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(repo: GitHubRepositoryRef, tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease> = 
        repository.createRelease(repo, tag, name, body, prerelease)
}

class CreateStoreReleaseUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(tag: String, name: String, body: String, prerelease: Boolean): Result<GitHubRelease> = 
        repository.createStoreRelease(tag, name, body, prerelease)
}

class UploadAssetUseCase(private val repository: PublishingRepository) {
    suspend operator fun invoke(uploadUrl: String, fileBytes: ByteArray, fileName: String, contentType: String): Result<Unit> = 
        repository.uploadAsset(uploadUrl, fileBytes, fileName, contentType)
}

data class AdminUseCases(
    val getStoreIndex: GetStoreIndexUseCase,
    val updateStoreIndex: UpdateStoreIndexUseCase,
    val ensureRepository: EnsureRepositoryUseCase,
    val updateAppManifest: UpdateAppManifestUseCase,
    val getLatestStoreRelease: GetLatestStoreReleaseUseCase,
    val createRelease: CreateReleaseUseCase,
    val createStoreRelease: CreateStoreReleaseUseCase,
    val uploadAsset: UploadAssetUseCase
)
