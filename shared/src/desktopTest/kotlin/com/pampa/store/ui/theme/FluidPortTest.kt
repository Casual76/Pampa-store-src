package com.pampa.store.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Il porto del look Fluid, verificato dove si puo' rompere in silenzio.
 *
 * Una palette derivata sbaglia in un modo solo che conta davvero: testo che non si legge sul suo
 * sfondo. Non e' una cosa che si nota guardando due schermate, perche' capita a una coppia di
 * colori su venti, quindi la si controlla qui.
 */
class FluidPortTest {

    private val schemes = listOf(
        "chiaro" to fluidColorScheme(PampaStoreBrand, isDark = false),
        "scuro" to fluidColorScheme(PampaStoreBrand, isDark = true),
    )

    /** Il minimo WCAG per il testo grande e per gli elementi di interfaccia. */
    private val minimumContrast = 3.0f

    private fun contrast(foreground: Color, background: Color): Float {
        val high = maxOf(foreground.luminance(), background.luminance())
        val low = minOf(foreground.luminance(), background.luminance())
        return (high + 0.05f) / (low + 0.05f)
    }

    @Test
    fun `ogni coppia sfondo-contenuto resta leggibile`() {
        for ((name, scheme) in schemes) {
            val pairs = listOf(
                "primary" to (scheme.onPrimary to scheme.primary),
                "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "secondary" to (scheme.onSecondary to scheme.secondary),
                "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "tertiary" to (scheme.onTertiary to scheme.tertiary),
                "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "error" to (scheme.onError to scheme.error),
                "errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                "surface" to (scheme.onSurface to scheme.surface),
                "background" to (scheme.onBackground to scheme.background),
                "surfaceContainerHighest" to (scheme.onSurface to scheme.surfaceContainerHighest),
            )
            for ((role, colors) in pairs) {
                val (content, background) = colors
                val ratio = contrast(content, background)
                assertTrue(
                    ratio >= minimumContrast,
                    "tema $name, ruolo $role: contrasto $ratio, sotto il minimo $minimumContrast",
                )
            }
        }
    }

    /**
     * La scala di superfici deve salire in una direzione sola.
     *
     * Se una superficie "piu' alta" e' piu' scura di quella sotto, l'elevazione si legge al
     * contrario e le card sembrano buchi invece che rilievi.
     */
    @Test
    fun `la scala di superfici e' monotona`() {
        for ((name, scheme) in schemes) {
            val ladder = listOf(
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
            ).map { it.luminance() }
            val salgono = ladder.zipWithNext().all { (basso, alto) -> alto >= basso }
            val scendono = ladder.zipWithNext().all { (basso, alto) -> alto <= basso }
            assertTrue(salgono || scendono, "tema $name: la scala di superfici non e' monotona: $ladder")
        }
    }

    @Test
    fun `il tema scuro e' davvero scuro e quello chiaro davvero chiaro`() {
        val chiaro = fluidColorScheme(PampaStoreBrand, isDark = false)
        val scuro = fluidColorScheme(PampaStoreBrand, isDark = true)
        assertTrue(chiaro.background.luminance() > 0.5f, "il fondo chiaro non e' chiaro")
        assertTrue(scuro.background.luminance() < 0.2f, "il fondo scuro non e' scuro")
    }

    /**
     * L'unica cosa che il porto promette davvero: cambiare la coppia del marchio cambia tutto.
     *
     * Era il difetto delle due palette scritte a mano che c'erano prima — trenta colori da tenere
     * d'accordo a mano, e nessun modo di sapere se lo erano.
     */
    @Test
    fun `cambiare il marchio cambia anche le superfici, non solo l'accento`() {
        val originale = fluidColorScheme(PampaStoreBrand, isDark = false)
        val altro = fluidColorScheme(
            PampaStoreBrand.copy(light = Color(0xFFB3261E), dark = Color(0xFFFFB4AB)),
            isDark = false,
        )
        assertNotEquals(originale.primary, altro.primary)
        assertNotEquals(
            originale.surfaceContainerHighest,
            altro.surfaceContainerHighest,
            "le superfici non hanno seguito il marchio: la tinta non arriva fino in fondo",
        )
    }

    @Test
    fun `il contenuto piu' leggibile viene scelto per contrasto, non per chiarezza`() {
        // Un medio in cui "e' chiaro o scuro" sbaglia: la luminanza sta poco sopra la meta', ma il
        // nero ci contrasta molto meglio del bianco.
        assertEquals(Color(0xFF121214), highestContrastContent(Color(0xFFB8C4A0)))
        assertEquals(Color(0xFFFDFDFF), highestContrastContent(Color(0xFF2F5F7A)))
    }
}
