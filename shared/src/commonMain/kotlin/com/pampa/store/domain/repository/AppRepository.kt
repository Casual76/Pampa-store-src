package com.pampa.store.domain.repository

import com.pampa.store.data.api.DownloadEvent
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.ReleaseAssetSource
import com.pampa.store.data.model.StoreManifest
import com.pampa.store.data.model.StoreUpdateInfo
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    suspend fun getStoreManifest(): Result<StoreManifest>
    suspend fun getLatestStoreUpdate(channel: Channel): Result<StoreUpdateInfo?>
    fun downloadAsset(source: ReleaseAssetSource, savePath: String, onProgress: (Float) -> Unit): Flow<DownloadEvent>
    fun close()
}
