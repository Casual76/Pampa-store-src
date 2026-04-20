package com.pampa.store.data.repository

import com.pampa.store.data.api.CatalogService
import com.pampa.store.data.api.DownloadEvent
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.ReleaseAssetSource
import com.pampa.store.data.model.StoreManifest
import com.pampa.store.data.model.StoreUpdateInfo
import com.pampa.store.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow

class DefaultAppRepository(
    private val catalogService: CatalogService
) : AppRepository {
    override suspend fun getStoreManifest(): Result<StoreManifest> = catalogService.getManifest()
    
    override suspend fun getLatestStoreUpdate(channel: Channel): Result<StoreUpdateInfo?> = 
        catalogService.getLatestStoreUpdate(channel)
        
    override fun downloadAsset(source: ReleaseAssetSource, savePath: String, onProgress: (Float) -> Unit): Flow<DownloadEvent> =
        catalogService.downloadAsset(source, savePath, onProgress)
        
    override fun close() {
        catalogService.close()
    }
}
