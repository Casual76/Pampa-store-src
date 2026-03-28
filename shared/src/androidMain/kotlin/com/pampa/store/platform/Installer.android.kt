package com.pampa.store.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.pampa.store.data.model.AppPlatform
import com.pampa.store.data.model.InstallEvent
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile

actual class AppInstaller(private val context: Context) : InstallerService {
    actual override fun install(filePath: String, appId: String): Flow<InstallEvent> = flow {
        emit(InstallEvent.Preparing)

        val file = File(filePath)
        if (!file.exists()) {
            emit(InstallEvent.Failure("missing_file", "File not found: $filePath"))
            return@flow
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permissionIntent)
            emit(
                InstallEvent.Failure(
                    "unknown_sources_permission",
                    "Abilita l'installazione da origini sconosciute per Pampa Store e riprova."
                )
            )
            return@flow
        }

        val packageInfo = context.packageManager.readArchiveInfo(filePath)
        if (packageInfo == null) {
            emit(
                InstallEvent.Failure(
                    "invalid_apk",
                    "Android non riesce a leggere l'APK scaricato."
                )
            )
            return@flow
        }

        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageInfo.packageName)
        }

        val sessionId = try {
            packageInstaller.createSession(sessionParams)
        } catch (e: Exception) {
            emit(
                InstallEvent.Failure(
                    "session_create_failed",
                    e.message ?: "Impossibile avviare la sessione di installazione."
                )
            )
            return@flow
        }

        val events = PackageInstallSessionRegistry.register(sessionId)

        try {
            packageInstaller.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite(file.name, 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                emit(InstallEvent.Installing("Invio del pacchetto al sistema..."))

                val callbackIntent = Intent(context, PackageInstallResultReceiver::class.java).apply {
                    action = PackageInstallResultReceiver.ACTION_INSTALL_STATUS
                    putExtra(PackageInstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    flags
                )

                session.commit(pendingIntent.intentSender)
            }

            var terminalEvent: InstallEvent? = null
            events.takeWhile { event ->
                val keepCollecting = event !is InstallEvent.Success && event !is InstallEvent.Failure
                if (!keepCollecting) terminalEvent = event
                keepCollecting
            }.collect { emit(it) }

            terminalEvent?.let { emit(it) }
        } catch (e: Exception) {
            runCatching {
                packageInstaller.abandonSession(sessionId)
            }
            PackageInstallSessionRegistry.unregister(sessionId)
            emit(
                InstallEvent.Failure(
                    "session_write_failed",
                    e.message ?: "Impossibile scrivere l'APK nella sessione di installazione."
                )
            )
        }
    }
}

actual fun getCurrentPlatform(): AppPlatform = AppPlatform.ANDROID

actual fun openFileManager(path: String) {
    // No-op on Android, handled via intents
}

@Suppress("DEPRECATION")
private fun PackageManager.readArchiveInfo(filePath: String): PackageInfo? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(filePath, PackageManager.PackageInfoFlags.of(0))
    } else {
        getPackageArchiveInfo(filePath, 0)
    }
}
