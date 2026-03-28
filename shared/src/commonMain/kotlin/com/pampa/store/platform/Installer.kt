package com.pampa.store.platform

import com.pampa.store.data.model.AppPlatform
import com.pampa.store.data.model.InstallEvent
import kotlinx.coroutines.flow.Flow

interface InstallerService {
    fun install(filePath: String, appId: String): Flow<InstallEvent>
}

expect class AppInstaller : InstallerService {
    override fun install(filePath: String, appId: String): Flow<InstallEvent>
}

expect fun getCurrentPlatform(): AppPlatform

expect fun openFileManager(path: String)
