package com.pampa.store.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// ── Brand Colors ──
// Colori di stato, non di tema: "installato", "attenzione", "errore" non hanno uno slot in
// Material, e un ruolo semantico senza slot e' l'unico caso in cui un colore scritto qui e' la
// risposta giusta. Tutto il resto viene da MaterialTheme.colorScheme, derivato da PampaStoreBrand.
val AccentPink = Color(0xFFD45872)
val AccentOrange = Color(0xFFFFA23E)
val AccentGreen = Color(0xFF82C662)

// ── Category Colors ──
val CategoryColors = mapOf(
    "AI" to Color(0xFF3A8DCA),
    "Games" to Color(0xFFD75D68),
    "Tools" to Color(0xFF4F9D69),
    "Media" to Color(0xFFE08C31),
    "Social" to Color(0xFF2E9D92),
    "General" to Color(0xFF68766E)
)

fun getCategoryColor(category: String): Color {
    return CategoryColors[category] ?: CategoryColors["General"]!!
}

// ── Typography ──
internal data class BrandFontFamilies(
    val display: FontFamily,
    val sans: FontFamily
)

@Composable
internal expect fun rememberBrandFontFamilies(): BrandFontFamilies

/**
 * La scala tipografica iOS, sui caratteri del Pampa Store.
 *
 * Cambia la **scala**, non la voce: DM Serif Display e Space Grotesk restano, perche' sono
 * l'identita' dello store. Quello che arriva dal Fluid Engine e' la rampa — 34 / 28 / 22 / 20 / 17 /
 * 15 / 13 / 11 al posto della 57 / 45 / 36 / 32 di Material, che su un telefono e' enorme — e la
 * curva di tracking dipendente dalla dimensione: negativa nei titoli, neutra intorno ai 13, positiva
 * sotto. Un carattere impostato a tracking zero si legge slargato nei titoli e stretto nelle
 * didascalie, ed e' meta' del motivo per cui un'app "sembra un esempio di Material".
 */
private fun tracking(sizeSp: Float): TextUnit {
    val anchors = listOf(
        11f to 0.30f,
        13f to 0.00f,
        15f to -0.15f,
        17f to -0.25f,
        20f to -0.35f,
        24f to -0.45f,
        28f to -0.55f,
        34f to -0.70f,
    )
    if (sizeSp <= anchors.first().first) return anchors.first().second.sp
    if (sizeSp >= anchors.last().first) return anchors.last().second.sp
    for (index in 0 until anchors.lastIndex) {
        val (lowSize, lowTrack) = anchors[index]
        val (highSize, highTrack) = anchors[index + 1]
        if (sizeSp in lowSize..highSize) {
            val ratio = (sizeSp - lowSize) / (highSize - lowSize)
            return (lowTrack + (highTrack - lowTrack) * ratio).sp
        }
    }
    return 0f.sp
}

/**
 * Compose centra la prima riga dentro la sua line box solo se glielo si chiede.
 *
 * Senza, una riga da 17sp dentro un'interlinea da 22sp si porta tutti e 5 gli sp di spazio sopra, e
 * un titolo dentro una riga di altezza fissa si legge visivamente basso.
 */
private val FluidLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun fluidStyle(
    family: FontFamily,
    sizeSp: Float,
    lineHeightSp: Float,
    weight: FontWeight,
    letterSpacing: TextUnit = tracking(sizeSp),
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing,
    lineHeightStyle = FluidLineHeightStyle,
)

@Composable
private fun brandTypography(): Typography {
    val brandFonts = rememberBrandFontFamilies()
    val display = brandFonts.display
    val sans = brandFonts.sans

    return Typography(
        // Titoli grandi. Il serif resta dov'era: in cima, dove lo store si presenta.
        displayLarge = fluidStyle(display, 34f, 41f, FontWeight.Bold),
        displayMedium = fluidStyle(display, 34f, 41f, FontWeight.Bold),
        displaySmall = fluidStyle(display, 30f, 36f, FontWeight.Bold),

        headlineLarge = fluidStyle(display, 28f, 34f, FontWeight.Bold),
        headlineMedium = fluidStyle(display, 26f, 32f, FontWeight.Bold),
        headlineSmall = fluidStyle(sans, 22f, 28f, FontWeight.Bold),

        // Titoli. 17sp Semibold e' quello che fa il lavoro: la riga principale di ogni voce.
        titleLarge = fluidStyle(sans, 20f, 25f, FontWeight.SemiBold),
        titleMedium = fluidStyle(sans, 17f, 22f, FontWeight.SemiBold),
        titleSmall = fluidStyle(sans, 15f, 20f, FontWeight.SemiBold),

        // Corpo. 15sp Regular e' la riga secondaria sotto un titolo.
        bodyLarge = fluidStyle(sans, 17f, 23f, FontWeight.Normal),
        bodyMedium = fluidStyle(sans, 15f, 21f, FontWeight.Normal),
        bodySmall = fluidStyle(sans, 13f, 18f, FontWeight.Normal),

        // Etichette. Alle misure piccole il tracking torna positivo per restare leggibili.
        labelLarge = fluidStyle(sans, 15f, 20f, FontWeight.SemiBold),
        labelMedium = fluidStyle(sans, 13f, 17f, FontWeight.Medium),
        labelSmall = fluidStyle(sans, 11f, 14f, FontWeight.SemiBold, letterSpacing = 0.5.sp),
    )
}

/**
 * Le forme, tutte ad angolo continuo.
 *
 * I raggi sono piu' grandi dei precedenti perche' un angolo continuo dello stesso raggio nominale si
 * legge piu' stretto: la curva resta piu' a lungo vicino al lato.
 */
private val AppShapes = Shapes(
    extraSmall = ContinuousCornerShape(FluidRadius.Small),
    small = ContinuousCornerShape(FluidRadius.Control),
    medium = ContinuousCornerShape(FluidRadius.Card),
    large = ContinuousCornerShape(FluidRadius.Group),
    extraLarge = ContinuousCornerShape(FluidRadius.Sheet),
)

/**
 * Il tema del Pampa Store, costruito sul look del Fluid Engine.
 *
 * La firma resta identica di proposito: l'unico punto di chiamata non sa che sotto e' cambiato
 * tutto. Al posto delle due palette scritte a mano c'e' [fluidColorScheme], che deriva ogni
 * superficie da [PampaStoreBrand]: cambiare quella coppia di colori ricolora l'app intera restando
 * coerente, che e' esattamente quello che le due liste di 30 colori non permettevano.
 *
 * Vedi [FLUID_PORT_OF] per il rapporto con l'engine vero.
 */
@Composable
fun PampaStoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = fluidColorScheme(brand = PampaStoreBrand, isDark = darkTheme),
        typography = brandTypography(),
        shapes = AppShapes,
        content = content,
    )
}
