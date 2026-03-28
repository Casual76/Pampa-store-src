package com.pampa.store.data.local

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

actual class PreferencesStorage(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file get() = File(context.filesDir, "store_prefs.json")

    actual suspend fun load(): StorePreferences {
        return try {
            if (file.exists()) {
                json.decodeFromString(file.readText())
            } else {
                StorePreferences(
                    downloadPath = File(context.getExternalFilesDir(null), "downloads").absolutePath
                )
            }
        } catch (e: Exception) {
            StorePreferences(
                downloadPath = File(context.getExternalFilesDir(null), "downloads").absolutePath
            )
        }
    }

    actual suspend fun save(preferences: StorePreferences) {
        file.writeText(json.encodeToString(preferences))
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
