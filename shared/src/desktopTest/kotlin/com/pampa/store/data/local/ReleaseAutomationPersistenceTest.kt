package com.pampa.store.data.local

import com.pampa.store.data.model.DetectedArtifact
import com.pampa.store.data.model.PendingReleaseDraft
import com.pampa.store.data.model.ReleaseArtifactKind
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.model.ReleaseAutomationTarget
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ReleaseAutomationPersistenceTest {
    private val originalUserHome = System.getProperty("user.home")

    @AfterTest
    fun restoreUserHome() {
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun legacyPreferencesLoadWithAutomationDefaults() = runTest {
        val tempHome = Files.createTempDirectory("pampastore-legacy-prefs").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        val configDir = File(tempHome, ".pampastore").also { it.mkdirs() }
        File(configDir, "store_prefs.json").writeText(
            """
            {
              "defaultChannel":"STABLE",
              "storeUpdateChannel":"BETA",
              "darkMode":true,
              "downloadPath":"C:/downloads",
              "installedApps":{},
              "adminPanelEnabled":false,
              "storeUpdateAutoCheckEnabled":true
            }
            """.trimIndent()
        )

        val localStorage = LocalStorage(PreferencesStorage())
        localStorage.init()

        val prefs = localStorage.preferences.value
        assertEquals("STABLE", prefs.defaultChannel)
        assertEquals("BETA", prefs.storeUpdateChannel)
        assertTrue(prefs.releaseAutomationProfiles.isEmpty())
        assertTrue(prefs.pendingReleaseDrafts.isEmpty())
        assertTrue(prefs.handledReleaseArtifacts.isEmpty())
    }

    @Test
    fun automationPreferencesPersistAcrossReload() = runTest {
        val tempHome = Files.createTempDirectory("pampastore-automation-prefs").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        val profile = ReleaseAutomationProfile(
            id = "store-release",
            name = "Store release",
            target = ReleaseAutomationTarget.Store,
            windowsDirectory = "C:/build/out"
        )
        val draft = PendingReleaseDraft(
            id = "draft-1",
            profileId = profile.id,
            profileName = profile.name,
            target = profile.target,
            version = "1.2.0",
            windowsArtifact = DetectedArtifact(
                kind = ReleaseArtifactKind.WINDOWS,
                path = "C:/build/out/Pampa Store-1.2.0.exe",
                fileName = "Pampa Store-1.2.0.exe",
                version = "1.2.0",
                signature = "sig-win"
            )
        )

        val firstStorage = LocalStorage(PreferencesStorage())
        firstStorage.init()
        firstStorage.saveReleaseAutomationProfiles(listOf(profile))
        firstStorage.savePendingReleaseDrafts(listOf(draft))
        firstStorage.markReleaseArtifactsHandled(listOf("sig-win"))

        val secondStorage = LocalStorage(PreferencesStorage())
        secondStorage.init()

        val prefs = secondStorage.preferences.value
        assertEquals(listOf(profile), prefs.releaseAutomationProfiles)
        assertEquals(listOf(draft), prefs.pendingReleaseDrafts)
        assertTrue("sig-win" in prefs.handledReleaseArtifacts)
    }
}
