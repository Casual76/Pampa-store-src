package com.pampa.store

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pampa.store.data.api.DisabledAdminPublishingService
import com.pampa.store.data.local.LocalStorage
import com.pampa.store.data.local.PreferencesStorage
import com.pampa.store.data.model.DEFAULT_GITHUB_MANIFEST_PATH
import com.pampa.store.data.model.DEFAULT_STORE_OWNER
import com.pampa.store.data.model.DEFAULT_STORE_REPO
import com.pampa.store.data.model.GitHubRepositoryRef
import com.pampa.store.platform.AppInstaller
import com.pampa.store.platform.AndroidPlatformAppService
import com.pampa.store.ui.PampaStoreApp

class MainActivity : ComponentActivity() {

    private lateinit var localStorage: LocalStorage
    private lateinit var installer: AppInstaller
    private lateinit var platformAppService: AndroidPlatformAppService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefsStorage = PreferencesStorage(applicationContext)
        localStorage = LocalStorage(prefsStorage)
        installer = AppInstaller(applicationContext)
        platformAppService = AndroidPlatformAppService(applicationContext)

        setContent {
            PampaStoreApp(
                localStorage = localStorage,
                installerProvider = { installer },
                platformAppService = platformAppService,
                adminPublishingService = DisabledAdminPublishingService(
                    "Publishing disponibile solo su desktop con PAMPA_GH_TOKEN, PAMPA_REPO_OWNER e PAMPA_REPO_NAME. " +
                        "Per i client Android il repository store deve essere pubblico."
                ),
                storeRepository = GitHubRepositoryRef(
                    repoOwner = DEFAULT_STORE_OWNER,
                    repoName = DEFAULT_STORE_REPO,
                    manifestPath = DEFAULT_GITHUB_MANIFEST_PATH
                ),
                appVersion = BuildConfig.VERSION_NAME,
                adminPanelBuildEnabled = BuildConfig.DEBUG
            )
        }
    }
}
