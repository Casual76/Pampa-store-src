package com.pampa.store.domain.repository

import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.InstalledAppInfo
import com.pampa.store.data.model.PendingReleaseDraft
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.local.StorePreferences
import kotlinx.coroutines.flow.StateFlow

interface PreferencesRepository {
    val preferences: StateFlow<StorePreferences>
    
    suspend fun init()
    suspend fun getInstalledInfo(appId: String): InstalledAppInfo?
    suspend fun markInstalled(appId: String, version: String, channel: Channel, path: String)
    suspend fun markUninstalled(appId: String)
    fun getDefaultChannel(): Channel
    fun getStoreUpdateChannel(): Channel
    suspend fun saveReleaseAutomationProfiles(profiles: List<ReleaseAutomationProfile>)
    suspend fun savePendingReleaseDrafts(drafts: List<PendingReleaseDraft>)
    suspend fun isReleaseArtifactHandled(signature: String): Boolean
    suspend fun markReleaseArtifactsHandled(signatures: List<String>)
}
