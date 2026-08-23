package com.pampa.store.ui.theme

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Il bordo elastico: quando la lista finisce, il contenuto cede invece di fermarsi di colpo.
 *
 * Portato dal Fluid Engine. Il file originale non ha una sola dipendenza da Android — e' matematica
 * e `NestedScrollConnection` — quindi qui e' una copia fedele e non un'approssimazione. Vale
 * comunque [FLUID_PORT_OF]: se l'engine cambia la curva, questa non se ne accorge.
 */

/** Quanto del movimento del dito il contenuto segue ancora all'inizio dell'overscroll. */
private const val OverscrollResistance = 0.55f

/** Tetto del rimbalzo, come frazione della finestra. Tiene in schermo anche un lancio violento. */
private const val MaximumBounceFraction = 0.24f

/** Oltre questa velocita' un lancio non compra piu' corsa: e' "il piu' forte possibile". */
private const val MaximumBounceVelocityPxPerSecond = 5_500f

/** Molla criticamente smorzata con risposta ~0.38s: nessun sovraelongamento, nessuna pigrizia. */
private val SettleStiffness: Float = ((2.0 * PI / 0.38) * (2.0 * PI / 0.38)).toFloat()
private const val SettleDamping = 1f

/**
 * L'elastico di UIKit.
 *
 * `f(x) = x·d·c / (d + c·x)` — parte seguendo il dito al [resistance] della sua velocita' e decade
 * dolcemente verso un asintoto a [dimensionPx], quindi non esiste un punto in cui il contenuto si
 * ferma di colpo o salta. L'asintoto viene dalla finestra e non da un valore fisso in dp: e' quello
 * che fa sentire la cedevolezza proporzionata alla superficie che si sta trascinando.
 */
private fun rubberBand(rawDistancePx: Float, dimensionPx: Float): Float {
    val dimension = dimensionPx.coerceAtLeast(0f)
    val raw = rawDistancePx.coerceAtLeast(0f)
    val c = OverscrollResistance
    if (dimension <= 0f || raw <= 0f) return 0f
    return raw * dimension * c / (dimension + c * raw)
}

private fun inverseRubberBand(visualDistancePx: Float, dimensionPx: Float): Float {
    val dimension = dimensionPx.coerceAtLeast(0f)
    val c = OverscrollResistance
    val visual = visualDistancePx.coerceIn(0f, dimension)
    if (dimension <= 0f || visual <= 0f) return 0f
    if (visual >= dimension) return Float.MAX_VALUE
    return dimension * visual / (c * (dimension - visual))
}

private fun signedRubberBand(raw: Float, dimension: Float): Float =
    sign(raw) * rubberBand(abs(raw), dimension)

private fun signedInverseRubberBand(visual: Float, dimension: Float): Float =
    sign(visual) * inverseRubberBand(abs(visual), dimension)

/**
 * Lo stato del bordo elastico.
 *
 * [canScroll] non e' un dettaglio: una pagina piu' corta della sua finestra non ha una "fine"
 * contro cui rimbalzare, e non c'e' niente che la cedevolezza potrebbe star dicendo. Farla
 * rimbalzare comunque spinge tutta la pagina oltre la barra di stato.
 */
@Stable
class FluidOverscrollState(
    private val canScroll: () -> Boolean = { true },
) : NestedScrollConnection {

    private var dimensionPx = 0f
    private var rawDistancePx = 0f

    /** Positivo spinge il contenuto in basso (bordo alto), negativo in alto (bordo basso). */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    fun updateViewport(heightPx: Float) {
        dimensionPx = (heightPx * MaximumBounceFraction).coerceAtLeast(0f)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val offset = offsetPx
        if (offset == 0f || available.y == 0f) return Offset.Zero
        // Il ritorno a riposo avviene sempre prima che la lista si muova, cosi' il contenuto non
        // scorre mentre e' ancora spostato: il doppio movimento che fa sembrare lasco un bordo
        // elastico.
        val consumedY = when {
            offset > 0f && available.y < 0f -> max(available.y, -offset)
            offset < 0f && available.y > 0f -> min(available.y, -offset)
            else -> return Offset.Zero
        }
        applyOffset(offset + consumedY)
        return Offset(0f, consumedY)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (dimensionPx <= 0f) return Offset.Zero
        // Gli avanzi di un lancio sono ignorati qui apposta: arrivano come velocita' in
        // onPostFling, dove una molla sola esprime tutta l'escursione invece di accumularla
        // fotogramma per fotogramma.
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val leftover = available.y
        if (leftover == 0f) return Offset.Zero
        if (rawDistancePx == 0f && !canScroll()) return Offset.Zero
        rawDistancePx += leftover
        offsetPx = signedRubberBand(rawDistancePx, dimensionPx)
        return Offset(0f, leftover)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settleTo(0f, available.y)
        return Velocity(0f, available.y)
    }

    private fun applyOffset(value: Float) {
        val clamped = value.coerceIn(-dimensionPx, dimensionPx)
        offsetPx = clamped
        rawDistancePx = signedInverseRubberBand(clamped, dimensionPx)
    }

    private suspend fun settleTo(target: Float, velocityY: Float) {
        if (offsetPx == target && velocityY == 0f) return
        if (dimensionPx <= 0f) {
            offsetPx = target
            rawDistancePx = signedInverseRubberBand(target, dimensionPx)
            return
        }
        val ceiling = dimensionPx
        animate(
            initialValue = offsetPx,
            targetValue = target,
            initialVelocity = velocityY.coerceIn(
                -MaximumBounceVelocityPxPerSecond,
                MaximumBounceVelocityPxPerSecond,
            ),
            animationSpec = spring(
                dampingRatio = SettleDamping,
                stiffness = SettleStiffness,
                visibilityThreshold = 0.5f,
            ),
        ) { value, _ ->
            offsetPx = value.coerceIn(-ceiling, ceiling)
        }
        offsetPx = target
        rawDistancePx = signedInverseRubberBand(target, dimensionPx)
    }
}

@Composable
fun rememberFluidOverscroll(canScroll: () -> Boolean = { true }): FluidOverscrollState =
    remember { FluidOverscrollState(canScroll) }

/**
 * Va sul **contenitore** che riceve il gesto: intercetta lo scroll annidato e misura la finestra.
 *
 * Separato da [fluidOverscrollContent] perche' il contenitore deve restare fermo mentre il
 * contenuto si sposta — spostare anche lui vorrebbe dire spostare il gesto insieme al disegno.
 */
fun Modifier.fluidOverscroll(state: FluidOverscrollState): Modifier = this
    .nestedScroll(state)
    .onSizeChanged { state.updateViewport(it.height.toFloat()) }

/**
 * Va sul **contenuto** che deve cedere.
 *
 * `graphicsLayer` con una lambda e non `offset`: cosi' lo spostamento si legge in fase di disegno e
 * non ricompone ne' rimisura niente a ogni fotogramma del rimbalzo.
 */
fun Modifier.fluidOverscrollContent(state: FluidOverscrollState): Modifier =
    this.graphicsLayer { translationY = state.offsetPx }
