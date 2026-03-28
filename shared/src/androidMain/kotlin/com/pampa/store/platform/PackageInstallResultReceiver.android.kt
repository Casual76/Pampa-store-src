package com.pampa.store.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.pampa.store.data.model.InstallEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PackageInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(
            PackageInstallReceiverExtras.sessionIdKey(intent),
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        )
        if (sessionId == -1) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = intent.parcelableIntent(Intent.EXTRA_INTENT)
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                }
                PackageInstallSessionRegistry.tryEmit(
                    sessionId,
                    InstallEvent.AwaitingUserAction("Conferma l'installazione sul dispositivo.")
                )
            }
            PackageInstaller.STATUS_SUCCESS -> {
                PackageInstallSessionRegistry.tryEmit(sessionId, InstallEvent.Success)
            }
            else -> {
                PackageInstallSessionRegistry.tryEmit(
                    sessionId,
                    InstallEvent.Failure(
                        code = status.toInstallFailureCode(),
                        message = status.toInstallFailureMessage(statusMessage)
                    )
                )
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.pampa.store.ACTION_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "com.pampa.store.extra.INSTALL_SESSION_ID"
    }
}

internal object PackageInstallSessionRegistry {
    private val sessionEvents = ConcurrentHashMap<Int, MutableSharedFlow<InstallEvent>>()

    fun register(sessionId: Int): SharedFlow<InstallEvent> {
        val flow = MutableSharedFlow<InstallEvent>(replay = 8, extraBufferCapacity = 8)
        sessionEvents[sessionId] = flow
        return flow.asSharedFlow()
    }

    fun unregister(sessionId: Int) {
        sessionEvents.remove(sessionId)
    }

    fun tryEmit(sessionId: Int, event: InstallEvent) {
        sessionEvents[sessionId]?.tryEmit(event)
        if (event is InstallEvent.Success || event is InstallEvent.Failure) {
            unregister(sessionId)
        }
    }
}

private object PackageInstallReceiverExtras {
    fun sessionIdKey(intent: Intent): String {
        return if (intent.hasExtra(PackageInstallResultReceiver.EXTRA_SESSION_ID)) {
            PackageInstallResultReceiver.EXTRA_SESSION_ID
        } else {
            PackageInstaller.EXTRA_SESSION_ID
        }
    }
}

private fun Int.toInstallFailureCode(): String = when (this) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> "aborted"
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
    PackageInstaller.STATUS_FAILURE_CONFLICT -> "conflict"
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "incompatible"
    PackageInstaller.STATUS_FAILURE_INVALID -> "invalid"
    PackageInstaller.STATUS_FAILURE_STORAGE -> "storage"
    else -> "generic_failure"
}

private fun Int.toInstallFailureMessage(systemMessage: String): String = when (this) {
    PackageInstaller.STATUS_FAILURE_ABORTED ->
        "Installazione annullata dall'utente."
    PackageInstaller.STATUS_FAILURE_BLOCKED ->
        "Android ha bloccato l'installazione dell'APK."
    PackageInstaller.STATUS_FAILURE_CONFLICT ->
        "Conflitto di firma o versione con un'app gia' installata."
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
        "L'APK non e' compatibile con questo dispositivo."
    PackageInstaller.STATUS_FAILURE_INVALID ->
        "Android considera l'APK non valido o non installabile."
    PackageInstaller.STATUS_FAILURE_STORAGE ->
        "Spazio insufficiente per completare l'installazione."
    else ->
        systemMessage.ifBlank { "Installazione non riuscita." }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableIntent(key: String): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        getParcelableExtra(key) as? Intent
    }
}
