package com.pampa.store.platform

data class InstallPrerequisites(
    val canInstallPackages: Boolean = true,
    val message: String? = null
)

interface PlatformAppService {
    suspend fun getInstallPrerequisites(): InstallPrerequisites

    suspend fun resolveInstalledVersion(packageName: String): String?

    fun openInstallSettings()

    fun launchInstalledApp(packageName: String): Boolean
}
