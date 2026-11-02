package com.pampa.store.ui

import com.pampa.store.data.AccentPresetId
import com.pampa.store.data.AccentSource
import com.pampa.store.data.AppThemeMode
import com.pampa.store.data.StorePreferences
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidAccentChoice
import dev.antigravity.fluidengine.ui.fluid.FluidAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il ponte fra quello che lo Store salva da sempre e le impostazioni dell'engine che la sezione
 * «Aspetto» legge e riscrive. Le preferenze di chi aggiorna dalla 1.8.x devono dire la stessa cosa.
 */
class AppearanceMappingTest {

    @Test
    fun theDefaultsOfAnUpdatedInstallAreTheDefaultsOfTheEngine() {
        // Un'installazione che aggiorna non ha ancora le due chiavi nuove: valgono i default.
        val settings = StorePreferences().toEngineSettings()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AccentMode.BRAND, settings.accentMode)
        assertFalse(settings.amoledEnabled)
        assertTrue("l'aptica era sempre accesa: resta accesa", settings.hapticsEnabled)
    }

    @Test
    fun everySavedThemeAndPresetSurvivesTheRoundTrip() {
        for (theme in AppThemeMode.entries) {
            for (preset in AccentPresetId.entries) {
                for (source in AccentSource.entries) {
                    val saved = StorePreferences(
                        themeMode = theme.name,
                        accentSource = source.name,
                        accentPreset = preset.storageName,
                    )
                    val back = saved.withEngineSettings(saved.toEngineSettings())
                    assertEquals("$theme $preset $source", saved, back)
                }
            }
        }
    }

    @Test
    fun pureBlackAndHapticsRoundTripToo() {
        val saved = StorePreferences(pureBlack = true, hapticsEnabled = false)
        val back = saved.withEngineSettings(saved.toEngineSettings())
        assertEquals(saved, back)
    }

    @Test
    fun pickingAPresetInTheSectionSavesItAndLeavesPresetSource() {
        val saved = StorePreferences(
            accentSource = AccentSource.DYNAMIC.name,
            accentPreset = AccentPresetId.ION_BLUE.storageName,
        )
        val next = FluidAppearance.withAccent(
            saved.toEngineSettings(),
            FluidAccentChoice.Preset(AccentPresetId.CORAL_PULSE.storageName),
        )
        val after = saved.withEngineSettings(next)
        assertEquals(AccentSource.PRESET.name, after.accentSource)
        assertEquals(AccentPresetId.CORAL_PULSE.storageName, after.accentPreset)
    }

    @Test
    fun theBrandDotIsNebula76() {
        val saved = StorePreferences(accentPreset = AccentPresetId.AURORA_MINT.storageName)
        val after = saved.withEngineSettings(
            FluidAppearance.withAccent(saved.toEngineSettings(), FluidAccentChoice.Brand),
        )
        assertEquals(AccentSource.PRESET.name, after.accentSource)
        assertEquals(AccentPresetId.NEBULA_76.storageName, after.accentPreset)
    }

    @Test
    fun systemColoursKeepTheRememberedPresetForTheWayBack() {
        val saved = StorePreferences(accentPreset = AccentPresetId.SOLAR_AMBER.storageName)
        val dynamic = saved.withEngineSettings(
            FluidAppearance.withAccent(saved.toEngineSettings(), FluidAccentChoice.Dynamic),
        )
        assertEquals(AccentSource.DYNAMIC.name, dynamic.accentSource)
        assertEquals(AccentPresetId.SOLAR_AMBER.storageName, dynamic.accentPreset)
        // ... e tornando ai preset, l'ambra e' ancora li'.
        assertEquals(AccentPresetId.SOLAR_AMBER, dynamic.selectedAccentPreset())
    }

    @Test
    fun blackIsAModeOfDarkNotATheme() {
        val saved = StorePreferences(themeMode = AppThemeMode.LIGHT.name)
        val black = saved.withEngineSettings(
            FluidAppearance.withThemeMode(saved.toEngineSettings(), ThemeMode.DARK).copy(amoledEnabled = true),
        )
        assertEquals(AppThemeMode.DARK.name, black.themeMode)
        assertTrue(black.pureBlack)
        // AMOLED, che la sezione non offre, si legge come "Scuro" piu' il nero.
        val amoled = saved.withEngineSettings(EngineSettings(themeMode = ThemeMode.AMOLED))
        assertEquals(AppThemeMode.DARK.name, amoled.themeMode)
        assertTrue(amoled.pureBlack)
    }

    @Test
    fun anUnknownSavedPresetFallsBackToTheBrand() {
        val saved = StorePreferences(accentPreset = "viola-che-non-esiste")
        assertEquals(AccentPresetId.NEBULA_76, saved.selectedAccentPreset())
        assertEquals(AccentMode.BRAND, saved.toEngineSettings().accentMode)
    }

    @Test
    fun theSelectablePresetsAreTheOthersAndNotTheBrandTwice() {
        assertEquals(AccentPresetId.entries.size - 1, PampaSelectablePresets.size)
        assertFalse(PampaSelectablePresets.any { it.name == PampaBrand.name })
        assertEquals(AccentPresetId.NEBULA_76.storageName, PampaBrand.name)
    }
}
