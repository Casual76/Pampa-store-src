package com.pampa.store.pampai

import com.pampa.store.engine.StoreEngine
import dev.antigravity.fluidengine.ai.bridge.AiToolHostProvider
import dev.antigravity.fluidengine.ai.bridge.ReadyState
import dev.antigravity.fluidengine.ai.bridge.RemoteCall
import dev.antigravity.fluidengine.ai.tools.ToolRegistry

/**
 * Lo Store visto da PampAI/Aria: il `ContentProvider` del bridge (permesso `signature`, stessa
 * chiave dello store) con gli otto strumenti del catalogo e della coda, eseguiti qui, nello stesso
 * processo e sullo stesso [StoreEngine] della UI.
 */
class StoreToolHostProvider : AiToolHostProvider<StoreToolContext>() {

  private val registry by lazy { StoreTools.registry() }

  override fun registry(): ToolRegistry<StoreToolContext> = registry

  override suspend fun context(call: RemoteCall): StoreToolContext = StoreToolContext(StoreEngine.get(context!!), context!!)

  override fun domain(): String = "store"

  override fun appLabel(): String = "Pampa Store"

  override fun routerHint(): String = "il Pampa Store: catalogo delle app Pampa, aggiornamenti disponibili, installazioni e versioni, canale stabile o beta"

  override fun vocabulary(): List<String> = context?.let { StoreEngine.get(it).uiState.value.apps.map { app -> app.entry.name } }.orEmpty()

  override fun ready(): ReadyState {
    // L'engine parte da qui se la UI non l'ha ancora fatto: il catalogo arriva dalla cache subito, dalla rete poco dopo.
    context?.let { StoreEngine.get(it) }
    return ReadyState(true)
  }

  override fun partsAuthority(): String? = null
}
