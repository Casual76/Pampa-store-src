package com.pampa.store

import com.pampa.store.automation.DesktopReleaseAutomationService
import com.pampa.store.automation.NoOpReleaseAutomationService
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pampa.store.data.api.DisabledAdminPublishingService
import com.pampa.store.data.api.GitHubAdminConfig
import com.pampa.store.data.api.GitHubAdminPublishingService
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.local.PreferencesStorage
import com.pampa.store.data.model.DEFAULT_GITHUB_MANIFEST_PATH
import com.pampa.store.data.model.DEFAULT_STORE_OWNER
import com.pampa.store.data.model.DEFAULT_STORE_REPO
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.platform.AppInstaller
import com.pampa.store.platform.DesktopPlatformAppService
import com.pampa.store.ui.PampaStoreApp
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties

fun main() = application {
    val prefsStorage = PreferencesStorage()
    val localStorage = LocalStorage(prefsStorage)
    
    // Inizializzazione sincrona (bloccante all'avvio) delle preferenze
    runBlocking { localStorage.init() }
    
    val prefs = localStorage.preferences.value
    val installer = AppInstaller()
    val platformAppService = DesktopPlatformAppService()
    
    val githubToken = prefs.githubToken.ifBlank { resolveDesktopGitHubToken() }
    val storeRepository = if (prefs.repoOwner.isNotBlank() && prefs.repoName.isNotBlank()) {
        GitHubRepositoryRef(
            repoOwner = prefs.repoOwner,
            repoName = prefs.repoName,
            manifestPath = DEFAULT_GITHUB_MANIFEST_PATH
        )
    } else {
        createDesktopStoreRepository()
    }
    
    val adminPublishingService = createDesktopAdminPublishingService(storeRepository, githubToken)
    val appVersion = "1.1.6" // Versione aggiornata come richiesto
    val adminPanelBuildEnabled = prefs.adminPanelEnabled || resolveDesktopAdminPanelBuildFlag(appVersion)
    
    val releaseAutomationService = if (adminPanelBuildEnabled && adminPublishingService.isConfigured) {
        DesktopReleaseAutomationService()
    } else {
        NoOpReleaseAutomationService
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Pampa Store",
        state = rememberWindowState(
            size = DpSize(1100.dp, 750.dp)
        )
    ) {
        PampaStoreApp(
            localStorage = localStorage,
            installerProvider = { installer },
            platformAppService = platformAppService,
            adminPublishingService = adminPublishingService,
            releaseAutomationService = releaseAutomationService,
            storeRepository = storeRepository,
            appVersion = appVersion,
            adminPanelBuildEnabled = adminPanelBuildEnabled,
            catalogGitHubToken = githubToken
        )
    }
}

private fun createDesktopStoreRepository(): GitHubRepositoryRef {
    val owner = System.getenv("PAMPA_REPO_OWNER").orEmpty().ifBlank { DEFAULT_STORE_OWNER }
    val repo = System.getenv("PAMPA_REPO_NAME").orEmpty().ifBlank { DEFAULT_STORE_REPO }
    return GitHubRepositoryRef(
        repoOwner = owner,
        repoName = repo,
        manifestPath = DEFAULT_GITHUB_MANIFEST_PATH
    )
}

private fun createDesktopAdminPublishingService(
    storeRepository: GitHubRepositoryRef,
    token: String
) = run {
    if (token.isBlank()) {
        DisabledAdminPublishingService(
            "Publishing desktop disattivato. Configura PAMPA_GH_TOKEN (o GITHUB_TOKEN/GH_TOKEN), PAMPA_REPO_OWNER e PAMPA_REPO_NAME."
        )
    } else {
        GitHubAdminPublishingService(
            GitHubAdminConfig(
                storeRepository = storeRepository,
                token = token
            )
        )
    }
}

private fun resolveDesktopGitHubToken(): String {
    val localPropertiesToken = firstNonBlank(
        readLocalProperty("pampa.gh.token"),
        readLocalProperty("pampa.github.token"),
        readLocalProperty("github.token")
    )

    return firstNonBlank(
        System.getenv("PAMPA_GH_TOKEN"),
        System.getenv("GITHUB_TOKEN"),
        System.getenv("GH_TOKEN"),
        System.getProperty("pampa.gh.token"),
        localPropertiesToken
    )
}

private fun readLocalProperty(key: String): String {
    val localProperties = File("local.properties")
    if (!localProperties.exists()) return ""

    val properties = Properties()
    return runCatching {
        localProperties.inputStream().use { properties.load(it) }
        properties.getProperty(key).orEmpty().trim()
    }.getOrDefault("")
}

private fun firstNonBlank(vararg values: String?): String {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}

private fun resolveDesktopAdminPanelBuildFlag(appVersion: String): Boolean {
    val explicitValue = firstNonBlank(
        System.getenv("PAMPA_ADMIN_ENABLED"),
        System.getenv("PAMPA_ENABLE_ADMIN"),
        System.getProperty("pampa.admin.enabled"),
        System.getProperty("pampa.enable.admin")
    )

    return when {
        explicitValue.equals("1") ||
            explicitValue.equals("true", ignoreCase = true) ||
            explicitValue.equals("yes", ignoreCase = true) -> true

        explicitValue.equals("0") ||
            explicitValue.equals("false", ignoreCase = true) ||
            explicitValue.equals("no", ignoreCase = true) -> false

        else -> appVersion.equals("dev", ignoreCase = true)
    }
}
