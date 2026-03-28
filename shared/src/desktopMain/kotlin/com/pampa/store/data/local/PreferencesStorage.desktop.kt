package com.pampa.store.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

actual class PreferencesStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val configDir = File(System.getProperty("user.home"), ".pampastore").also { it.mkdirs() }
    private val file = File(configDir, "store_prefs.json")

    actual suspend fun load(): StorePreferences {
        return try {
            if (file.exists()) {
                json.decodeFromString(file.readText())
            } else {
                StorePreferences(
                    downloadPath = File(configDir, "downloads").absolutePath
                )
            }
        } catch (e: Exception) {
            StorePreferences(
                downloadPath = File(configDir, "downloads").absolutePath
            )
        }
    }

    actual suspend fun save(preferences: StorePreferences) {
        file.writeText(json.encodeToString(preferences))
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
