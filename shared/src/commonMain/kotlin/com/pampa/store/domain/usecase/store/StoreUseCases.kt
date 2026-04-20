package com.pampa.store.domain.usecase.store

import com.pampa.store.data.api.DownloadEvent
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.ReleaseAssetSource
import com.pampa.store.data.model.StoreManifest
import com.pampa.store.data.model.StoreUpdateInfo
import com.pampa.store.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow

class GetStoreCatalogUseCase(private val repository: AppRepository) {
    suspend operator fun invoke(): Result<StoreManifest> = repository.getStoreManifest()
}

class GetStoreUpdateUseCase(private val repository: AppRepository) {
    suspend operator fun invoke(channel: Channel): Result<StoreUpdateInfo?> = repository.getLatestStoreUpdate(channel)
}

class DownloadAppAssetUseCase(private val repository: AppRepository) {
    operator fun invoke(source: ReleaseAssetSource, savePath: String, onProgress: (Float) -> Unit): Flow<DownloadEvent> {
        return repository.downloadAsset(source, savePath, onProgress)
    }
}

data class StoreUseCases(
    val getStoreCatalog: GetStoreCatalogUseCase,
    val getStoreUpdate: GetStoreUpdateUseCase,
    val downloadAppAsset: DownloadAppAssetUseCase
)
