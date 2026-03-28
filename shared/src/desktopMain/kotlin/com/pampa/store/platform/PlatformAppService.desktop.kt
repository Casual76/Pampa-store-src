package com.pampa.store.platform

class DesktopPlatformAppService : PlatformAppService {
    override suspend fun getInstallPrerequisites(): InstallPrerequisites {
        return InstallPrerequisites(canInstallPackages = true)
    }

    override suspend fun resolveInstalledVersion(packageName: String): String? = null

    override fun openInstallSettings() = Unit

    override fun launchInstalledApp(packageName: String): Boolean = false
}
