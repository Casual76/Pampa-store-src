package com.pampa.store.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.storeDataStore by preferencesDataStore(name = "pampa_store")

class StorePersistence(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val preferences: Flow<StorePreferences> = context.storeDataStore.data.map { values ->
        StorePreferences(
            themeMode = values[Keys.themeMode] ?: AppThemeMode.SYSTEM.name,
            accentSource = values[Keys.accentSource] ?: AccentSource.PRESET.name,
            accentPreset = values[Keys.accentPreset] ?: AccentPresetId.NEBULA_76.storageName,
            defaultChannel = values[Keys.defaultChannel] ?: Channel.STABLE.name,
            storeUpdateChannel = values[Keys.storeUpdateChannel] ?: Channel.STABLE.name,
            autoCheckStoreUpdates = values[Keys.autoCheckStoreUpdates] ?: true,
        )
    }

    suspend fun updatePreferences(transform: (StorePreferences) -> StorePreferences) {
        val current = preferences.first()
        val updated = transform(current)
        context.storeDataStore.edit { values ->
            values[Keys.themeMode] = updated.themeMode
            values[Keys.accentSource] = updated.accentSource
            values[Keys.accentPreset] = updated.accentPreset
            values[Keys.defaultChannel] = updated.defaultChannel
            values[Keys.storeUpdateChannel] = updated.storeUpdateChannel
            values[Keys.autoCheckStoreUpdates] = updated.autoCheckStoreUpdates
        }
    }

    suspend fun readCachedCatalog(): CachedCatalog? {
        val values = context.storeDataStore.data.first()
        val encoded = values[Keys.catalogJson] ?: return null
        return runCatching { json.decodeFromString<CachedCatalog>(encoded) }.getOrNull()
    }

    suspend fun writeCachedCatalog(catalog: CachedCatalog) {
        context.storeDataStore.edit { values ->
            values[Keys.catalogJson] = json.encodeToString(catalog)
            values[Keys.catalogSavedAt] = catalog.savedAt
        }
    }

    /**
     * Quali versioni sono già state annunciate, per app.
     *
     * Il controllo in background gira ogni sei ore e un aggiornamento non installato resta lì per
     * giorni: senza memoria di cosa è già stato detto, la stessa versione della stessa app
     * produrrebbe quattro notifiche al giorno.
     */
    suspend fun readAnnouncedVersions(): Map<String, String> {
        val encoded = context.storeDataStore.data.first()[Keys.announcedVersions] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrDefault(emptyMap())
    }

    suspend fun writeAnnouncedVersions(versions: Map<String, String>) {
        context.storeDataStore.edit { values ->
            values[Keys.announcedVersions] = json.encodeToString(versions)
        }
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val accentSource = stringPreferencesKey("accent_source")
        val accentPreset = stringPreferencesKey("accent_preset")
        val defaultChannel = stringPreferencesKey("default_channel")
        val storeUpdateChannel = stringPreferencesKey("store_update_channel")
        val autoCheckStoreUpdates = booleanPreferencesKey("auto_check_store_updates")
        val catalogJson = stringPreferencesKey("catalog_json")
        val catalogSavedAt = longPreferencesKey("catalog_saved_at")
        val announcedVersions = stringPreferencesKey("announced_versions")
    }
}

inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
