package com.pampa.store.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun legacyWindowsFieldIsIgnored() {
        val version = json.decodeFromString<VersionInfo>(
            """{"version":"1.2.3","releaseTag":"stable-v1.2.3","apkAsset":"app.apk","exeAsset":"old.exe"}""",
        )
        assertEquals("app.apk", version.apkAsset)
        assertFalse(json.encodeToString(version).contains("exeAsset"))
    }

    @Test
    fun androidOnlyManifestParsesWithoutLegacyFields() {
        val version = json.decodeFromString<VersionInfo>(
            """{"version":"2.0.0","releaseTag":"stable-v2.0.0","apkAsset":"pampa.apk","sizeBytes":42}""",
        )
        assertEquals("2.0.0", version.version)
        assertEquals("pampa.apk", version.apkAsset)
        assertEquals(42L, version.sizeBytes)
    }

    @Test
    fun cachedCatalogRoundTrips() {
        val catalog = CachedCatalog(
            apps = listOf(fixtureApp("one", installedPackage = "com.example.one")),
            savedAt = 42L,
        )
        assertEquals(catalog, json.decodeFromString<CachedCatalog>(json.encodeToString(catalog)))
    }

    @Test
    fun uiStateFiltersInstalledAndSearch() {
        val installed = StoreApp(fixtureApp("voice", "com.example.voice"), installedVersion = "1.0")
        val available = StoreApp(fixtureApp("gallery", "com.example.gallery"))
        val state = StoreUiState(
            apps = listOf(installed, available),
            ownershipFilter = OwnershipFilter.INSTALLED,
            searchQuery = "voice",
        )
        assertEquals(listOf("voice"), state.visibleApps.map { it.entry.id })
        assertTrue(state.installedCount == 1)
    }

    @Test
    fun uiStateCountsAndFiltersUpdatesAndCategories() {
        val installed = StoreApp(
            fixtureApp("voice", "com.example.voice").copy(category = "Utility", beta = VersionInfo("1.1-beta")),
            installedVersion = "1.0",
            hasUpdate = true,
        )
        val current = StoreApp(
            fixtureApp("school", "com.example.school").copy(category = "Education"),
            installedVersion = "1.0",
        )
        val available = StoreApp(fixtureApp("gallery", "com.example.gallery").copy(category = "Utility"))
        val state = StoreUiState(apps = listOf(installed, current, available), isLoading = false)

        assertEquals(2, state.installedCount)
        assertEquals(1, state.updateCount)
        assertEquals(1, state.betaCount)
        assertEquals(listOf("Education", "Utility"), state.categories)
        assertEquals(
            listOf("voice"),
            state.copy(ownershipFilter = OwnershipFilter.UPDATABLE).visibleApps.map { it.entry.id },
        )
        assertEquals(
            listOf("voice", "gallery"),
            state.copy(selectedCategory = "Utility").visibleApps.map { it.entry.id },
        )
    }

    @Test
    fun channelsPreferencesAndInstallStatesHaveSafeFallbacks() {
        val stable = VersionInfo(version = "1.0", apkAsset = "stable.apk")
        val beta = VersionInfo(version = "1.1-beta", apkAsset = "beta.apk")
        val app = fixtureApp("voice", "com.example.voice").copy(stable = stable, beta = beta)

        assertEquals(stable, app.versionFor(Channel.STABLE))
        assertEquals(beta, app.versionFor(Channel.BETA))
        assertEquals(stable, app.copy(beta = null).versionFor(Channel.BETA))
        assertEquals(AppThemeMode.SYSTEM, enumOrDefault("REMOVED_MODE", AppThemeMode.SYSTEM))
        assertEquals(AccentPresetId.NEBULA_76.storageName, StorePreferences().accentPreset)
        val downloading = DownloadState.Downloading(0.5f, 5, 10)
        val failure = InstallEvent.Failure("permission", "missing")
        assertEquals(0.5f, downloading.progress)
        assertEquals("permission", failure.code)
    }

    private fun fixtureApp(id: String, installedPackage: String) = AppEntry(
        id = id,
        name = id.replaceFirstChar(Char::uppercase),
        description = "Test app",
        packageName = installedPackage,
        stable = VersionInfo(version = "1.0", releaseTag = "stable-$id-v1.0", apkAsset = "$id.apk"),
    )
}
