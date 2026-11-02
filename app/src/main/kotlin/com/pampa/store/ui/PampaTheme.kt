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
import dev.antigravity.fluidengine.ui.fluid.FluidAppearance
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

/** Il colore dell'app: Nebula 76, il primo preset. È il «marchio» del selettore dell'engine. */
val PampaBrand: AccentPreset = PampaAccentPresets.first()

/** I preset che il selettore offre accanto al marchio: gli altri sei, perché il marchio *è* Nebula 76. */
val PampaSelectablePresets: List<AccentPreset> = PampaAccentPresets.drop(1)

@Composable
fun PampaTheme(
    preferences: StorePreferences,
    content: @Composable () -> Unit,
) {
    FluidTheme(
        settings = preferences.toEngineSettings(),
        brand = PampaBrand,
        presets = PampaAccentPresets,
        content = content,
    )
}

fun StorePreferences.selectedAccentPreset(): AccentPresetId =
    AccentPresetId.entries.firstOrNull { it.storageName == accentPreset } ?: AccentPresetId.NEBULA_76

/**
 * Le preferenze dello Store nei termini dell'engine: sono quelle che il tema legge e che la sezione
 * «Aspetto» (`fluidAppearanceSection`) mostra e riscrive.
 *
 * Quello che lo Store salva non cambia — tema, origine dell'accento, preset, e dalla 1.9.0 il nero
 * assoluto e l'aptica — e resta com'era sul disco: questo è solo il ponte. Nebula 76, il preset di
 * serie, è il marchio dell'app e non una voce a parte (`AccentMode.BRAND`), come il selettore lo
 * mostra: un pallino solo, non due dello stesso colore.
 */
internal fun StorePreferences.toEngineSettings(): EngineSettings {
    val mode = when (enumOrDefault(themeMode, AppThemeMode.SYSTEM)) {
        AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
        AppThemeMode.LIGHT -> ThemeMode.LIGHT
        AppThemeMode.DARK -> ThemeMode.DARK
    }
    val source = enumOrDefault(accentSource, AccentSource.PRESET)
    val preset = selectedAccentPreset()
    return EngineSettings(
        themeMode = mode,
        accentMode = when {
            source == AccentSource.DYNAMIC -> AccentMode.DYNAMIC
            preset == AccentPresetId.NEBULA_76 -> AccentMode.BRAND
            else -> AccentMode.CUSTOM_PRESET
        },
        customAccentName = preset.storageName,
        dynamicColorEnabled = source == AccentSource.DYNAMIC,
        amoledEnabled = pureBlack,
        hapticsEnabled = hapticsEnabled,
    )
}

/**
 * Le preferenze dopo che la sezione «Aspetto» ha prodotto le [next] impostazioni dell'engine.
 *
 * L'inverso di [toEngineSettings]. Il preset ricordato non si perde passando ai colori di sistema
 * (si torna al preset di prima, come faceva il vecchio selettore), e `AMOLED` — che la sezione non
 * produce, ma un'altra strada potrebbe — si legge come «Scuro» più il nero.
 */
internal fun StorePreferences.withEngineSettings(next: EngineSettings): StorePreferences {
    val mode = when (FluidAppearance.themeChoice(next)) {
        ThemeMode.LIGHT -> AppThemeMode.LIGHT
        ThemeMode.DARK, ThemeMode.AMOLED -> AppThemeMode.DARK
        ThemeMode.SYSTEM -> AppThemeMode.SYSTEM
    }
    val chosen = when (next.accentMode) {
        AccentMode.DYNAMIC -> AccentSource.DYNAMIC to selectedAccentPreset()
        AccentMode.BRAND -> AccentSource.PRESET to AccentPresetId.NEBULA_76
        AccentMode.CUSTOM_PRESET -> AccentSource.PRESET to (
            AccentPresetId.entries.firstOrNull { it.storageName.equals(next.customAccentName, ignoreCase = true) }
                ?: AccentPresetId.NEBULA_76
            )
    }
    return copy(
        themeMode = mode.name,
        accentSource = chosen.first.name,
        accentPreset = chosen.second.storageName,
        pureBlack = FluidAppearance.isPureBlack(next),
        hapticsEnabled = next.hapticsEnabled,
    )
}
