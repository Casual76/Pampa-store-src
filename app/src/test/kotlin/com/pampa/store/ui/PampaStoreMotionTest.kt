package com.pampa.store.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PampaStoreMotionTest {
    @Test
    fun topLevelRoutesMoveInTabOrder() {
        assertEquals(
            StoreRouteMotionDecision(StoreRouteMotionKind.TopLevelSwitch, direction = 1),
            decideStoreRouteMotion("home", "catalog"),
        )
        assertEquals(
            StoreRouteMotionDecision(StoreRouteMotionKind.TopLevelSwitch, direction = -1),
            decideStoreRouteMotion("more", "home"),
        )
    }

    /**
     * La scheda Aggiornamenti sta fra Catalogo e Altro, e la direzione deve saperlo: una pagina che
     * entra dal lato sbagliato dice che sei tornato indietro quando stai andando avanti.
     */
    @Test
    fun updatesSitsBetweenCatalogAndMore() {
        assertEquals(
            StoreRouteMotionDecision(StoreRouteMotionKind.TopLevelSwitch, direction = 1),
            decideStoreRouteMotion("catalog", "updates"),
        )
        assertEquals(
            StoreRouteMotionDecision(StoreRouteMotionKind.TopLevelSwitch, direction = 1),
            decideStoreRouteMotion("updates", "more"),
        )
        assertEquals(
            StoreRouteMotionDecision(StoreRouteMotionKind.TopLevelSwitch, direction = -1),
            decideStoreRouteMotion("updates", "home"),
        )
    }

    @Test
    fun detailOpenAndPopUseHierarchicalMotion() {
        assertEquals(
            StoreRouteMotionKind.Detail,
            decideStoreRouteMotion("catalog", "detail/{appId}").kind,
        )
        assertEquals(
            StoreRouteMotionKind.Detail,
            decideStoreRouteMotion("detail/{appId}", "catalog").kind,
        )
    }
}
