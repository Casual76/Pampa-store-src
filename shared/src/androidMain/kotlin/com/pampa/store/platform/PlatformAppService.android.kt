package com.pampa.store.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

class AndroidPlatformAppService(
    private val context: Context
) : PlatformAppService {
    override suspend fun getInstallPrerequisites(): InstallPrerequisites {
        val canInstallPackages = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        return if (canInstallPackages) {
            InstallPrerequisites(canInstallPackages = true)
        } else {
            InstallPrerequisites(
                canInstallPackages = false,
                message = "Abilita le origini sconosciute per installare gli APK da Pampa Store."
            )
        }
    }

    override suspend fun resolveInstalledVersion(packageName: String): String? {
        if (packageName.isBlank()) return null
        return try {
            val packageInfo = context.packageManager.getInstalledPackageInfo(packageName)
            packageInfo.versionName ?: packageInfo.longVersionCodeCompat().toString()
        } catch (_: Exception) {
            null
        }
    }

    override fun openInstallSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun launchInstalledApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.getInstalledPackageInfo(packageName: String) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
} else {
    getPackageInfo(packageName, 0)
}

private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}
