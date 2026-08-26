package com.pampa.store.data

/**
 * Una voce della coda di aggiornamento.
 *
 * Lo stato è quello che una singola app ha già, [DownloadState]: la coda non ha bisogno di un
 * vocabolario proprio, e averne due significa tradurre l'uno nell'altro in ogni riga che li mostra
 * — cioè avere due posti in cui "sta scaricando" può voler dire cose diverse.
 */
data class QueueEntry(
    val appId: String,
    val name: String,
    val channel: Channel,
    val targetVersion: String,
    val state: DownloadState = DownloadState.Queued,
) {
    /** In volo: ha già cominciato e non si richiama indietro. */
    val isRunning: Boolean
        get() = state is DownloadState.Downloading ||
            state is DownloadState.Verifying ||
            state is DownloadState.AwaitingUser ||
            state is DownloadState.Installing

    val isWaiting: Boolean get() = state is DownloadState.Queued

    val isFailed: Boolean get() = state is DownloadState.Error

    val isDone: Boolean get() = state is DownloadState.Installed

    val isFinished: Boolean get() = isDone || isFailed
}

/**
 * Accoda quello che non c'è già.
 *
 * Un'app che è ancora in attesa o già in volo non si accoda una seconda volta: due voci per lo
 * stesso pacchetto sono due download dello stesso APK e due prompt di sistema identici. Una voce
 * *finita* invece si sostituisce, perché quello è il caso di chi riprova dopo un errore o aggiorna
 * di nuovo dopo che è uscita un'altra versione.
 */
fun List<QueueEntry>.enqueue(candidates: List<QueueEntry>): List<QueueEntry> {
    val pending = filterNot(QueueEntry::isFinished).map(QueueEntry::appId).toSet()
    val fresh = candidates.filterNot { it.appId in pending }
    val freshIds = fresh.map(QueueEntry::appId).toSet()
    // Le voci finite dello stesso id se ne vanno: quella nuova le rimpiazza invece di lasciare in
    // lista un "installata" o un "non riuscito" del giro precedente.
    return filterNot { it.appId in freshIds } + fresh
}

/** Rimette in attesa quello che è fallito, e lascia stare tutto il resto. */
fun List<QueueEntry>.retryFailed(): List<QueueEntry> =
    map { if (it.isFailed) it.copy(state = DownloadState.Queued) else it }

/**
 * Toglie dalla coda tutto quello che non è ancora partito, più i resti dei giri precedenti.
 *
 * Chi è in volo ci resta: un APK già consegnato al PackageInstaller non si richiama indietro da
 * qui, e fingere il contrario lascerebbe l'installazione a metà con la riga sparita dallo schermo.
 */
fun List<QueueEntry>.clearPending(): List<QueueEntry> = filter(QueueEntry::isRunning)

/** Aggiorna lo stato di una voce, se la voce c'è. */
fun List<QueueEntry>.withState(appId: String, state: DownloadState): List<QueueEntry> =
    map { if (it.appId == appId) it.copy(state = state) else it }
