package com.pampa.store.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.pampa.store.data.AppEntry
import com.pampa.store.data.InstallPrerequisites
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.VersionInfo
import org.junit.Rule
import org.junit.Test

class StoreScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsCollectionHeroAndApps() {
        composeRule.setContent {
            PampaTheme(StorePreferences()) {
                HomeScreen(
                    state = fixtureState(),
                    onRefresh = {},
                    onOpenApp = {},
                    onOpenCatalog = {},
                    onShowUpdates = {},
                )
            }
        }
        composeRule.onNodeWithTag("home-hero").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aggiorna catalogo").assertIsDisplayed()
        composeRule.onNodeWithText("LA TUA RACCOLTA").assertIsDisplayed()
        composeRule.onNodeWithText("KeyVoice").assertIsDisplayed()
    }

    @Test
    fun catalogExposesSearchAndFilters() {
        composeRule.setContent {
            PampaTheme(StorePreferences()) {
                CatalogScreen(
                    state = fixtureState(),
                    onRefresh = {},
                    onSearch = {},
                    onCategory = {},
                    onOwnership = {},
                    onOpenApp = {},
                )
            }
        }
        composeRule.onNodeWithTag("catalog-search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aggiorna catalogo").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog-ownership-filter").assertIsDisplayed()
        composeRule.onNodeWithText("KeyVoice").assertIsDisplayed()
    }

    @Test
    fun detailShowsInstallPermissionAndReleaseChannel() {
        composeRule.setContent {
            PampaTheme(StorePreferences()) {
                DetailScreen(
                    app = fixtureState().apps.first(),
                    preferences = StorePreferences(),
                    prerequisites = InstallPrerequisites(
                        canInstallPackages = false,
                        message = "Abilita le origini consentite.",
                    ),
                    onBack = {},
                    onInstall = {},
                    onOpen = {},
                    onOpenInstallSettings = {},
                )
            }
        }
        composeRule.onNodeWithTag("detail-hero").assertIsDisplayed()
        composeRule.onNodeWithText("Autorizzazione necessaria").assertIsDisplayed()
        composeRule.onNodeWithText("Stable").assertIsDisplayed()
    }

    @Test
    fun catalogShowsEmptyAndErrorStates() {
        composeRule.setContent {
            PampaTheme(StorePreferences()) {
                CatalogScreen(
                    state = StoreUiState(isLoading = false, error = "Rete non disponibile"),
                    onRefresh = {},
                    onSearch = {},
                    onCategory = {},
                    onOwnership = {},
                    onOpenApp = {},
                )
            }
        }
        composeRule.onNodeWithText("Il catalogo non si è caricato").assertIsDisplayed()
        composeRule.onNodeWithText("Rete non disponibile").assertIsDisplayed()
    }

    @Test
    fun moreExposesThemeAndUnknownSourcesControls() {
        composeRule.setContent {
            PampaTheme(StorePreferences()) {
                MoreScreen(
                    state = fixtureState().copy(
                        prerequisites = InstallPrerequisites(false, "Autorizzazione necessaria per installare APK."),
                    ),
                    onRefresh = {},
                    onThemeMode = {},
                    onAccentSource = {},
                    onAccentPreset = {},
                    onDefaultChannel = {},
                    onStoreChannel = {},
                    onAutoCheck = {},
                    onOpenInstallSettings = {},
                    onInstallStoreUpdate = {},
                )
            }
        }
        composeRule.onNodeWithTag("appearance-settings").assertIsDisplayed()
        composeRule.onNodeWithText("Nebula 76").assertIsDisplayed()
        composeRule.onNodeWithText("Origini consentite").assertIsDisplayed()
    }

    private fun fixtureState() = StoreUiState(
        apps = listOf(
            StoreApp(
                entry = AppEntry(
                    id = "keyvoice",
                    name = "KeyVoice",
                    description = "Voce e accessibilità",
                    packageName = "com.pampa.keyvoice",
                    category = "Accessibilità",
                    stable = VersionInfo("2.1.0", apkAsset = "keyvoice.apk"),
                ),
                installedVersion = "2.0.0",
                hasUpdate = true,
            ),
        ),
        isLoading = false,
        preferences = StorePreferences(),
    )
}
