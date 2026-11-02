package com.pampa.store.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'ordine delle schede, che e' il `peer` di `FluidNavHost`: il verso del passo laterale fra due schede.
 * Il resto del movimento (il dettaglio da destra, il back sotto il dito) e' dell'engine e non si prova qui.
 */
class PampaStoreMotionTest {
    @Test
    fun topLevelRoutesMoveInTabOrder() {
        assertEquals(1, tabStep("home", "catalog"))
        assertEquals(-1, tabStep("more", "home"))
    }

    /**
     * La scheda Aggiornamenti sta fra Catalogo e Altro, e il verso deve saperlo: una pagina che
     * entra dal lato sbagliato dice che sei tornato indietro quando stai andando avanti.
     */
    @Test
    fun updatesSitsBetweenCatalogAndMore() {
        assertEquals(1, tabStep("catalog", "updates"))
        assertEquals(1, tabStep("updates", "more"))
        assertEquals(-1, tabStep("updates", "home"))
    }

    @Test
    fun theDetailIsNotATabSoNoSideStep() {
        assertEquals(0, tabStep("catalog", "detail/{appId}"))
        assertEquals(0, tabStep("detail/{appId}", "catalog"))
    }

    @Test
    fun aTabWithItselfOrAnUnknownRouteDoesNotStep() {
        assertEquals(0, tabStep("home", "home"))
        assertEquals(0, tabStep(null, "home"))
        assertEquals(0, tabStep("home", "boh"))
    }
}
