package com.pampa.store.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pampa.store.data.CachedCatalog
import com.pampa.store.data.CatalogClient
import com.pampa.store.data.StorePersistence
import com.pampa.store.data.announcedVersions
import com.pampa.store.data.resolveStoreApps
import com.pampa.store.data.updatesToAnnounce
import com.pampa.store.platform.AndroidAppService
import com.pampa.store.platform.StoreNotifications
import java.util.concurrent.TimeUnit

/**
 * Il controllo che gira anche quando lo Store è chiuso.
 *
 * Senza, un aggiornamento esiste solo dal momento in cui apri l'app — che per uno store è il caso
 * d'uso esattamente al contrario: lo apri *perché* ti è stato detto che c'è qualcosa.
 *
 * Ogni giro fa tre cose e in quest'ordine: scarica il catalogo, **lo scrive nella stessa cache che
 * legge l'app** (così la prossima apertura è già piena invece di partire da uno schermo vuoto), e
 * solo alla fine decide se c'è qualcosa da annunciare.
 */
class UpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val persistence = StorePersistence(applicationContext)
        val client = CatalogClient()
        val catalog = try {
            client.fetchCatalog()
        } catch (error: Throwable) {
            // Rete assente, GitHub giù, manifest malformato: nessuno di questi è un motivo per
            // rinunciare. Il prossimo giro è fra sei ore e nel frattempo non è cambiato niente per
            // chi guarda lo schermo.
            return Result.retry()
        } finally {
            client.close()
        }

        persistence.writeCachedCatalog(
            CachedCatalog(
                apps = catalog.apps,
                storeReleases = catalog.storeReleases,
                warnings = catalog.warnings,
                savedAt = System.currentTimeMillis(),
            ),
        )

        val appService = AndroidAppService(applicationContext)
        val updatable = resolveStoreApps(catalog.apps, appService::installedVersion)
            .filter { it.hasUpdate }

        val notifications = StoreNotifications(applicationContext)
        if (updatable.isEmpty()) {
            notifications.cancel()
            persistence.writeAnnouncedVersions(emptyMap())
            return Result.success()
        }

        val announced = persistence.readAnnouncedVersions()
        val fresh = updatesToAnnounce(updatable, announced)
        if (fresh.isNotEmpty()) {
            // La notifica elenca *tutto* quello che è aggiornabile, non solo le novità: quello che
            // la persona vede aprendo lo Store è la lista intera, e una notifica che ne nomina
            // metà si legge come uno scarto fra i due.
            notifications.notifyUpdates(updatable)
        }
        persistence.writeAnnouncedVersions(announcedVersions(updatable))
        return Result.success()
    }

    companion object {
        private const val UniqueName = "pampa-update-check"
        private const val IntervalHours = 6L

        /**
         * `KEEP` e non `REPLACE`.
         *
         * `REPLACE` rimetterebbe il timer a zero a ogni avvio dell'app, e un'app che apri più
         * spesso di ogni sei ore non controllerebbe **mai** in background: il lavoro periodico
         * ricomincerebbe sempre da capo poco prima di scadere.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(IntervalHours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UniqueName, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
