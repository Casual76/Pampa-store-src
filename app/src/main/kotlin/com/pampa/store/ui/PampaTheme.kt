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

val PampaAccentPresets = listOf(
    AccentPreset("nebula76", "Nebula 76", Color(0xFF6255D9), Color(0xFF968BFF)),
    AccentPreset("ionBlue", "Ion Blue", Color(0xFF2877D8), Color(0xFF69A7FF)),
    AccentPreset("auroraMint", "Aurora Mint", Color(0xFF158B73), Color(0xFF58D6B5)),
    AccentPreset("solarAmber", "Solar Amber", Color(0xFFB96B00), Color(0xFFFFB950)),
    AccentPreset("coralPulse", "Coral Pulse", Color(0xFFC34F68), Color(0xFFFF8299)),
)

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
