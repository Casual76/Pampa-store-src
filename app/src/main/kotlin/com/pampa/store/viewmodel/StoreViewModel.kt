package com.pampa.store.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pampa.store.data.Channel
import com.pampa.store.data.OwnershipFilter
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StoreEvent
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.VersionInfo
import com.pampa.store.engine.StoreEngine
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Il guscio della UI intorno a [StoreEngine]: stesso stato, stesse azioni, nessuna logica. La
 * logica sta nell'engine perche' la usa anche il provider del bridge (PampAI/Aria), nello stesso
 * processo e sullo stesso stato — e perche' un download non deve morire con la schermata.
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {
  private val engine = StoreEngine.get(application)

  val uiState: StateFlow<StoreUiState> = engine.uiState
  val events: SharedFlow<StoreEvent> = engine.events

  fun refreshCatalog(initial: Boolean = false) = engine.refreshCatalog(initial)
  fun refreshInstalledApps() = engine.refreshInstalledApps()
  fun refreshPrerequisites() = engine.refreshPrerequisites()
  fun refreshNotificationPermission() = engine.refreshNotificationPermission()
  fun app(appId: String): StoreApp? = engine.app(appId)
  fun setSearchQuery(query: String) = engine.setSearchQuery(query)
  fun setCategory(category: String?) = engine.setCategory(category)
  fun setOwnershipFilter(filter: OwnershipFilter) = engine.setOwnershipFilter(filter)
  /** Le preferenze cambiate da chi ha il modello sotto mano (la sezione Aspetto passa dalle impostazioni dell'engine). */
  fun updatePreferences(transform: (StorePreferences) -> StorePreferences) = engine.updatePreferences(transform)
  fun setDefaultChannel(channel: Channel) = engine.updatePreferences { it.copy(defaultChannel = channel.name) }
  fun setStoreUpdateChannel(channel: Channel) = engine.updatePreferences { it.copy(storeUpdateChannel = channel.name) }
  fun setAutoCheckStoreUpdates(enabled: Boolean) = engine.updatePreferences { it.copy(autoCheckStoreUpdates = enabled) }
  fun openInstallSettings() = engine.openInstallSettings()
  fun openNotificationSettings() = engine.openNotificationSettings()
  fun openApp(packageName: String) {
    engine.openApp(packageName)
  }
  fun installApp(appId: String, channel: Channel?) = engine.installApp(appId, channel)
  fun updateOne(appId: String) = engine.updateOne(appId)
  fun installVersion(appId: String, version: VersionInfo) = engine.installVersion(appId, version)
  fun cancelPendingReinstall() = engine.cancelPendingReinstall()
  fun updateAll() {
    engine.updateAll()
  }
  fun installStoreUpdate() = engine.installStoreUpdate()
  fun retryFailed() = engine.retryFailed()
  fun clearQueue() = engine.clearQueue()
  fun checkStoreUpdateNow() = engine.checkStoreUpdateNow()
  fun clearMessage() = engine.clearMessage()
}
