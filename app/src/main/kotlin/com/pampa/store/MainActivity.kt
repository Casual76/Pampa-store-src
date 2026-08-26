package com.pampa.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pampa.store.platform.StoreNotifications
import com.pampa.store.ui.PampaStoreApp
import com.pampa.store.viewmodel.StoreViewModel
import com.pampa.store.work.UpdateCheckWorker

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<StoreViewModel>()

    /**
     * La rotta che un intent esterno ha chiesto di aprire, finché qualcuno non la consuma.
     *
     * Non è un parametro di navigazione: l'intent può arrivare quando l'app è già in piedi
     * ([onNewIntent]), quindi deve essere uno stato osservabile e non un valore letto una volta
     * durante [onCreate].
     */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StoreNotifications(this).ensureChannel()
        UpdateCheckWorker.schedule(this)
        pendingRoute = intent.routeExtra()
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshInstalledApps()
                    // Il permesso notifiche si revoca dalle impostazioni di sistema, cioè fuori da
                    // qui: l'unico momento in cui possiamo accorgercene è tornando in primo piano.
                    viewModel.refreshNotificationPermission()
                }
            },
        )
        setContent {
            PampaStoreApp(
                viewModel = viewModel,
                pendingRoute = pendingRoute,
                onRouteConsumed = { pendingRoute = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.routeExtra()
    }
}

private fun Intent.routeExtra(): String? =
    getStringExtra(StoreNotifications.ExtraRoute)?.takeIf(String::isNotBlank)
