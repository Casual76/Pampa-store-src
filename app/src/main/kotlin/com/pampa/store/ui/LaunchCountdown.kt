package com.pampa.store.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pampa.store.platform.MailDraft
import dev.antigravity.fluidengine.ui.fluid.FluidHero
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.delay

/**
 * Tutto quello che lo Store sa di Fünkazzenland, compilato nell'app.
 *
 * Il gioco non e' nel catalogo: esce su invito, quindi non c'e' niente da leggere dalla rete e il
 * countdown funziona anche offline. Si tocca qui, e solo qui, se la data o il contatto cambiano.
 */
object FunkazzenlandLaunch {
    const val TITLE = "Fünkazzenland"

    /** L'uscita, 10 ottobre 2026 alle 17:00 a Roma (CEST). L'offset e' nella stringa: il fuso del telefono non conta. */
    val RELEASE_AT: Instant = OffsetDateTime.parse("2026-10-10T17:00:00+02:00").toInstant()

    /** Per quanti giorni dopo l'uscita la card resta in Home prima di sparire. */
    const val VISIBLE_DAYS_AFTER_RELEASE = 14L

    val INVITE_MAIL = MailDraft(
        to = "pampalonialessio2008@gmail.com",
        subject = "Invito Fünkazzenland",
        body = "Ciao Alessio,\n\n" +
            "vorrei un invito per Fünkazzenland.\n" +
            "Lo giocherei su: PC / Android (cancella quello che non serve)\n\n" +
            "Sono: ",
    )
}

/** Dove siamo rispetto all'uscita. */
enum class LaunchPhase { Before, After, Hidden }

/** Il tempo che manca, scomposto. Mai negativo. */
data class CountdownParts(
    val days: Long,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
) {
    val isZero: Boolean get() = days == 0L && hours == 0 && minutes == 0 && seconds == 0
}

/**
 * Giorni, ore, minuti e secondi da [now] a [target].
 *
 * I secondi si arrotondano per eccesso: finche' non e' scaduto davvero non si vede "00:00:00", e a
 * un secondo dalla scadenza si legge 00:00:01. Dopo la scadenza tutto resta a zero, non va in
 * negativo. Lavora su [Instant], quindi non dipende dal fuso orario del telefono.
 */
fun countdownParts(now: Instant, target: Instant = FunkazzenlandLaunch.RELEASE_AT): CountdownParts {
    val remainingMillis = Duration.between(now, target).toMillis()
    if (remainingMillis <= 0L) return CountdownParts(0, 0, 0, 0)
    val total = (remainingMillis + 999L) / 1000L
    return CountdownParts(
        days = total / 86_400L,
        hours = ((total % 86_400L) / 3_600L).toInt(),
        minutes = ((total % 3_600L) / 60L).toInt(),
        seconds = (total % 60L).toInt(),
    )
}

/** Prima, dopo, oppure sparita: la card esce di scena [FunkazzenlandLaunch.VISIBLE_DAYS_AFTER_RELEASE] giorni dopo l'uscita. */
fun launchPhase(
    now: Instant,
    target: Instant = FunkazzenlandLaunch.RELEASE_AT,
    visibleDaysAfter: Long = FunkazzenlandLaunch.VISIBLE_DAYS_AFTER_RELEASE,
): LaunchPhase = when {
    now < target -> LaunchPhase.Before
    now < target.plus(Duration.ofDays(visibleDaysAfter)) -> LaunchPhase.After
    else -> LaunchPhase.Hidden
}

/** "1g 04:12:09", o "04:12:09" sotto le 24 ore. Senza secondi se [withSeconds] e' falso. */
fun formatCountdown(parts: CountdownParts, withSeconds: Boolean = true): String {
    val clock = buildString {
        append(parts.hours.twoDigits()).append(':').append(parts.minutes.twoDigits())
        if (withSeconds) append(':').append(parts.seconds.twoDigits())
    }
    return if (parts.days > 0) "${parts.days}g $clock" else clock
}

/**
 * La frase per TalkBack. Arriva al minuto e non al secondo di proposito: si legge quando la
 * persona ci si ferma sopra, e non e' una regione live che parla ogni secondo.
 */
fun countdownDescription(parts: CountdownParts): String {
    val units = buildList {
        if (parts.days > 0) add(parts.days to if (parts.days == 1L) "giorno" else "giorni")
        if (parts.hours > 0) add(parts.hours.toLong() to if (parts.hours == 1) "ora" else "ore")
        if (parts.minutes > 0) add(parts.minutes.toLong() to if (parts.minutes == 1) "minuto" else "minuti")
    }
    if (units.isEmpty()) return "Manca meno di un minuto"
    val text = units.map { (n, label) -> "$n $label" }
    val joined = when (text.size) {
        1 -> text[0]
        else -> text.dropLast(1).joinToString(", ") + " e " + text.last()
    }
    val verb = if (units.size == 1 && units[0].first == 1L) "Manca" else "Mancano"
    return "$verb $joined"
}

/**
 * Fra quanti millisecondi serve ricalcolare il countdown.
 *
 * Prima dell'uscita si sveglia sul prossimo cambio di secondo (cosi' il tick non deriva), dopo
 * basta un controllo al minuto: l'unica cosa che puo' ancora cambiare e' la sparizione della card.
 */
fun launchTickDelayMillis(now: Instant, target: Instant = FunkazzenlandLaunch.RELEASE_AT): Long {
    val remaining = Duration.between(now, target).toMillis()
    return if (remaining <= 0L) 60_000L else ((remaining - 1L) % 1000L) + 1L
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/**
 * L'ora corrente, rilanciata mentre la schermata e' in primo piano.
 *
 * Rilegge l'orologio a ogni giro invece di sommare secondi: se il telefono dorme o l'ora di
 * sistema cambia, il valore si riallinea da solo. Fermo in background (`STARTED`), quindi non
 * consuma nulla quando lo Store non si vede.
 */
@Composable
private fun rememberLaunchNow(clock: () -> Instant): State<Instant> {
    val currentClock by rememberUpdatedState(clock)
    val now = remember { mutableStateOf(currentClock()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = currentClock()
                now.value = current
                delay(launchTickDelayMillis(current))
            }
        }
    }
    return now
}

/**
 * La card della Home che conta i secondi all'uscita di Fünkazzenland e rimanda a Pampa per
 * l'invito. Non c'e' un pulsante "scarica": il gioco non e' sullo Store, lo manda lui a pochi.
 *
 * [clock] e' iniettabile per i test e per gli screenshot dello stato "dopo la scadenza".
 */
@Composable
fun LaunchCountdownCard(
    onRequestInvite: (MailDraft) -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Instant = Instant::now,
) {
    val now by rememberLaunchNow(clock)
    val phase = launchPhase(now)
    if (phase == LaunchPhase.Hidden) return

    // Con le animazioni spente (scala 0) il valore non scorre piu': i secondi sarebbero solo un
    // numero che sfarfalla, quindi si mostrano i minuti e basta.
    val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
    val parts = countdownParts(now)
    val before = phase == LaunchPhase.Before
    val value = when {
        !before -> "È uscito"
        reducedMotion && parts.days == 0L && parts.hours == 0 && parts.minutes == 0 -> "Meno di un minuto"
        else -> formatCountdown(parts, withSeconds = !reducedMotion)
    }
    val spoken = if (before) {
        "${FunkazzenlandLaunch.TITLE}, accesso anticipato su invito. ${countdownDescription(parts)} all'uscita."
    } else {
        "${FunkazzenlandLaunch.TITLE} è uscito. Accesso anticipato su invito."
    }

    FluidHero(
        tone = FluidHeroTone.Tertiary,
        motif = FluidHeroMotif.Ticks,
        eyebrow = "Accesso anticipato · su invito",
        value = value,
        title = FunkazzenlandLaunch.TITLE,
        description = if (before) {
            "Il gioco della 5F per PC e Android. Non è sullo Store: lo mando io, a pochi."
        } else {
            "Il gioco della 5F per PC e Android è uscito, ma non è sullo Store: lo mando io, a pochi."
        },
        icon = Icons.Rounded.SportsEsports,
        actionLabel = if (before) "Scrivimi per un invito" else "Scrivimi per l’invito",
        onAction = { onRequestInvite(FunkazzenlandLaunch.INVITE_MAIL) },
        modifier = modifier
            .testTag("home-launch-countdown")
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    )
}
