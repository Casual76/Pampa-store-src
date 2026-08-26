package com.pampa.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateQueueTest {

    private fun entry(
        id: String,
        state: DownloadState = DownloadState.Queued,
    ) = QueueEntry(
        appId = id,
        name = id.replaceFirstChar(Char::uppercase),
        channel = Channel.STABLE,
        targetVersion = "1.0.0",
        state = state,
    )

    @Test
    fun `un app gia in attesa non si accoda due volte`() {
        val queue = listOf(entry("alpha")).enqueue(listOf(entry("alpha"), entry("beta")))

        assertEquals(listOf("alpha", "beta"), queue.map(QueueEntry::appId))
    }

    @Test
    fun `un app in volo non si accoda di nuovo`() {
        val running = entry("alpha", DownloadState.Downloading(0.4f, 40, 100))

        val queue = listOf(running).enqueue(listOf(entry("alpha")))

        assertEquals(1, queue.size)
        assertTrue(queue.single().isRunning)
    }

    @Test
    fun `una voce finita viene sostituita da quella nuova invece di restare in lista`() {
        val done = entry("alpha", DownloadState.Installed("1.0.0"))

        val queue = listOf(done).enqueue(listOf(entry("alpha")))

        assertEquals(1, queue.size)
        assertTrue(queue.single().isWaiting)
    }

    @Test
    fun `riprovare rimette in attesa solo i falliti`() {
        val queue = listOf(
            entry("alpha", DownloadState.Installed("1.0.0")),
            entry("beta", DownloadState.Error("rete assente")),
            entry("gamma", DownloadState.Downloading(0.1f, 1, 10)),
        ).retryFailed()

        assertTrue(queue[0].isDone)
        assertTrue(queue[1].isWaiting)
        assertTrue(queue[2].isRunning)
    }

    @Test
    fun `svuotare la coda lascia solo cio che e gia partito`() {
        val queue = listOf(
            entry("alpha"),
            entry("beta", DownloadState.Installing("installo")),
            entry("gamma", DownloadState.Error("boom")),
        ).clearPending()

        assertEquals(listOf("beta"), queue.map(QueueEntry::appId))
    }

    @Test
    fun `lo stato si scrive solo sulla voce che lo riguarda`() {
        val queue = listOf(entry("alpha"), entry("beta"))
            .withState("beta", DownloadState.Verifying("controllo"))

        assertTrue(queue[0].isWaiting)
        assertEquals(DownloadState.Verifying("controllo"), queue[1].state)
    }
}
