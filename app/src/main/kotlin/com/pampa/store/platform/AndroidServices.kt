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
import com.pampa.store.data.InstallEvent
import com.pampa.store.data.InstallPrerequisites
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile

class AndroidAppService(private val context: Context) {
    fun installPrerequisites(): InstallPrerequisites {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        return if (allowed) {
            InstallPrerequisites()
        } else {
            InstallPrerequisites(
                canInstallPackages = false,
                message = "Abilita Pampa Store tra le origini consentite per installare gli APK.",
            )
        }
    }

    fun installedVersion(packageName: String): String? {
        if (packageName.isBlank()) return null
        return runCatching {
            val info = context.packageManager.installedPackageInfo(packageName)
            info.versionName ?: info.longVersionCodeCompat().toString()
        }.getOrNull()
    }

    fun openInstallSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun isInstalled(packageName: String): Boolean = installedVersion(packageName) != null

    /**
     * Chiede ad Android di disinstallare un pacchetto.
     *
     * `ACTION_DELETE` e non `PackageInstaller.uninstall`: il secondo pretende che lo Store sia
     * l'installer di record del pacchetto, cioè funziona solo per le app che ha installato lui e
     * lancia `SecurityException` per tutte le altre. Questo intent apre lo stesso dialogo di sistema
     * e vale per qualunque pacchetto.
     *
     * Non torna un esito: la conferma è del sistema e la risposta arriva dal fatto che, al rientro
     * in primo piano, il pacchetto non c'è più.
     */
    fun requestUninstall(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    fun downloadsDirectory(): File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "downloads",
    ).also(File::mkdirs)

    /**
     * Butta l’APK che ha finito il suo lavoro.
     *
     * La cartella dei download sta in `getExternalFilesDir`, che nessuno svuota per noi. Una
     * volta che il pacchetto è stato consegnato al PackageInstaller il file non serve più a
     * niente — tenerlo vuol dire solo lasciare decine di megabyte a ogni installazione.
     */
    fun discardDownload(file: File) {
        runCatching { file.delete() }
    }

    /**
     * Svuota la cartella dei download da quello che è rimasto indietro.
     *
     * Perché [discardDownload] da solo non basta: se l’installazione fallisce, o se è lo Store
     * stesso ad aggiornarsi — e allora Android termina il processo prima che ci sia modo di
     * cancellare alcunché — l’APK resta lì. Da chiamare solo quando non c’è nessun download in
     * volo, altrimenti si porta via il file che qualcuno sta ancora scrivendo.
     */
    fun pruneDownloads() {
        runCatching { downloadsDirectory().listFiles()?.forEach(File::delete) }
    }

    fun validateApk(file: File, expectedPackageName: String): Result<PackageInfo> = runCatching {
        require(file.exists() && file.length() > 0L) { "Il file scaricato è vuoto o mancante." }
        val info = context.packageManager.archivePackageInfo(file.absolutePath)
            ?: error("Android non riesce a leggere l’APK scaricato.")
        if (expectedPackageName.isNotBlank()) {
            require(info.packageName == expectedPackageName) {
                "Il pacchetto ${info.packageName} non corrisponde a $expectedPackageName."
            }
        }
        info
    }
}

class AppInstaller(private val context: Context) {
    fun install(file: File, expectedPackageName: String): Flow<InstallEvent> = flow {
        emit(InstallEvent.Preparing)

        if (!file.exists()) {
            emit(InstallEvent.Failure("missing_file", "APK non trovato."))
            return@flow
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            emit(InstallEvent.Failure("permission", "Autorizza Pampa Store a installare app e riprova."))
            return@flow
        }

        val packageInfo = context.packageManager.archivePackageInfo(file.absolutePath)
        if (packageInfo == null ||
            (expectedPackageName.isNotBlank() && packageInfo.packageName != expectedPackageName)
        ) {
            emit(InstallEvent.Failure("invalid_apk", "L’APK non è valido per questa app."))
            return@flow
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageInfo.packageName)
        }
        val sessionId = runCatching { packageInstaller.createSession(params) }.getOrElse { error ->
            emit(InstallEvent.Failure("session", error.message ?: "Impossibile preparare l’installazione."))
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
                emit(InstallEvent.Installing("Invio del pacchetto ad Android…"))
                val callback = Intent(context, PackageInstallResultReceiver::class.java).apply {
                    action = PackageInstallResultReceiver.ACTION_INSTALL_STATUS
                    putExtra(PackageInstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callback, flags)
                session.commit(pendingIntent.intentSender)
            }

            var terminal: InstallEvent? = null
            events.takeWhile { event ->
                val keepGoing = event !is InstallEvent.Success && event !is InstallEvent.Failure
                if (!keepGoing) terminal = event
                keepGoing
            }.collect(::emit)
            terminal?.let { emit(it) }
        } catch (error: Throwable) {
            runCatching { packageInstaller.abandonSession(sessionId) }
            PackageInstallSessionRegistry.unregister(sessionId)
            emit(InstallEvent.Failure("installer", error.message ?: "Installazione non riuscita."))
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.installedPackageInfo(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        getPackageInfo(packageName, 0)
    }

@Suppress("DEPRECATION")
private fun PackageManager.archivePackageInfo(path: String): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
    } else {
        getPackageArchiveInfo(path, 0)
    }

private fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
