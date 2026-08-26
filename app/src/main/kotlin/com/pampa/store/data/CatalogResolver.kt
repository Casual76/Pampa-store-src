package com.pampa.store.data

import com.pampa.store.util.isVersionNewer

/**
 * Come si chiede ad Android che versione di un pacchetto è installata.
 *
 * Un'interfaccia invece della chiamata diretta perché è l'unica riga di [resolveStoreApps] che ha
 * bisogno di un dispositivo: astraendola, la definizione di "questa app è aggiornabile" si prova
 * senza emulatore.
 */
fun interface InstalledVersionLookup {
    fun installedVersion(packageName: String): String?
}

/**
 * Dalle voci del catalogo alle app con lo stato di installazione addosso.
 *
 * Vive qui e non nel ViewModel perché non ha un solo lettore: il worker che controlla gli
 * aggiornamenti in background deve rispondere **esattamente** alla stessa domanda della schermata.
 * Due implementazioni della stessa definizione divergono, e il modo in cui te ne accorgi è una
 * notifica per un aggiornamento che poi l'app non ti mostra — o, peggio, il contrario.
 *
 * Lo Store stesso è escluso: si aggiorna per conto suo tramite [StoreReleaseChannels], perché è
 * l'unica app del catalogo che deve sostituire il processo che sta eseguendo l'aggiornamento.
 */
fun resolveStoreApps(
    entries: List<AppEntry>,
    lookup: InstalledVersionLookup,
): List<StoreApp> = entries
    .filterNot { it.id == STORE_APP_ID }
    .map { entry ->
        val installedVersion = lookup.installedVersion(entry.packageName)
        // Il caso null va tolto di mezzo per primo. Scritto come `when (installedVersion)` con
        // `entry.beta?.version ->` in cima, un'app **non installata** di un'app **senza beta**
        // confronta null con null e finisce sul canale beta: da lì `versionFor` ripiega sulla
        // stabile e la voce dichiara una versione disponibile che nessuno ha chiesto.
        val installedChannel = when {
            installedVersion == null -> null
            installedVersion == entry.beta?.version -> Channel.BETA
            else -> Channel.STABLE
        }
        val latest = installedChannel?.let(entry::versionFor)
        StoreApp(
            entry = entry,
            installedVersion = installedVersion,
            installedChannel = installedChannel,
            availableVersion = latest?.version,
            hasUpdate = installedVersion != null && latest != null &&
                isVersionNewer(latest.version, installedVersion),
        )
    }
    .sortedBy { it.entry.name.lowercase() }

/**
 * Cosa vale la pena annunciare, dato cosa è già stato annunciato.
 *
 * La mappa è `id dell'app` → `versione per cui è già uscita una notifica`. Serve perché il controllo
 * gira ogni sei ore e un aggiornamento che nessuno installa resta disponibile per giorni: senza
 * questo confronto la stessa versione della stessa app produrrebbe quattro notifiche al giorno
 * finché non la installi, che è il modo più rapido di far spegnere le notifiche a qualcuno.
 *
 * Il confronto è sulla **versione**, non sull'id: se esce una versione ancora più nuova di un
 * aggiornamento già annunciato, quella è una notizia nuova e si annuncia.
 */
fun updatesToAnnounce(
    updatable: List<StoreApp>,
    alreadyAnnounced: Map<String, String>,
): List<StoreApp> = updatable.filter { app ->
    val target = app.availableVersion ?: return@filter false
    alreadyAnnounced[app.entry.id] != target
}

/**
 * Il registro degli annunci dopo un giro.
 *
 * Ricostruito dall'insieme aggiornabile invece che accumulato: un'app che è stata installata, o
 * tolta dal catalogo, esce dalla mappa da sola. Accumulando, il registro sarebbe cresciuto per
 * sempre e avrebbe zittito l'annuncio di una reinstallazione della stessa versione.
 */
fun announcedVersions(updatable: List<StoreApp>): Map<String, String> =
    updatable.mapNotNull { app -> app.availableVersion?.let { app.entry.id to it } }.toMap()

/**
 * Se rimettere questa release richiede prima di disinstallare l'app.
 *
 * Android rifiuta di sovrascrivere un pacchetto con uno di `versionCode` inferiore e la deroga è
 * riservata al sistema, quindi tornare a una versione precedente è un'operazione diversa
 * dall'installarne una: costa i dati dell'app. Sta qui, e non nel ViewModel, perché la decisione ha
 * due lettori — chi la esegue e il pannello che la annuncia prima del tocco — e se divergono uno dei
 * due mente.
 */
fun requiresUninstallFirst(installedVersion: String?, targetVersion: String): Boolean =
    installedVersion != null && isVersionNewer(installedVersion, targetVersion)
