package com.pampa.store.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pampa.store.data.AccentPresetId
import com.pampa.store.data.AccentSource
import com.pampa.store.data.AppThemeMode
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.enumOrDefault
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * I preset, derivati dall'enum invece che riscritti accanto.
 *
 * Erano due liste che dovevano restare uguali — l'enum che la preferenza salva e questa che il tema
 * legge — e due liste di quel tipo divergono al primo preset aggiunto: il nuovo compare fra le
 * scelte e poi non tinge niente, oppure tinge e non compare.
 */
val PampaAccentPresets = AccentPresetId.entries.map { preset ->
    AccentPreset(
        preset.storageName,
        preset.label,
        Color(preset.lightSeed),
        Color(preset.darkSeed),
    )
}

@Composable
fun PampaTheme(
    preferences: StorePreferences,
    content: @Composable () -> Unit,
) {
    val themeMode = when (enumOrDefault(preferences.themeMode, AppThemeMode.SYSTEM)) {
        AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
        AppThemeMode.LIGHT -> ThemeMode.LIGHT
        AppThemeMode.DARK -> ThemeMode.DARK
    }
    val source = enumOrDefault(preferences.accentSource, AccentSource.PRESET)
    val preset = PampaAccentPresets.firstOrNull { it.name == preferences.accentPreset }
        ?: PampaAccentPresets.first()
    val settings = EngineSettings(
        themeMode = themeMode,
        accentMode = if (source == AccentSource.DYNAMIC) AccentMode.DYNAMIC else AccentMode.BRAND,
        customAccentName = preset.name,
        dynamicColorEnabled = source == AccentSource.DYNAMIC,
    )
    FluidTheme(settings = settings, brand = preset, content = content)
}

fun StorePreferences.selectedAccentPreset(): AccentPresetId =
    AccentPresetId.entries.firstOrNull { it.storageName == accentPreset } ?: AccentPresetId.NEBULA_76
