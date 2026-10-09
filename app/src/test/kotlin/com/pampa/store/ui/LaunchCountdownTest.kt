package com.pampa.store.ui

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchCountdownTest {
    private val target = FunkazzenlandLaunch.RELEASE_AT

    private fun at(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

    @Test
    fun releaseIsRomeThreeInTheAfternoon() {
        // 15:00 CEST == 13:00 UTC, comunque sia impostato il telefono.
        assertEquals(Instant.parse("2026-10-10T13:00:00Z"), target)
    }

    @Test
    fun beforeRelease_splitsDaysHoursMinutesSeconds() {
        // 1 giorno, 4 ore, 12 minuti e 9 secondi prima delle 15:00 di Roma del 10.
        val now = target.minus(Duration.ofSeconds(86_400L + 4 * 3_600L + 12 * 60L + 9L))
        val parts = countdownParts(now, target)
        assertEquals(CountdownParts(days = 1, hours = 4, minutes = 12, seconds = 9), parts)
        assertEquals("1g 04:12:09", formatCountdown(parts))
        assertEquals("1g 04:12", formatCountdown(parts, withSeconds = false))
    }

    @Test
    fun underOneDay_dropsTheDaysPart() {
        val parts = countdownParts(target.minusSeconds(4 * 3_600L + 12 * 60L + 9L), target)
        assertEquals("04:12:09", formatCountdown(parts))
    }

    @Test
    fun oneSecondBeforeRelease_isStillBefore_andReadsOneSecond() {
        val now = target.minusSeconds(1)
        assertEquals(CountdownParts(0, 0, 0, 1), countdownParts(now, target))
        assertEquals(LaunchPhase.Before, launchPhase(now, target))
        assertEquals("00:00:01", formatCountdown(countdownParts(now, target)))
    }

    @Test
    fun partOfASecondRoundsUp_neverShowsZeroWhileStillBefore() {
        val now = target.minusMillis(300)
        assertEquals(CountdownParts(0, 0, 0, 1), countdownParts(now, target))
        assertEquals(LaunchPhase.Before, launchPhase(now, target))
    }

    @Test
    fun atAndAfterRelease_partsAreZeroNeverNegative() {
        assertTrue(countdownParts(target, target).isZero)
        assertTrue(countdownParts(target.plusSeconds(1), target).isZero)
        assertTrue(countdownParts(target.plus(Duration.ofDays(400)), target).isZero)
        assertEquals("00:00:00", formatCountdown(countdownParts(target.plusSeconds(5), target)))
    }

    @Test
    fun phaseSwitchesToAfterExactlyAtRelease() {
        assertEquals(LaunchPhase.Before, launchPhase(target.minusMillis(1), target))
        assertEquals(LaunchPhase.After, launchPhase(target, target))
        assertEquals(LaunchPhase.After, launchPhase(target.plus(Duration.ofDays(13)), target))
    }

    @Test
    fun cardIsHiddenFourteenDaysAfterRelease() {
        val hideAt = target.plus(Duration.ofDays(14))
        assertEquals(LaunchPhase.After, launchPhase(hideAt.minusMillis(1), target))
        assertEquals(LaunchPhase.Hidden, launchPhase(hideAt, target))
        assertEquals(LaunchPhase.Hidden, launchPhase(hideAt.plus(Duration.ofDays(200)), target))
    }

    @Test
    fun differentTimeZonesGiveTheSameCountdown() {
        // Le 11:00 UTC si scrivono in modi diversi: devono contare 2 ore e basta.
        val instants = listOf(
            at("2026-10-10T11:00:00Z"),
            at("2026-10-10T13:00:00+02:00"), // Roma
            at("2026-10-10T06:00:00-05:00"), // Cinque ore dietro UTC
            at("2026-10-10T20:00:00+09:00"), // Tokyo
        )
        instants.forEach { now ->
            assertEquals(CountdownParts(0, 2, 0, 0), countdownParts(now, target))
            assertEquals(LaunchPhase.Before, launchPhase(now, target))
        }
        // E la scadenza e' la stessa per chi la legge da Tokyo: le 22:00 dello stesso giorno.
        assertEquals(LaunchPhase.After, launchPhase(at("2026-10-10T22:00:00+09:00"), target))
        assertEquals(LaunchPhase.Before, launchPhase(at("2026-10-10T21:59:59+09:00"), target))
    }

    @Test
    fun spokenDescription_usesCorrectItalianPlurals_andNoSeconds() {
        assertEquals(
            "Mancano 1 giorno, 4 ore e 12 minuti",
            countdownDescription(CountdownParts(1, 4, 12, 9)),
        )
        assertEquals("Manca 1 ora", countdownDescription(CountdownParts(0, 1, 0, 30)))
        assertEquals("Mancano 2 giorni", countdownDescription(CountdownParts(2, 0, 0, 0)))
        assertEquals("Manca meno di un minuto", countdownDescription(CountdownParts(0, 0, 0, 42)))
    }

    @Test
    fun tickDelayAlignsToTheNextSecondBoundary() {
        // 2,3 s alla scadenza: la prossima cifra cambia fra 300 ms (a 2,0 s).
        assertEquals(300L, launchTickDelayMillis(target.minusMillis(2_300), target))
        // Esattamente su un secondo intero: un secondo pieno, non zero.
        assertEquals(1_000L, launchTickDelayMillis(target.minusSeconds(5), target))
        // Dopo l'uscita basta un controllo al minuto.
        assertEquals(60_000L, launchTickDelayMillis(target.plusSeconds(1), target))
        assertEquals(60_000L, launchTickDelayMillis(target, target))
    }

    @Test
    fun inviteMailPointsToPampa() {
        val mail = FunkazzenlandLaunch.INVITE_MAIL
        assertEquals("pampalonialessio2008@gmail.com", mail.to)
        assertEquals("Invito Fünkazzenland", mail.subject)
        assertTrue(mail.body.isNotBlank())
    }
}
