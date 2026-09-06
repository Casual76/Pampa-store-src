package com.pampa.store.pampai

import android.content.Context
import com.pampa.store.BuildConfig
import com.pampa.store.data.Channel
import com.pampa.store.data.DownloadState
import com.pampa.store.data.STORE_APP_ID
import com.pampa.store.data.StoreApp
import com.pampa.store.data.enumOrDefault
import com.pampa.store.data.versionFor
import com.pampa.store.engine.StoreEngine
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Args.str
import dev.antigravity.fluidengine.ai.tools.ConfirmationText
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import dev.antigravity.fluidengine.ai.tools.ToolText
import java.text.Normalizer
import kotlinx.serialization.json.JsonObject

/** Cosa un tool dello Store ha in mano: l'engine (catalogo, coda) e il contesto Android. */
class StoreToolContext(val engine: StoreEngine, val context: Context)

/** L'unico gruppo: lo Store e' un'app piccola, e per il router e' una porta sola. */
enum class StoreToolGroup(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  STORE("store", "store", "il Pampa Store: catalogo delle app Pampa, dettaglio e versioni, aggiornamenti disponibili, installa o aggiorna, apri un'app, canale stabile/beta, coda delle installazioni"),
}

private fun normalize(text: String): String =
  Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

/** Trova un'app per id, nome o pacchetto, tollerando abbreviazioni ("cv", "meteo", "bus"). */
internal fun StoreEngine.findApp(query: String): StoreApp? {
  val q = normalize(query)
  val apps = uiState.value.apps
  val aliases = mapOf("cv" to "classeviva", "registro" to "classeviva", "meteo" to "weather", "bus" to "transit", "musica" to "fluidify", "convertitore" to "convert")
  val wanted = aliases[q] ?: q
  return apps.firstOrNull { normalize(it.entry.id) == wanted || normalize(it.entry.name) == wanted || it.entry.packageName.lowercase() == q }
    ?: apps.firstOrNull { normalize(it.entry.name).contains(wanted) || normalize(it.entry.id).contains(wanted) }
    ?: apps.firstOrNull { wanted.split(" ").all { w -> normalize(it.entry.name).contains(w) } }
}

private fun StoreApp.line(): String = buildString {
  append(entry.name)
  append(" (").append(entry.id).append(")")
  append(" · ").append(entry.category)
  append(" · ")
  append(
    when {
      installedVersion == null -> "non installata" + (availableVersion?.let { " · disponibile $it" } ?: "")
      hasUpdate -> "installata $installedVersion · aggiornamento $availableVersion"
      else -> "installata $installedVersion · aggiornata"
    },
  )
  installedChannel?.let { append(" · canale ${it.label.lowercase()}") }
}

private fun DownloadState.words(): String = when (this) {
  DownloadState.Idle -> "ferma"
  DownloadState.Queued -> "in coda"
  is DownloadState.Downloading -> "download ${(progress * 100).toInt()}%"
  is DownloadState.Verifying -> message
  is DownloadState.AwaitingUser -> "in attesa della conferma dell'utente"
  is DownloadState.Installing -> message
  is DownloadState.Installed -> "installata $version"
  is DownloadState.Error -> "errore: $message"
}

private const val NO_CATALOG = "il catalogo non e' disponibile (niente rete e nessuna copia): apri il Pampa Store e riprova"

class CatalogoTool : AiTool<StoreToolContext> {
  override val name = "catalogo"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "Le app del Pampa Store: nome, id, categoria, se e' installata, se ha un aggiornamento. Con un filtro per nome o categoria."
  override val parameters = Schema.obj(mapOf("filtro" to Schema.str("parole nel nome o nella categoria (facoltativo)")))

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    if (!ctx.engine.ensureCatalog()) return ToolOutput.error(NO_CATALOG)
    val filter = args.str("filtro")?.let(::normalize)
    val apps = ctx.engine.uiState.value.apps.filter { filter == null || normalize(it.entry.name).contains(filter) || normalize(it.entry.category).contains(filter) || normalize(it.entry.id).contains(filter) }
    if (apps.isEmpty()) return ToolText.output { line("app", if (filter != null) "nessuna che corrisponda a \"$filter\"" else "il catalogo e' vuoto") }
    return ToolText.output(3_000) {
      line("app nel catalogo", apps.size)
      apps.take(40).forEach { line(it.line()) }
      line("store", "Pampa Store ${BuildConfig.VERSION_NAME}" + (ctx.engine.uiState.value.storeUpdate?.let { " · aggiornamento ${it.version.version}" } ?: ""))
    }
  }
}

class AppTool : AiTool<StoreToolContext> {
  override val name = "app"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "Il dettaglio di un'app del catalogo: descrizione, versioni stabile e beta con data, novita' dell'ultima versione, cronologia, stato dell'installazione."
  override val parameters = Schema.obj(mapOf("app" to Schema.str("nome, id o pacchetto dell'app")), required = listOf("app"))

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    if (!ctx.engine.ensureCatalog()) return ToolOutput.error(NO_CATALOG)
    val query = args.str("app") ?: return ToolOutput.error("manca l'app")
    val app = ctx.engine.findApp(query) ?: return ToolOutput.error("nessuna app \"$query\" nel catalogo; ci sono: ${ctx.engine.uiState.value.apps.joinToString { it.entry.name }}")
    val e = app.entry
    return ToolText.output(3_000) {
      line("app", "${e.name} (${e.id})")
      line("pacchetto", e.packageName)
      line("categoria", e.category)
      line("sviluppatore", e.developer)
      line("descrizione", e.description.take(400))
      line("stato", app.line().substringAfter(" · ").substringAfter(" · "))
      e.stable?.let { line("stabile", "${it.version} (${it.releaseDate})") }
      e.beta?.let { line("beta", "${it.version} (${it.releaseDate})") }
      (e.stable ?: e.beta)?.changelog?.takeIf { it.isNotBlank() }?.let { line("novita'", it.take(600)) }
      if (e.history.isNotEmpty()) line("cronologia", e.history.take(4).joinToString("; ") { "${it.version} (${it.releaseDate})" })
      if (app.downloadState != DownloadState.Idle) line("installazione", app.downloadState.words())
    }
  }
}

class AggiornamentiTool : AiTool<StoreToolContext> {
  override val name = "aggiornamenti"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "Gli aggiornamenti disponibili per le app installate (e per lo Store stesso). Per \"ci sono aggiornamenti?\"."
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    if (!ctx.engine.ensureCatalog()) return ToolOutput.error(NO_CATALOG)
    val state = ctx.engine.uiState.value
    val updates = state.updatableApps
    val store = state.storeUpdate
    if (updates.isEmpty() && store == null) return ToolText.output { line("aggiornamenti", "nessuno: e' tutto aggiornato (${state.installedCount} app installate)") }
    return ToolText.output {
      line("aggiornamenti disponibili", updates.size + (if (store != null) 1 else 0))
      updates.forEach { line("${it.entry.name}: ${it.installedVersion} → ${it.availableVersion}" + (it.entry.versionFor(it.installedChannel ?: Channel.STABLE)?.changelog?.takeIf { c -> c.isNotBlank() }?.let { c -> " · ${c.take(160)}" } ?: "")) }
      store?.let { line("Pampa Store: ${BuildConfig.VERSION_NAME} → ${it.version.version}") }
      if (!state.prerequisites.canInstallPackages) line("nota", "l'installazione da Pampa Store non e' autorizzata: l'utente la abilita dalle impostazioni (lo Store lo chiede)")
    }
  }
}

class InstallaTool : AiTool<StoreToolContext> {
  override val name = "installa"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "Installa o aggiorna un'app del catalogo (scarica, verifica e chiede ad Android di installare). Chiede conferma; puo' durare."
  override val parameters = Schema.obj(mapOf("app" to Schema.str("nome o id dell'app"), "canale" to Schema.str("stabile o beta (default: quello attuale)", enum = listOf("stabile", "beta"))), required = listOf("app"))
  override val needsConfirmation = true
  override val longRunning = true
  override val isAction = true

  override suspend fun describe(args: JsonObject, ctx: StoreToolContext): ConfirmationText? {
    ctx.engine.ensureCatalog()
    val app = args.str("app")?.let { ctx.engine.findApp(it) } ?: return null
    val channel = channelOf(args, app)
    val version = app.entry.versionFor(channel)?.version ?: "?"
    return ConfirmationText(if (app.installedVersion == null) "Installare ${app.entry.name} $version?" else "Aggiornare ${app.entry.name} alla $version?", "Canale ${channel.label.lowercase()} · dal Pampa Store")
  }

  private fun channelOf(args: JsonObject, app: StoreApp): Channel = when (args.str("canale")) {
    "beta" -> Channel.BETA
    "stabile" -> Channel.STABLE
    else -> app.installedChannel ?: Channel.STABLE
  }

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    if (!ctx.engine.ensureCatalog()) return ToolOutput.error(NO_CATALOG)
    val app = args.str("app")?.let { ctx.engine.findApp(it) } ?: return ToolOutput.error("app non trovata nel catalogo")
    val channel = channelOf(args, app)
    val version = app.entry.versionFor(channel) ?: return ToolOutput.error("${app.entry.name} non ha una versione sul canale ${channel.label.lowercase()}")
    if (app.installedVersion == version.version) return ToolText.output { line("fatto", "${app.entry.name} e' gia' alla ${version.version}") }
    ctx.engine.installApp(app.entry.id, channel)
    return outcome(ctx, app.entry.id, app.entry.name, version.version)
  }
}

/** Il testo dell'esito di una voce della coda, aspettandola entro il tempo del lavoro lungo. */
internal suspend fun outcome(ctx: StoreToolContext, appId: String, name: String, version: String): ToolOutput {
  return when (val state = ctx.engine.awaitOutcome(appId, timeoutMillis = 170_000L)) {
    is DownloadState.Installed -> ToolText.output { line("fatto", "$name installata alla ${state.version}") }
    is DownloadState.AwaitingUser -> ToolText.output {
      line("stato", "$name $version scaricata e verificata: Android chiede la conferma dell'installazione")
      line("nota", "se la finestra di conferma non si vede, l'utente apre il Pampa Store e la trova nella scheda Aggiornamenti")
    }
    is DownloadState.Error -> ToolOutput.error("${state.message} (app: $name)")
    else -> ToolText.output { line("stato", "$name $version: ${state.words()}, continua nel Pampa Store") }
  }
}

class AggiornaTuttoTool : AiTool<StoreToolContext> {
  override val name = "aggiorna_tutto"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "Aggiorna tutte le app che hanno un aggiornamento (lo Store per ultimo). Chiede conferma; puo' durare."
  override val parameters = Schema.obj(emptyMap())
  override val needsConfirmation = true
  override val longRunning = true
  override val isAction = true

  override suspend fun describe(args: JsonObject, ctx: StoreToolContext): ConfirmationText? {
    ctx.engine.ensureCatalog()
    val state = ctx.engine.uiState.value
    val count = state.updatableApps.size + (if (state.storeUpdate != null) 1 else 0)
    if (count == 0) return null
    return ConfirmationText("Aggiornare $count app?", state.updatableApps.joinToString(", ") { it.entry.name } + (state.storeUpdate?.let { ", Pampa Store" } ?: ""))
  }

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    if (!ctx.engine.ensureCatalog()) return ToolOutput.error(NO_CATALOG)
    val count = ctx.engine.updateAll()
    if (count == 0) return ToolText.output { line("fatto", "niente da aggiornare") }
    val finished = ctx.engine.awaitQueueIdle(170_000L)
    val state = ctx.engine.uiState.value
    return ToolText.output {
      line("accodate", count)
      state.updateQueue.forEach { line("${it.name}: ${it.state.words()}") }
      if (!finished) line("nota", "la coda continua nel Pampa Store")
      if (state.updateQueue.any { it.state is DownloadState.AwaitingUser }) line("nota", "Android chiede la conferma per ogni installazione: l'utente la da' nella finestra che si apre (o dal Pampa Store)")
    }
  }
}

class ApriAppTool : AiTool<StoreToolContext> {
  override val name = "apri_app"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "Apre un'app del catalogo, se installata."
  override val parameters = Schema.obj(mapOf("app" to Schema.str("nome o id dell'app")), required = listOf("app"))
  override val isAction = true

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    if (!ctx.engine.ensureCatalog()) return ToolOutput.error(NO_CATALOG)
    val app = args.str("app")?.let { ctx.engine.findApp(it) } ?: return ToolOutput.error("app non trovata nel catalogo")
    if (app.installedVersion == null) return ToolOutput.error("${app.entry.name} non e' installata: si puo' installare con installa")
    return if (ctx.engine.openApp(app.entry.packageName)) ToolText.output { line("fatto", "aperta ${app.entry.name}") } else ToolOutput.error("${app.entry.name} non si apre da qui")
  }
}

class CanaleTool : AiTool<StoreToolContext> {
  override val name = "canale"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "Legge o imposta il canale predefinito delle nuove installazioni (stabile o beta) e quello degli aggiornamenti dello Store."
  override val parameters = Schema.obj(mapOf("canale" to Schema.str("il nuovo canale predefinito (senza: dice quello attuale)", enum = listOf("stabile", "beta"))))
  override val isAction = true

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    val prefs = ctx.engine.uiState.value.preferences
    val requested = when (args.str("canale")) { "beta" -> Channel.BETA; "stabile" -> Channel.STABLE; else -> null }
    if (requested != null) ctx.engine.updatePreferences { it.copy(defaultChannel = requested.name) }
    return ToolText.output {
      line("canale predefinito", (requested ?: enumOrDefault(prefs.defaultChannel, Channel.STABLE)).label.lowercase() + (if (requested != null) " (impostato)" else ""))
      line("canale dello Store", enumOrDefault(prefs.storeUpdateChannel, Channel.STABLE).label.lowercase())
      line("nota", "il canale di un'app gia' installata si cambia installandola dall'altro canale (installa con canale=beta)")
    }
  }
}

class CodaTool : AiTool<StoreToolContext> {
  override val name = "coda"
  override val group: AiToolGroup = StoreToolGroup.STORE
  override val description = "La coda delle installazioni: cosa sta scaricando, cosa aspetta, cosa e' fallito."
  override val parameters = Schema.obj(emptyMap())

  override suspend fun run(args: JsonObject, ctx: StoreToolContext): ToolOutput {
    val queue = ctx.engine.uiState.value.updateQueue
    if (queue.isEmpty()) return ToolText.output { line("coda", "vuota") }
    return ToolText.output {
      line("coda", queue.size)
      queue.forEach { line("${it.name} ${it.targetVersion}${if (it.appId == STORE_APP_ID) " (lo Store)" else ""}: ${it.state.words()}") }
    }
  }
}

object StoreTools {
  fun registry(): ToolRegistry<StoreToolContext> = ToolRegistry(
    tools = listOf(CatalogoTool(), AppTool(), AggiornamentiTool(), InstallaTool(), AggiornaTuttoTool(), ApriAppTool(), CanaleTool(), CodaTool()),
    groups = StoreToolGroup.entries,
    actionGroup = null,
  )
}
