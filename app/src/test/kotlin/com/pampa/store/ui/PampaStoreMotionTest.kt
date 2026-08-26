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
