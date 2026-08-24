package com.pampa.store.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.pampa.store.data.InstallEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PackageInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(
            EXTRA_SESSION_ID,
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
        )
        if (sessionId == -1) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                intent.parcelableIntent(Intent.EXTRA_INTENT)?.let { confirmation ->
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstallSessionRegistry.emit(
                    sessionId,
                    InstallEvent.AwaitingUserAction("Conferma l’installazione sul dispositivo."),
                )
            }
            PackageInstaller.STATUS_SUCCESS ->
                PackageInstallSessionRegistry.emit(sessionId, InstallEvent.Success)
            else -> PackageInstallSessionRegistry.emit(
                sessionId,
                InstallEvent.Failure(status.failureCode(), status.failureMessage(message)),
            )
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.pampa.store.ACTION_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "com.pampa.store.extra.INSTALL_SESSION_ID"
    }
}

internal object PackageInstallSessionRegistry {
    private val sessions = ConcurrentHashMap<Int, MutableSharedFlow<InstallEvent>>()

    fun register(sessionId: Int): SharedFlow<InstallEvent> =
        MutableSharedFlow<InstallEvent>(replay = 8, extraBufferCapacity = 8)
            .also { sessions[sessionId] = it }
            .asSharedFlow()

    fun unregister(sessionId: Int) {
        sessions.remove(sessionId)
    }

    fun emit(sessionId: Int, event: InstallEvent) {
        sessions[sessionId]?.tryEmit(event)
        if (event is InstallEvent.Success || event is InstallEvent.Failure) unregister(sessionId)
    }
}

private fun Int.failureCode(): String = when (this) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> "aborted"
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
    PackageInstaller.STATUS_FAILURE_CONFLICT -> "conflict"
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "incompatible"
    PackageInstaller.STATUS_FAILURE_INVALID -> "invalid"
    PackageInstaller.STATUS_FAILURE_STORAGE -> "storage"
    else -> "failure"
}

private fun Int.failureMessage(systemMessage: String): String = when (this) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> "Installazione annullata."
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android ha bloccato l’installazione."
    PackageInstaller.STATUS_FAILURE_CONFLICT -> "La firma non coincide con l’app già installata."
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "L’APK non è compatibile con questo dispositivo."
    PackageInstaller.STATUS_FAILURE_INVALID -> "Android considera l’APK non valido."
    PackageInstaller.STATUS_FAILURE_STORAGE -> "Spazio insufficiente per completare l’installazione."
    else -> systemMessage.ifBlank { "Installazione non riuscita." }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableIntent(key: String): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        getParcelableExtra(key) as? Intent
    }
