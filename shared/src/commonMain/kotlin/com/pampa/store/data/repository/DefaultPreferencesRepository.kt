package com.pampa.store.data.repository

import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.model.Channel
import com.pampa.store.data.model.InstalledAppInfo
import com.pampa.store.data.model.PendingReleaseDraft
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.local.StorePreferences
import com.pampa.store.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.StateFlow

class DefaultPreferencesRepository(
    private val localStorage: LocalStorage
) : PreferencesRepository {
    override val preferences: StateFlow<StorePreferences> get() = localStorage.preferences

    override suspend fun init() = localStorage.init()
    
    override suspend fun getInstalledInfo(appId: String): InstalledAppInfo? = localStorage.getInstalledInfo(appId)
    
    override suspend fun markInstalled(appId: String, version: String, channel: Channel, path: String) {
        localStorage.markInstalled(appId, version, channel, path)
    }
    
    override suspend fun markUninstalled(appId: String) {
        localStorage.markUninstalled(appId)
    }
    
    override fun getDefaultChannel(): Channel = localStorage.getDefaultChannel()
    
    override fun getStoreUpdateChannel(): Channel = localStorage.getStoreUpdateChannel()
    
    override suspend fun saveReleaseAutomationProfiles(profiles: List<ReleaseAutomationProfile>) {
        localStorage.saveReleaseAutomationProfiles(profiles)
    }
    
    override suspend fun savePendingReleaseDrafts(drafts: List<PendingReleaseDraft>) {
        localStorage.savePendingReleaseDrafts(drafts)
    }
    
    override suspend fun isReleaseArtifactHandled(signature: String): Boolean = localStorage.isReleaseArtifactHandled(signature)
    
    override suspend fun markReleaseArtifactsHandled(signatures: List<String>) {
        localStorage.markReleaseArtifactsHandled(signatures)
    }
}
