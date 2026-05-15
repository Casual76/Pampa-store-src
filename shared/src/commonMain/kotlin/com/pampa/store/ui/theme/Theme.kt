package com.pampa.store.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Brand Colors ──
val PrimaryViolet = Color(0xFF2F5F7A)
val PrimaryVioletLight = Color(0xFFC7E7F4)
val PrimaryVioletDark = Color(0xFF0E344C)

val SecondaryTeal = Color(0xFF2D7B68)
val SecondaryTealDark = Color(0xFF184E43)

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

// ── Dark Color Scheme ──
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8ECAE6),
    onPrimary = Color(0xFF003549),
    primaryContainer = PrimaryVioletDark,
    onPrimaryContainer = PrimaryVioletLight,

    secondary = Color(0xFF7ED4BC),
    onSecondary = Color(0xFF003A2D),
    secondaryContainer = SecondaryTealDark,
    onSecondaryContainer = Color(0xFFBFF5E2),

    tertiary = AccentOrange,
    onTertiary = Color(0xFF2A1700),
    tertiaryContainer = Color(0xFF5C3A00),
    onTertiaryContainer = Color(0xFFFFDDB3),

    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE1E3E8),

    surface = Color(0xFF151A20),
    onSurface = Color(0xFFE1E3E8),
    surfaceVariant = Color(0xFF1E252D),
    onSurfaceVariant = Color(0xFFC1C8D0),

    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = Color(0xFF121820),
    surfaceContainer = Color(0xFF1A2028),
    surfaceContainerHigh = Color(0xFF222830),
    surfaceContainerHighest = Color(0xFF2A3038),

    outline = Color(0xFF3A4450),
    outlineVariant = Color(0xFF2A3340),

    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF401A1A),
    onErrorContainer = Color(0xFFFFDAD6),

    inverseSurface = Color(0xFFE1E3E8),
    inverseOnSurface = Color(0xFF1A1E24),
    inversePrimary = PrimaryViolet
)

// ── Light Color Scheme ──
private val LightColorScheme = lightColorScheme(
    primary = PrimaryViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECF6),
    onPrimaryContainer = PrimaryVioletDark,

    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F0E4),
    onSecondaryContainer = SecondaryTealDark,

    tertiary = Color(0xFFD48820),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF3E2500),

    background = Color(0xFFF8FAFB),
    onBackground = Color(0xFF191C1E),

    surface = Color(0xFFFCFCFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFF0F3F5),
    onSurfaceVariant = Color(0xFF44474E),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F7F8),
    surfaceContainer = Color(0xFFEFF1F3),
    surfaceContainerHigh = Color(0xFFE8EBED),
    surfaceContainerHighest = Color(0xFFE2E5E7),

    outline = Color(0xFFD0D3D8),
    outlineVariant = Color(0xFFE0E3E8),

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFF0F0F3),
    inversePrimary = Color(0xFF8ECAE6)
)

// ── Shapes ──
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ── Typography ──
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
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = displayBrand,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        labelLarge = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = sansBrand,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
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
        shapes = AppShapes,
        content = content
    )
}
