package com.pampa.store.pampai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.pampa.store.MainActivity
import com.pampa.store.R
import com.pampa.store.data.DownloadState
import com.pampa.store.engine.StoreEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Tiene vivo il processo mentre la coda delle installazioni lavora, con una notifica che dice a
 * che punto e'. Serve quando la coda parte da fuori (PampAI/Aria) e lo Store non ha una schermata
 * aperta: senza, Android puo' chiudere il processo a meta' download. Si ferma da solo quando la
 * coda si svuota.
 */
class StoreInstallService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var watcher: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val manager = getSystemService(NotificationManager::class.java)
    manager?.createNotificationChannel(NotificationChannel(CHANNEL, "Installazioni", NotificationManager.IMPORTANCE_LOW).apply { description = "La coda delle installazioni mentre lavora" })
    val notification = build("Preparazione…")
    if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(NOTIFICATION_ID, notification)
    val engine = StoreEngine.get(this)
    watcher?.cancel()
    watcher = scope.launch {
      combine(engine.queueActive, engine.uiState) { active, state -> active to state.updateQueue }
        .distinctUntilChanged()
        .collect { (active, queue) ->
          if (!active) {
            stopSelf()
            return@collect
          }
          val running = queue.firstOrNull { it.isRunning }
          val text = when (val state = running?.state) {
            is DownloadState.Downloading -> "${running.name}: ${(state.progress * 100).toInt()}%"
            is DownloadState.Verifying -> "${running.name}: ${state.message}"
            is DownloadState.AwaitingUser -> "${running.name}: conferma l'installazione"
            is DownloadState.Installing -> "${running.name}: ${state.message}"
            else -> running?.name ?: "In coda: ${queue.count { it.isWaiting }}"
          }
          runCatching { manager?.notify(NOTIFICATION_ID, build(text)) }
        }
    }
    return START_NOT_STICKY
  }

  private fun build(text: String): Notification {
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return Notification.Builder(this, CHANNEL)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Pampa Store")
      .setContentText(text)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(open)
      .build()
  }

  override fun onDestroy() {
    watcher?.cancel()
    scope.cancel()
    super.onDestroy()
  }

  private companion object {
    const val CHANNEL = "install_queue"
    const val NOTIFICATION_ID = 4202
  }
}
