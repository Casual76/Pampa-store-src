package com.pampa.store.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import com.pampa.store.BuildConfig
import com.pampa.store.data.AppEntry
import com.pampa.store.data.BuildVersion
import com.pampa.store.data.CachedCatalog
import com.pampa.store.data.CatalogClient
import com.pampa.store.data.Channel
import com.pampa.store.data.DownloadState
import com.pampa.store.data.InstallEvent
import com.pampa.store.data.PendingReinstall
import com.pampa.store.data.PendingUpdate
import com.pampa.store.data.QueueEntry
import com.pampa.store.data.RepositoryRef
import com.pampa.store.data.STORE_APP_ID
import com.pampa.store.data.STORE_APP_NAME
import com.pampa.store.data.StoreApp
import com.pampa.store.data.StoreEvent
import com.pampa.store.data.StoreEventTone
import com.pampa.store.data.StorePersistence
import com.pampa.store.data.StorePreferences
import com.pampa.store.data.StoreUiState
import com.pampa.store.data.StoreUpdate
import com.pampa.store.data.VersionInfo
import com.pampa.store.data.clearPending
import com.pampa.store.data.enqueue
import com.pampa.store.data.enumOrDefault
import com.pampa.store.data.pendingUpdate
import com.pampa.store.data.pendingUpdates
import com.pampa.store.data.releaseAssetUrl
import com.pampa.store.data.requiresUninstallFirst
import com.pampa.store.data.resolveStoreApps
import com.pampa.store.data.resolveStoreUpdate
import com.pampa.store.data.retryFailed
import com.pampa.store.data.versionFor
import com.pampa.store.data.withState
import com.pampa.store.pampai.StoreInstallService
import com.pampa.store.platform.AndroidAppService
import com.pampa.store.platform.AppInstaller
import com.pampa.store.platform.StoreNotifications
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Il cuore dello Store, uno per processo: il catalogo, lo stato delle app, la coda delle
 * installazioni. Era dentro `StoreViewModel`; da quando lo Store espone i suoi strumenti a
 * PampAI/Aria (che chiama dal proprio processo mentre lo Store puo' non avere una schermata
 * aperta) la logica vive qui, e il ViewModel e' un guscio che la mostra. Un solo stato per UI e
 * bridge: la coda che parte da Aria si vede nella scheda Aggiornamenti, e viceversa.
 */
class StoreEngine private constructor(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val persistence = StorePersistence(context)
  private val catalogClient = CatalogClient()
  private val appService = AndroidAppService(context)
  private val installer = AppInstaller(context)
  private val notifications = StoreNotifications(context)

  private val _uiState = MutableStateFlow(StoreUiState())
  val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

  /**
   * I fatti che meritano un avviso in cima allo schermo.
   *
   * Un flusso e non un campo dello stato: un avviso e' un *evento*, e messo dentro lo stato
   * sopravvive alla propria utilita' — riappare a ogni ricomposizione finche' qualcuno non si
   * ricorda di azzerarlo, e quel qualcuno prima o poi se ne dimentica.
   */
  private val _events = MutableSharedFlow<StoreEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<StoreEvent> = _events.asSharedFlow()

  /**
   * La coda gira in un job solo, e questo riferimento e' come si sa se sta gia' girando.
   *
   * Due job che pescano dalla stessa coda consegnerebbero due APK al PackageInstaller nello
   * stesso istante, e il secondo prompt di sistema copre il primo. Chi accoda chiama [pumpQueue],
   * che parte solo se non c'e' gia' qualcuno a pompare.
   */
  private var queueJob: Job? = null

  /** Vero mentre la coda lavora: il service in primo piano vive quanto questo. */
  private val _queueActive = MutableStateFlow(false)
  val queueActive: StateFlow<Boolean> = _queueActive.asStateFlow()

  init {
    BuildVersion.name = BuildConfig.VERSION_NAME
    scope.launch {
      persistence.preferences.collect { preferences ->
        _uiState.update { it.copy(preferences = preferences) }
        checkStoreUpdate()
      }
    }
    scope.launch { bootstrap() }
  }

  private suspend fun bootstrap() {
    // Prima di qualunque download: qui non c'e' ancora niente di nostro in volo, ed e' l'unico
    // momento in cui la cartella si puo' svuotare senza stare a guardare cosa c'e' dentro.
    withContext(Dispatchers.IO) { appService.pruneDownloads() }
    val cached = withContext(Dispatchers.IO) { persistence.readCachedCatalog() }
    if (cached != null && cached.apps.isNotEmpty()) {
      applyCatalog(cached, usingCache = true)
      _uiState.update { it.copy(isLoading = false) }
    }
    refreshNotificationPermission()
    refreshCatalog(initial = cached == null)
  }

  fun refreshCatalog(initial: Boolean = false) {
    scope.launch { refreshCatalogInternal(initial) }
  }

  /** Per il bridge: un catalogo in mano prima di rispondere. Vero se c'e' (dalla rete o dalla cache). */
  suspend fun ensureCatalog(): Boolean {
    if (_uiState.value.apps.isNotEmpty()) return true
    withTimeoutOrNull(4_000L) { _uiState.first { !it.isLoading } }
    if (_uiState.value.apps.isNotEmpty()) return true
    refreshCatalogInternal(initial = true)
    return _uiState.value.apps.isNotEmpty()
  }

  private suspend fun refreshCatalogInternal(initial: Boolean): Boolean {
    _uiState.update {
      it.copy(
        isLoading = initial && it.apps.isEmpty(),
        isRefreshing = !initial || it.apps.isNotEmpty(),
        error = null,
      )
    }
    val outcome = runCatching { catalogClient.fetchCatalog() }
      .onSuccess { result ->
        val cached = CachedCatalog(
          apps = result.apps,
          storeReleases = result.storeReleases,
          warnings = result.warnings,
          savedAt = System.currentTimeMillis(),
        )
        withContext(Dispatchers.IO) { persistence.writeCachedCatalog(cached) }
        applyCatalog(cached, usingCache = false)
      }
      .onFailure { error ->
        _uiState.update { state ->
          state.copy(
            error = if (state.apps.isEmpty()) error.message ?: "Impossibile caricare il catalogo." else null,
            warning = if (state.apps.isNotEmpty()) "Stai vedendo l’ultimo catalogo disponibile. Tocca per riprovare." else null,
            isUsingCache = state.apps.isNotEmpty(),
          )
        }
      }
    _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
    return outcome.isSuccess
  }

  private suspend fun applyCatalog(catalog: CachedCatalog, usingCache: Boolean) {
    val resolved = withContext(Dispatchers.IO) { resolveInstalledApps(catalog.apps) }
    _uiState.update { state ->
      state.copy(
        apps = resolved,
        storeReleases = catalog.storeReleases,
        isUsingCache = usingCache,
        lastUpdatedAt = catalog.savedAt,
        warning = catalog.warnings.takeIf(List<String>::isNotEmpty)?.joinToString("\n"),
        error = null,
      )
    }
    checkStoreUpdate()
  }

  private fun resolveInstalledApps(entries: List<AppEntry>): List<StoreApp> = resolveStoreApps(entries, appService::installedVersion)

  fun refreshInstalledApps() {
    scope.launch {
      val current = _uiState.value.apps
      if (current.isEmpty()) {
        refreshPrerequisites()
        return@launch
      }
      val resolved = withContext(Dispatchers.IO) { resolveInstalledApps(current.map(StoreApp::entry)) }
      _uiState.update { state ->
        state.copy(
          apps = resolved.map { refreshed ->
            refreshed.copy(downloadState = state.apps.firstOrNull { it.entry.id == refreshed.entry.id }?.downloadState ?: DownloadState.Idle)
          },
          prerequisites = appService.installPrerequisites(),
        )
      }
      checkStoreUpdate()
      // Quello che e' stato appena installato non e' piu' un aggiornamento in attesa, e la
      // notifica che lo annunciava non ha piu' niente da dire.
      notifications.syncWithUpdates(pendingUpdates())
      resumePendingReinstall()
    }
  }

  fun refreshPrerequisites() {
    _uiState.update { it.copy(prerequisites = appService.installPrerequisites()) }
  }

  fun refreshNotificationPermission() {
    _uiState.update { it.copy(notificationsEnabled = notifications.canPost()) }
  }

  fun app(appId: String): StoreApp? = _uiState.value.apps.firstOrNull { it.entry.id == appId }

  fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

  fun setCategory(category: String?) = _uiState.update { it.copy(selectedCategory = category) }

  fun setOwnershipFilter(filter: com.pampa.store.data.OwnershipFilter) = _uiState.update { it.copy(ownershipFilter = filter) }

  fun updatePreferences(transform: (StorePreferences) -> StorePreferences) {
    scope.launch { persistence.updatePreferences(transform) }
  }

  fun openInstallSettings() = appService.openInstallSettings()

  fun openNotificationSettings() = notifications.openSettings()

  fun openApp(packageName: String): Boolean {
    val launched = appService.launch(packageName)
    if (!launched) _uiState.update { it.copy(warning = "L’app non espone un’attività avviabile.") }
    return launched
  }

  // ---------------------------------------------------------------------------------------
  // Installazioni. Tutto passa dalla coda, anche quando la coda ha una voce sola.
  // ---------------------------------------------------------------------------------------

  /** Installa o aggiorna una singola app. `channel` null significa "quello da cui e' installata". */
  fun installApp(appId: String, channel: Channel?) {
    val app = app(appId) ?: return
    val target = channel ?: app.installedChannel ?: enumOrDefault(_uiState.value.preferences.defaultChannel, Channel.STABLE)
    val version = app.entry.versionFor(target) ?: run {
      setInstallState(appId, DownloadState.Error("Nessuna versione disponibile."))
      return
    }
    enqueueAll(listOf(QueueEntry(appId = appId, name = app.entry.name, channel = target, targetVersion = version.version)))
  }

  /** Aggiorna una sola app sul canale da cui e' installata. */
  fun updateOne(appId: String) = installApp(appId, channel = null)

  /**
   * Installa una release **precisa**, scelta dalla cronologia.
   *
   * Se e' piu' vecchia di quella installata non si puo' sovrascrivere: Android riserva al sistema
   * la deroga a `INSTALL_FAILED_VERSION_DOWNGRADE`, quindi il ritorno indietro passa per forza
   * dalla disinstallazione. Qui si mette da parte l'installazione e si chiede ad Android di
   * togliere l'app; il resto lo fa [resumePendingReinstall] al rientro.
   */
  fun installVersion(appId: String, version: VersionInfo) {
    val app = app(appId) ?: return
    if (version.apkAsset.isBlank() || version.releaseTag.isBlank()) {
      setInstallState(appId, DownloadState.Error("Questa release non contiene un APK."))
      return
    }
    val isDowngrade = requiresUninstallFirst(app.installedVersion, version.version)
    if (!isDowngrade) {
      enqueueAll(listOf(QueueEntry(appId = appId, name = app.entry.name, channel = app.installedChannel ?: Channel.STABLE, targetVersion = version.version, explicitVersion = version)))
      return
    }
    _uiState.update {
      it.copy(pendingReinstall = PendingReinstall(appId = appId, appName = app.entry.name, packageName = app.entry.packageName, version = version))
    }
    if (!appService.requestUninstall(app.entry.packageName)) {
      cancelPendingReinstall()
      announce(
        id = "uninstall-failed-$appId",
        title = "Non riesco ad aprire la disinstallazione",
        message = "Rimuovi ${app.entry.name} dalle impostazioni, poi torna qui.",
        tone = StoreEventTone.Error,
      )
    }
  }

  fun cancelPendingReinstall() = _uiState.update { it.copy(pendingReinstall = null) }

  private fun resumePendingReinstall() {
    val pending = _uiState.value.pendingReinstall ?: return
    if (appService.isInstalled(pending.packageName)) {
      cancelPendingReinstall()
      return
    }
    cancelPendingReinstall()
    enqueueAll(listOf(QueueEntry(appId = pending.appId, name = pending.appName, channel = Channel.STABLE, targetVersion = pending.version.version, explicitVersion = pending.version)))
  }

  /**
   * Accoda tutto quello che e' aggiornabile. Lo Store va per ultimo di proposito: installare se
   * stessi termina il processo, e qualunque cosa fosse ancora in coda dietro non partirebbe mai.
   */
  fun updateAll(): Int {
    val state = _uiState.value
    val apps = state.updatableApps.mapNotNull { app ->
      val channel = app.installedChannel ?: return@mapNotNull null
      val version = app.entry.versionFor(channel) ?: return@mapNotNull null
      QueueEntry(appId = app.entry.id, name = app.entry.name, channel = channel, targetVersion = version.version)
    }
    val store = state.storeUpdate?.let { update ->
      QueueEntry(appId = STORE_APP_ID, name = STORE_APP_NAME, channel = enumOrDefault(state.preferences.storeUpdateChannel, Channel.STABLE), targetVersion = update.version.version)
    }
    val all = apps + listOfNotNull(store)
    enqueueAll(all)
    return all.size
  }

  fun installStoreUpdate() {
    val state = _uiState.value
    val update = state.storeUpdate ?: return
    enqueueAll(listOf(QueueEntry(appId = STORE_APP_ID, name = STORE_APP_NAME, channel = enumOrDefault(state.preferences.storeUpdateChannel, Channel.STABLE), targetVersion = update.version.version)))
  }

  fun retryFailed() {
    _uiState.update { it.copy(updateQueue = it.updateQueue.retryFailed()) }
    pumpQueue()
  }

  fun clearQueue() {
    _uiState.update { state ->
      val dropped = state.updateQueue.filterNot(QueueEntry::isRunning).map(QueueEntry::appId)
      state.copy(
        updateQueue = state.updateQueue.clearPending(),
        apps = state.apps.map { app -> if (app.entry.id in dropped) app.copy(downloadState = DownloadState.Idle) else app },
      )
    }
  }

  private fun enqueueAll(candidates: List<QueueEntry>) {
    if (candidates.isEmpty()) return
    _uiState.update { state ->
      val queue = state.updateQueue.enqueue(candidates)
      val queued = queue.filter(QueueEntry::isWaiting).map(QueueEntry::appId).toSet()
      state.copy(
        updateQueue = queue,
        apps = state.apps.map { app -> if (app.entry.id in queued) app.copy(downloadState = DownloadState.Queued) else app },
      )
    }
    pumpQueue()
  }

  private fun pumpQueue() {
    if (queueJob?.isActive == true) return
    queueJob = scope.launch {
      _queueActive.value = true
      startForegroundWork()
      try {
        while (true) {
          val next = _uiState.value.updateQueue.firstOrNull(QueueEntry::isWaiting) ?: break
          runQueueEntry(next)
        }
      } finally {
        _queueActive.value = false
        stopForegroundWork()
      }
    }
  }

  /**
   * Il service in primo piano tiene vivo il processo mentre si scarica: conta quando la coda parte
   * da Aria e lo Store non ha una schermata aperta. Android puo' rifiutarlo (l'app e' in
   * background): allora si va avanti lo stesso, e il chiamante tiene vivo il processo con le sue
   * chiamate di stato.
   */
  private fun startForegroundWork() {
    runCatching {
      val intent = Intent(context, StoreInstallService::class.java)
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }
  }

  private fun stopForegroundWork() {
    runCatching { context.stopService(Intent(context, StoreInstallService::class.java)) }
  }

  private suspend fun runQueueEntry(entry: QueueEntry) {
    val state = _uiState.value
    if (entry.appId == STORE_APP_ID) {
      val update = state.storeUpdate
      if (update == null) {
        setInstallState(entry.appId, DownloadState.Error("L’aggiornamento non è più disponibile."))
        return
      }
      runInstall(entry.appId, "com.pampa.store", update.repository, update.version)
      return
    }
    val app = state.apps.firstOrNull { it.entry.id == entry.appId }
    val version = entry.explicitVersion ?: app?.entry?.versionFor(entry.channel)
    if (app == null || version == null) {
      setInstallState(entry.appId, DownloadState.Error("L’app non è più nel catalogo."))
      return
    }
    runInstall(entry.appId, app.entry.packageName, app.entry.repository, version)
  }

  /**
   * Scarica, verifica e installa, e **attende** l'esito: la coda ha bisogno di sapere quando la
   * voce corrente e' finita per poter cominciare la prossima.
   */
  private suspend fun runInstall(stateId: String, expectedPackageName: String, repository: RepositoryRef, version: VersionInfo) {
    if (!appService.installPrerequisites().canInstallPackages) {
      refreshPrerequisites()
      setInstallState(stateId, DownloadState.Error("Autorizza l’installazione da Pampa Store e riprova."))
      return
    }
    if (version.apkAsset.isBlank() || version.releaseTag.isBlank()) {
      setInstallState(stateId, DownloadState.Error("Questa release non contiene un APK."))
      return
    }
    val safeName = version.apkAsset.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
    val destination = File(appService.downloadsDirectory(), safeName)
    runCatching {
      withContext(Dispatchers.IO) {
        catalogClient.download(repository.releaseAssetUrl(version), destination) { done, total ->
          val progress = if (total > 0) done.toFloat() / total else 0f
          setInstallState(stateId, DownloadState.Downloading(progress, done, total))
        }
        setInstallState(stateId, DownloadState.Verifying("Controllo del pacchetto…"))
        appService.validateApk(destination, expectedPackageName).getOrThrow()
      }
      installer.install(destination, expectedPackageName).collect { event ->
        when (event) {
          InstallEvent.Preparing -> setInstallState(stateId, DownloadState.Verifying("Preparazione…"))
          is InstallEvent.AwaitingUserAction -> setInstallState(stateId, DownloadState.AwaitingUser(event.message))
          is InstallEvent.Installing -> setInstallState(stateId, DownloadState.Installing(event.message))
          InstallEvent.Success -> {
            // L'APK e' gia' dentro la sessione di Android: da qui in poi e' solo peso.
            withContext(Dispatchers.IO) { appService.discardDownload(destination) }
            setInstallState(stateId, DownloadState.Installed(version.version))
            announce(id = "installed-$stateId-${version.version}", title = "Installata la ${version.version}", message = queueName(stateId), tone = StoreEventTone.Success)
            refreshInstalledApps()
          }
          is InstallEvent.Failure -> {
            setInstallState(stateId, DownloadState.Error(event.message))
            announce(id = "failed-$stateId-${version.version}", title = "Installazione non riuscita", message = event.message, tone = StoreEventTone.Error)
          }
        }
      }
    }.onFailure { error ->
      setInstallState(stateId, DownloadState.Error(error.message ?: "Download o installazione non riusciti."))
    }
  }

  /** Un solo posto in cui lo stato di un'installazione si scrive: riga dell'app e voce della coda insieme. */
  private fun setInstallState(appId: String, downloadState: DownloadState) {
    _uiState.update { state ->
      state.copy(
        updateQueue = state.updateQueue.withState(appId, downloadState),
        apps = state.apps.map { app -> if (app.entry.id == appId) app.copy(downloadState = downloadState) else app },
        warning = if (appId == STORE_APP_ID && downloadState is DownloadState.Error) downloadState.message else state.warning,
      )
    }
  }

  /**
   * Per il bridge: aspetta che la voce [appId] arrivi a un esito (installata, in attesa
   * dell'utente, errore), o che scada il tempo. Torna lo stato in cui l'ha lasciata.
   */
  suspend fun awaitOutcome(appId: String, timeoutMillis: Long): DownloadState {
    val settled = withTimeoutOrNull(timeoutMillis) {
      _uiState.first { state ->
        val entry = state.updateQueue.firstOrNull { it.appId == appId } ?: return@first true
        entry.state is DownloadState.Installed || entry.state is DownloadState.Error || entry.state is DownloadState.AwaitingUser
      }
    }
    val state = settled ?: _uiState.value
    return state.updateQueue.firstOrNull { it.appId == appId }?.state
      ?: state.apps.firstOrNull { it.entry.id == appId }?.downloadState
      ?: DownloadState.Idle
  }

  /** Per il bridge: aspetta che la coda si svuoti (o scada il tempo). Vero se ha finito. */
  suspend fun awaitQueueIdle(timeoutMillis: Long): Boolean =
    withTimeoutOrNull(timeoutMillis) { _queueActive.first { !it } } != null

  private fun announce(id: String, title: String, message: String, tone: StoreEventTone) {
    _events.tryEmit(StoreEvent(id = id, title = title, message = message, tone = tone))
  }

  private fun queueName(appId: String): String =
    _uiState.value.updateQueue.firstOrNull { it.appId == appId }?.name
      ?: _uiState.value.apps.firstOrNull { it.entry.id == appId }?.entry?.name
      ?: appId

  /**
   * Ricalcola se lo Store ha una versione piu' recente di quella in esecuzione. `force` e' per il
   * controllo chiesto a mano: spegnere il controllo automatico vuol dire "non cercare da solo",
   * non "non cercare".
   */
  private fun checkStoreUpdate(force: Boolean = false): StoreUpdate? {
    val state = _uiState.value
    if (!force && !state.preferences.autoCheckStoreUpdates) {
      _uiState.update { it.copy(storeUpdate = null) }
      return null
    }
    val update = resolveStoreUpdate(
      releases = state.storeReleases,
      channel = enumOrDefault(state.preferences.storeUpdateChannel, Channel.STABLE),
      installedVersion = BuildConfig.VERSION_NAME,
    )
    _uiState.update { it.copy(storeUpdate = update) }
    return update
  }

  /** Il controllo dello Store chiesto a mano, che **risponde sempre**. */
  fun checkStoreUpdateNow() {
    scope.launch {
      val reachable = refreshCatalogInternal(initial = false)
      val update = checkStoreUpdate(force = true)
      val channel = enumOrDefault(_uiState.value.preferences.storeUpdateChannel, Channel.STABLE)
      when {
        update != null -> announce(id = "store-update-${update.version.version}", title = "C'è la ${update.version.version}", message = "Installala da Aggiornamenti.", tone = StoreEventTone.Info)
        !reachable -> announce(id = "store-check-unreachable", title = "Controllo non riuscito", message = "Il catalogo non risponde. Riprova quando c'è rete.", tone = StoreEventTone.Error)
        else -> announce(id = "store-up-to-date", title = "Pampa Store è aggiornato", message = "La ${BuildConfig.VERSION_NAME} è l'ultima sul canale ${channel.label}.", tone = StoreEventTone.Success)
      }
    }
  }

  /** Tutto quello che una notifica avrebbe motivo di nominare, Store compreso. */
  private fun pendingUpdates(): List<PendingUpdate> {
    val state = _uiState.value
    return state.updatableApps.pendingUpdates() + listOfNotNull(state.storeUpdate?.pendingUpdate(BuildConfig.VERSION_NAME))
  }

  fun clearMessage() = _uiState.update { it.copy(warning = null, error = null) }

  companion object {
    @Volatile private var instance: StoreEngine? = null

    /** Uno per processo: lo chiede per primo chi arriva per primo, la UI o il provider del bridge. */
    fun get(context: Context): StoreEngine = instance ?: synchronized(this) {
      instance ?: StoreEngine(context.applicationContext).also { instance = it }
    }
  }
}
