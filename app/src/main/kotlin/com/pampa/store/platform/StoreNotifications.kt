package com.pampa.store.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pampa.store.MainActivity
import com.pampa.store.R
import com.pampa.store.data.PendingUpdate

/**
 * La notifica che dice che c'è qualcosa da aggiornare, e nient'altro.
 *
 * Una sola, raggruppata: quattro app aggiornabili sono una notizia sola ("ci sono aggiornamenti"),
 * non quattro. Quattro righe separate nell'ombra delle notifiche sono anche quattro cose da
 * scartare a mano, ed è il modo più rapido di far spegnere il canale a qualcuno.
 */
class StoreNotifications(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ChannelId,
            "Aggiornamenti disponibili",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avvisa quando una delle app installate — o lo Store stesso — ha una " +
                "versione più recente."
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Se il sistema ci lascia pubblicare.
     *
     * Sotto Android 13 il permesso non esiste e la risposta è sempre sì; da lì in su va chiesto, e
     * può essere revocato in qualunque momento dalle impostazioni — quindi si ricontrolla a ogni
     * ritorno in primo piano invece di ricordarsi la risposta di una volta.
     */
    fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notifyUpdates(updates: List<PendingUpdate>) {
        if (updates.isEmpty() || !canPost()) return
        ensureChannel()

        val title = if (updates.size == 1) {
            "1 aggiornamento pronto"
        } else {
            "${updates.size} aggiornamenti pronti"
        }
        val names = updates.map { update ->
            val from = update.installedVersion
            if (from != null) {
                "${update.name}  $from → ${update.availableVersion}"
            } else {
                "${update.name}  ${update.availableVersion}"
            }
        }

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        names.take(MaxLines).forEach(style::addLine)
        if (names.size > MaxLines) style.setSummaryText("e altre ${names.size - MaxLines}")

        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(names.joinToString(", ") { it.substringBefore("  ") })
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(openUpdatesIntent())
            .build()

        // areNotificationsEnabled() e il permesso sono già stati controllati sopra, ma fra quel
        // controllo e questa riga il sistema resta libero di dire di no: revocare un permesso non
        // aspetta il nostro comodo.
        runCatching { NotificationManagerCompat.from(context).notify(NotificationId, notification) }
    }

    /**
     * Ritira la notifica quando non c'è più niente da annunciare.
     *
     * L'elenco deve comprendere **anche** lo Store: chiamata con le sole app del catalogo, questa
     * riga cancellerebbe una notifica che parla di un aggiornamento ancora in piedi, e la notizia
     * sparirebbe dall'ombra senza che nessuno l'abbia letta.
     */
    fun syncWithUpdates(updates: List<PendingUpdate>) {
        if (updates.isEmpty()) cancel()
    }

    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(NotificationId) }
    }

    fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openUpdatesIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(ExtraRoute, RouteUpdates)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ChannelId = "updates"
        const val NotificationId = 4201
        const val ExtraRoute = "com.pampa.store.extra.ROUTE"
        const val RouteUpdates = "updates"
        private const val MaxLines = 5
    }
}
