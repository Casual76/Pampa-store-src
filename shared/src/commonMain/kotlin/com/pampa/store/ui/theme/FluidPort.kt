package com.pampa.store.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Il look del Fluid Engine, portato a mano dentro `commonMain`.
 *
 * **Questo file e' una copia, e va trattato come tale.** Il Pampa Store e' Compose Multiplatform
 * (`org.jetbrains.compose`), i moduli dell'engine sono librerie Android: non possono entrare in un
 * source set condiviso col desktop. L'alternativa a questa copia non era "dipendere dall'engine",
 * era "restare l'unica app della famiglia con un aspetto diverso".
 *
 * Quello che si porta dietro una copia e' che non si aggiorna da sola. [FLUID_PORT_OF] dice da
 * quale versione dell'engine viene: quando l'engine va avanti, questo numero resta indietro, ed e'
 * li' che si vede. Chi tocca il design system dell'engine dovrebbe passare di qui.
 *
 * Non e' tutto l'engine: sono i tre pezzi che si vedono — la palette derivata da un solo accento,
 * gli angoli continui, la scala tipografica. I componenti (`FluidListRow`, `FluidScreen`, il motion
 * scheme) sono rimasti fuori: il motion scheme espressivo non esiste nel Material 3 di Compose
 * Multiplatform 1.7, e i componenti sarebbero una copia molto piu' grande e molto piu' fragile.
 */
const val FLUID_PORT_OF: String = "1.2.3"

// ─────────────────────────────────────────────────────────────────────────────
// Angoli continui
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Un rettangolo arrotondato con angoli *continui* invece che circolari.
 *
 * Un angolo circolare unisce un lato dritto a un arco, quindi la curvatura salta da zero a `1/r` in
 * un punto solo. L'occhio legge quella discontinuita' come una piega, ed e' gran parte del motivo
 * per cui una card Material e una card iOS sembrano diverse anche allo stesso raggio. Un angolo
 * continuo fa salire e riscendere la curvatura, e non lascia giunzioni — ma si allarga di piu' su
 * ogni lato, per questo va disegnato un po' piu' grande per leggersi della stessa misura.
 *
 * La costruzione e' quella documentata da Figma per il suo controllo di smoothing: una curva di
 * raccordo, un arco circolare accorciato, e la curva speculare. A [smoothing] `0` degenera in un
 * angolo circolare normale; iOS sta intorno a `0.6`.
 */
@Stable
class ContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val smoothing: Float = IosSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): ContinuousCornerShape =
        ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f || size.minDimension == 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }
        val rtl = layoutDirection == LayoutDirection.Rtl
        val topLeft = if (rtl) topEnd else topStart
        val topRight = if (rtl) topStart else topEnd
        val bottomRight = if (rtl) bottomStart else bottomEnd
        val bottomLeft = if (rtl) bottomEnd else bottomStart

        val path = Path()
        // In senso orario dall'angolo in alto a destra. A ogni angolo si passa la direzione da cui
        // lo si entra e quella lungo cui lo si lascia, cosi' un solo emettitore serve tutti e
        // quattro senza casi speciali.
        path.continuousCorner(Offset(size.width, 0f), Right, Down, topRight, size, smoothing, moveTo = true)
        path.continuousCorner(Offset(size.width, size.height), Down, Left, bottomRight, size, smoothing)
        path.continuousCorner(Offset(0f, size.height), Left, Up, bottomLeft, size, smoothing)
        path.continuousCorner(Offset(0f, 0f), Up, Right, topLeft, size, smoothing)
        path.close()
        return Outline.Generic(path)
    }

    override fun toString(): String =
        "ContinuousCornerShape($topStart, $topEnd, $bottomEnd, $bottomStart)"

    companion object {
        /** Quanto Apple spinge la rampa di curvatura. Lo slider di Figma lo chiama 60%. */
        const val IosSmoothing: Float = 0.6f
    }
}

fun ContinuousCornerShape(
    radius: Dp,
    smoothing: Float = ContinuousCornerShape.IosSmoothing,
): ContinuousCornerShape = ContinuousCornerShape(
    CornerSize(radius),
    CornerSize(radius),
    CornerSize(radius),
    CornerSize(radius),
    smoothing,
)

fun ContinuousCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
    smoothing: Float = ContinuousCornerShape.IosSmoothing,
): ContinuousCornerShape = ContinuousCornerShape(
    CornerSize(topStart),
    CornerSize(topEnd),
    CornerSize(bottomEnd),
    CornerSize(bottomStart),
    smoothing,
)

/**
 * Una capsula: il raggio e' sempre meta' del lato corto.
 *
 * Switch, controlli segmentati e pill usano questa. iOS non da' mai loro un raggio fisso, e un
 * raggio fisso si vede appena il controllo cambia altezza.
 */
val FluidCapsuleShape: Shape =
    ContinuousCornerShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50))

private val Right = Offset(1f, 0f)
private val Down = Offset(0f, 1f)
private val Left = Offset(-1f, 0f)
private val Up = Offset(0f, -1f)

private fun Path.continuousCorner(
    corner: Offset,
    incoming: Offset,
    outgoing: Offset,
    requestedRadius: Float,
    size: Size,
    smoothing: Float,
    moveTo: Boolean = false,
) {
    val s = smoothing.coerceIn(0f, 1f)
    // L'angolo consuma (1 + s) * r di ogni lato, quindi il raggio deve cedere prima che due angoli
    // adiacenti si sovrappongano: senza, una pill corta si ripiega su se stessa.
    val budget = size.minDimension / 2f
    val radius = min(requestedRadius, budget / (1f + s)).coerceAtLeast(0f)

    val start = corner - incoming * ((1f + s) * radius)
    if (moveTo) moveTo(start.x, start.y) else lineTo(start.x, start.y)
    if (radius <= 0f) return

    val p = (1f + s) * radius
    val arcSweepDeg = 90f * (1f - s)
    val arcChord = sin(arcSweepDeg / 2f * DegToRad) * radius * Sqrt2
    val alphaDeg = (90f - arcSweepDeg) / 2f
    val betaDeg = 45f * s
    val handle = radius * tan(betaDeg / 2f * DegToRad)
    val c = handle * cos(alphaDeg * DegToRad)
    val d = c * tan(alphaDeg * DegToRad)
    val b = (p - arcChord - c - d) / 3f
    val a = 2f * b

    fun local(alongIncoming: Float, alongOutgoing: Float): Offset =
        start + incoming * alongIncoming + outgoing * alongOutgoing

    // Curvatura che sale, allontanandosi dal lato dritto.
    val arcStart = local(a + b + c, d)
    cubic(local(a, 0f), local(a + b, 0f), arcStart)

    // Il tratto circolare, come una sola cubica: esatta a meno di una frazione di pixel sotto i 90 gradi.
    val arcEnd = arcStart + incoming * arcChord + outgoing * arcChord
    val tangentAt = { deg: Float -> incoming * cos(deg * DegToRad) + outgoing * sin(deg * DegToRad) }
    val k = 4f / 3f * tan(arcSweepDeg / 4f * DegToRad) * radius
    cubic(
        arcStart + tangentAt(alphaDeg) * k,
        arcEnd - tangentAt(alphaDeg + arcSweepDeg) * k,
        arcEnd,
    )

    // …e che riscende nel lato dritto successivo. Lo specchio della prima curva: entrambi i punti di
    // controllo stanno sulla retta del lato uscente, cosi' l'angolo se ne va con curvatura zero.
    cubic(
        local(p, d + arcChord + c),
        local(p, d + arcChord + b + c),
        local(p, p),
    )
}

private fun Path.cubic(c1: Offset, c2: Offset, end: Offset) {
    cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
}

private val Sqrt2 = sqrt(2f)
private const val DegToRad = (PI / 180.0).toFloat()

/**
 * I raggi.
 *
 * Sono piu' grandi degli equivalenti Material apposta: un angolo continuo dello stesso raggio
 * nominale si legge visivamente piu' stretto, perche' la curva resta piu' a lungo vicino al lato.
 */
object FluidRadius {
    val Small: Dp = 10.dp
    val Control: Dp = 12.dp
    val Card: Dp = 18.dp
    val Group: Dp = 22.dp
    val Sheet: Dp = 38.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// Palette derivata da un solo accento
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Il seme di una palette.
 *
 * Due colori e non uno: lo stesso RGB su fondo chiaro e su fondo scuro non mantiene ne' il carattere
 * ne' il contrasto. Da questa coppia si deriva tutto il resto, quindi cambiare qui cambia l'app
 * intera in modo coerente.
 */
@Immutable
data class AccentPreset(
    val name: String,
    val label: String,
    val light: Color,
    val dark: Color,
) {
    fun resolve(isDark: Boolean): Color = if (isDark) dark else light
}

/** L'azzurro profondo del Pampa Store, nelle due versioni che gli servono. */
val PampaStoreBrand: AccentPreset = AccentPreset(
    name = "pampa-store",
    label = "Pampa Store",
    light = Color(0xFF2F5F7A),
    dark = Color(0xFF8ECAE6),
)

@Immutable
private data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

@Immutable
private data class SurfacePalette(
    val background: Color,
    val surface: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

/**
 * L'intera palette, da un accento solo.
 *
 * Le superfici grandi restano sobrie; i contenitori piu' piccoli e piu' alti ereditano una traccia
 * via via piu' forte della tinta. E' quello che fa sembrare l'app tinta invece che colorata.
 */
fun fluidColorScheme(
    brand: AccentPreset = PampaStoreBrand,
    isDark: Boolean,
): ColorScheme {
    val accent = brand.resolve(isDark)
    val surfaces = surfacePalette(accent = accent, isDark = isDark)
    val roles = fixedAccentRoles(accent, surfaces, isDark)
    return buildColorScheme(roles = roles, surfaces = surfaces, isDark = isDark)
}

private fun fixedAccentRoles(
    accent: Color,
    surfaces: SurfacePalette,
    isDark: Boolean,
): AccentRoles {
    // Una palette fissa ha comunque bisogno di piu' di una nota. Queste miscele sobrie tengono
    // l'accento riconoscibile dando alle superfici secondarie una loro identita' imparentata.
    val secondary = lerp(accent, if (isDark) Color(0xFF5E5CE6) else Color(0xFF5856D6), 0.26f)
    val tertiary = lerp(accent, if (isDark) Color(0xFFFF9F0A) else Color(0xFFFF9500), 0.22f)
    val primaryContainer = lerp(surfaces.low, accent, if (isDark) 0.28f else 0.17f)
    val secondaryContainer = lerp(surfaces.container, secondary, if (isDark) 0.22f else 0.14f)
    val tertiaryContainer = lerp(surfaces.high, tertiary, if (isDark) 0.18f else 0.11f)
    return AccentRoles(
        primary = accent,
        onPrimary = highestContrastContent(accent),
        primaryContainer = primaryContainer,
        onPrimaryContainer = highestContrastContent(primaryContainer),
        secondary = secondary,
        onSecondary = highestContrastContent(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = highestContrastContent(secondaryContainer),
        tertiary = tertiary,
        onTertiary = highestContrastContent(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = highestContrastContent(tertiaryContainer),
    )
}

private fun surfacePalette(accent: Color, isDark: Boolean): SurfacePalette {
    val neutralBackground = if (isDark) Color(0xFF0D0D0F) else Color(0xFFF2F2F7)
    val neutralSurface = if (isDark) Color(0xFF1B1B1E) else Color.White
    val neutralLowest = if (isDark) Color(0xFF101012) else Color.White
    val neutralLow = if (isDark) Color(0xFF1D1D20) else Color(0xFFFCFCFE)
    val neutralContainer = if (isDark) Color(0xFF202023) else Color(0xFFF9F9FC)
    val neutralHigh = if (isDark) Color(0xFF252528) else Color(0xFFF3F3F7)
    val neutralHighest = if (isDark) Color(0xFF2C2C2F) else Color(0xFFEBEBF0)

    return SurfacePalette(
        background = lerp(neutralBackground, accent, 0.012f),
        surface = lerp(neutralSurface, accent, 0.014f),
        lowest = lerp(neutralLowest, accent, 0.008f),
        low = lerp(neutralLow, accent, 0.024f),
        container = lerp(neutralContainer, accent, 0.038f),
        high = lerp(neutralHigh, accent, 0.055f),
        highest = lerp(neutralHighest, accent, 0.075f),
        onSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF121214),
        onSurfaceVariant = if (isDark) Color(0xFFAAAAB2) else Color(0xFF68686E),
        outline = if (isDark) Color(0xFF48484D) else Color(0xFFB8B8BF),
        outlineVariant = if (isDark) Color(0xFF323236) else Color(0xFFD8D8DE),
    )
}

private fun buildColorScheme(
    roles: AccentRoles,
    surfaces: SurfacePalette,
    isDark: Boolean,
): ColorScheme {
    val error = if (isDark) Color(0xFFFF6961) else Color(0xFFD70015)
    val errorContainer = lerp(surfaces.container, error, if (isDark) 0.24f else 0.14f)
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
        inversePrimary = roles.primary,
        secondary = roles.secondary,
        onSecondary = roles.onSecondary,
        secondaryContainer = roles.secondaryContainer,
        onSecondaryContainer = roles.onSecondaryContainer,
        tertiary = roles.tertiary,
        onTertiary = roles.onTertiary,
        tertiaryContainer = roles.tertiaryContainer,
        onTertiaryContainer = roles.onTertiaryContainer,
        background = surfaces.background,
        onBackground = surfaces.onSurface,
        surface = surfaces.surface,
        onSurface = surfaces.onSurface,
        surfaceVariant = surfaces.high,
        onSurfaceVariant = surfaces.onSurfaceVariant,
        surfaceTint = Color.Transparent,
        surfaceBright = if (isDark) surfaces.highest else surfaces.surface,
        surfaceDim = surfaces.background,
        surfaceContainerLowest = surfaces.lowest,
        surfaceContainerLow = surfaces.low,
        surfaceContainer = surfaces.container,
        surfaceContainerHigh = surfaces.high,
        surfaceContainerHighest = surfaces.highest,
        inverseSurface = if (isDark) Color(0xFFF2F2F5) else Color(0xFF1C1C1E),
        inverseOnSurface = if (isDark) Color(0xFF121214) else Color(0xFFF2F2F7),
        error = error,
        onError = highestContrastContent(error),
        errorContainer = errorContainer,
        onErrorContainer = highestContrastContent(errorContainer),
        outline = surfaces.outline,
        outlineVariant = surfaces.outlineVariant,
        scrim = Color.Black,
    )
}

/**
 * Il piu' leggibile fra il quasi-nero e il quasi-bianco su [background].
 *
 * Scelto per rapporto di contrasto invece che per "e' chiaro o scuro": la seconda regola sbaglia
 * proprio sui colori medi, che sono quelli su cui il testo si perde.
 */
internal fun highestContrastContent(background: Color): Color {
    val dark = Color(0xFF121214)
    val light = Color(0xFFFDFDFF)
    fun ratio(foreground: Color): Float {
        val high = maxOf(foreground.luminance(), background.luminance())
        val low = minOf(foreground.luminance(), background.luminance())
        return (high + 0.05f) / (low + 0.05f)
    }
    return if (ratio(dark) >= ratio(light)) dark else light
}
