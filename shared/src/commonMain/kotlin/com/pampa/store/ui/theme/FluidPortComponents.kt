package com.pampa.store.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * I componenti del Fluid Engine che servono al Pampa Store, portati in `commonMain`.
 *
 * Vale quello che dice [FLUID_PORT_OF]: **questa e' una copia**, e non si aggiorna da sola. Ne sono
 * stati portati pochi apposta — quelli che si vedono e che l'app usa in ogni schermata. Ogni
 * componente in piu' qui e' un componente in piu' da riallineare a mano quando l'engine si muove.
 */

/**
 * Il ruolo di una riga, espresso come colore.
 *
 * Il tono sta sulla piastrella dell'icona e non sullo sfondo della riga: colorare le righe una per
 * una trasforma un gruppo ordinato in un mosaico di blocchi, e l'informazione si perde proprio
 * mentre grida.
 */
enum class FluidTone {
    Primary,
    Success,
    Warning,
    Danger,
    Info,
    Neutral,
}

private data class ToneColors(val container: Color, val content: Color)

@Composable
private fun toneColors(tone: FluidTone): ToneColors {
    // Letto dallo sfondo e non dal tema di sistema: l'app decide il proprio chiaro/scuro.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (tone) {
        FluidTone.Primary -> ToneColors(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        FluidTone.Success -> if (isDark) {
            ToneColors(Color(0xFF0F2D1D), Color(0xFF81C784))
        } else {
            ToneColors(Color(0xFFE8F5E9), Color(0xFF1B5E20))
        }
        FluidTone.Warning -> if (isDark) {
            ToneColors(Color(0xFF332000), Color(0xFFFFB74D))
        } else {
            ToneColors(Color(0xFFFFF3E0), Color(0xFFE65100))
        }
        FluidTone.Danger -> ToneColors(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        FluidTone.Info -> ToneColors(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        FluidTone.Neutral -> ToneColors(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Una lista raggruppata: le righe condividono un solo contenitore arrotondato.
 *
 * Il contenitore ritaglia, cosi' l'evidenziazione della pressione di una riga viene tagliata sugli
 * angoli del gruppo invece di disegnare una toppa quadrata sopra di essi.
 */
@Composable
fun FluidListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = ContinuousCornerShape(FluidRadius.Group)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** Il filo fra due righe, rientrato fino a dove comincia il testo. */
@Composable
fun FluidListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    )
}

/**
 * Il riquadro pieno in cui sta l'icona in testa a una riga.
 *
 * E' qui che vive il tono, e non sullo sfondo della riga: e' quello che tiene un gruppo somigliante
 * a un oggetto solo.
 */
@Composable
fun FluidIconTile(
    tone: FluidTone = FluidTone.Neutral,
    content: @Composable () -> Unit,
) {
    val colors = toneColors(tone)
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(colors.content.copy(alpha = 0.16f), ContinuousCornerShape(FluidRadius.Small)),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Box(modifier = Modifier.size(19.dp)) { content() }
        }
    }
}

/**
 * Una riga di una lista raggruppata.
 *
 * Sotto pressione si **tinge** invece di rimpicciolirsi: restringere una riga di una pila rompe la
 * sagoma del gruppo, ed e' quello che faceva sembrare inquieto il trattamento precedente.
 */
@Composable
fun FluidListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    tone: FluidTone = FluidTone.Neutral,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = toneColors(tone)
    Column(modifier = modifier.fillMaxWidth().fluidRowPressable(onClick = onClick)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            overlineContent = eyebrow?.let {
                {
                    Text(
                        text = it,
                        color = colors.content,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = leading?.let { { FluidIconTile(tone = tone, content = it) } },
            trailingContent = if (trailing != null || onClick != null) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        trailing?.invoke()
                        if (onClick != null) {
                            // Il gallone e' l'unica cosa che distingue una riga da un'etichetta
                            // senza dover ricorrere a colore, peso o un bordo.
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            } else {
                null
            },
        )
    }
}

/**
 * L'intestazione di una sezione, in maiuscolo.
 *
 * Il maiuscolo vuole molto piu' tracking di quanto la curva dia da sola, perche' le lettere non
 * hanno piu' ascendenti e discendenti a separarle.
 */
@Composable
fun FluidSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * L'evidenziazione della pressione per una riga.
 *
 * Compare all'istante e svanisce con calma: e' quello che impedisce a un tocco rapido di sembrare
 * uno sfarfallio, perche' l'evidenziazione resta visibile almeno per la durata della dissolvenza.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.fluidRowPressable(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val highlight by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) spring(stiffness = 3000f) else tween(260),
        label = "fluid row highlight",
    )
    return this
        .drawBehind {
            if (highlight > 0.001f) {
                drawRect(highlightColor.copy(alpha = highlightColor.alpha * highlight))
            }
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}
