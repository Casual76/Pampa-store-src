package com.pampa.store.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PrimaryViolet = Color(0xFF27506D)
val PrimaryVioletLight = Color(0xFFB7D1DE)
val PrimaryVioletDark = Color(0xFF173246)

val SecondaryTeal = Color(0xFF5E8E72)
val SecondaryTealDark = Color(0xFF355245)

val AccentPink = Color(0xFFCC6B49)
val AccentOrange = Color(0xFFE0A458)
val AccentGreen = Color(0xFF7DB38A)

val SurfaceDark = Color(0xFF101514)
val SurfaceDarkElevated = Color(0xFF181F1D)
val SurfaceDarkCard = Color(0xFF202A27)
val SurfaceDarkInput = Color(0xFF2A3532)

val SurfaceLight = Color(0xFFF5EFE3)
val SurfaceLightCard = Color(0xFFFFFAF2)
val SurfaceLightElevated = Color(0xFFE8DED0)

val TextPrimary = Color(0xFFF5F0E6)
val TextSecondary = Color(0xFFAAB7AE)
val TextOnPrimary = Color(0xFFFFFFFF)

val TextPrimaryLight = Color(0xFF1D221E)
val TextSecondaryLight = Color(0xFF616A61)

val DividerDark = Color(0xFF40524C)
val DividerLight = Color(0xFFD7C9B7)
val GlassStrokeDark = Color(0x5F8BB0A3)
val GlassStrokeLight = Color(0x88E2D7C7)
val GlowBlue = Color(0xFF7FC4CE)
val GlowCoral = Color(0xFFE78A61)

val CategoryColors = mapOf(
    "AI" to Color(0xFF4E8FB6),
    "Games" to Color(0xFFD86D63),
    "Tools" to Color(0xFF6A9C7A),
    "Media" to Color(0xFFD89A55),
    "Social" to Color(0xFF5DB69E),
    "General" to Color(0xFF7C857C)
)

fun getCategoryColor(category: String): Color {
    return CategoryColors[category] ?: CategoryColors["General"]!!
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryViolet,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryVioletDark,
    onPrimaryContainer = PrimaryVioletLight,
    secondary = SecondaryTeal,
    onSecondary = Color.Black,
    secondaryContainer = SecondaryTealDark,
    tertiary = AccentPink,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceDarkElevated,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DividerDark,
    error = Color(0xFFFF6161),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryViolet,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryVioletLight,
    onPrimaryContainer = PrimaryVioletDark,
    secondary = SecondaryTealDark,
    onSecondary = Color.White,
    secondaryContainer = SecondaryTeal,
    tertiary = AccentPink,
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLightCard,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
    error = Color(0xFFD83838),
    onError = Color.White
)

internal data class BrandFontFamilies(
    val display: FontFamily,
    val sans: FontFamily
)

@Composable
internal expect fun rememberBrandFontFamilies(): BrandFontFamilies

@Composable
private fun brandTypography(): Typography {
    val brandFonts = rememberBrandFontFamilies()
    val displayBrand = brandFonts.display
    val sansBrand = brandFonts.sans

    return Typography(
        headlineLarge = TextStyle(
            fontFamily = displayBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 48.sp,
            lineHeight = 50.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = displayBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 38.sp,
            lineHeight = 42.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp
        ),
        titleLarge = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            lineHeight = 26.sp
        ),
        titleMedium = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 23.sp
        ),
        titleSmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 21.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp
        ),
        bodySmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp
        ),
        labelLarge = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp
        ),
        labelMedium = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelSmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    )
}

@Composable
fun PampaStoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = brandTypography(),
        content = content
    )
}
