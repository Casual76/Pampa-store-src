package com.pampa.store.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersioningTest {

    @Test
    fun comparesNumericSegmentsCorrectly() {
        assertTrue(isVersionNewer("1.0.10", "1.0.2"))
        assertFalse(isVersionNewer("1.0.2", "1.0.10"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertEquals(0, compareVersions("1.0.0", "1.0.0"))
        assertFalse(isVersionNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun releaseBeatsPreRelease() {
        assertTrue(isVersionNewer("1.0.0", "1.0.0-beta"))
        assertFalse(isVersionNewer("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun preReleaseNumericIdentifiersAreOrdered() {
        assertTrue(isVersionNewer("1.0.0-beta.2", "1.0.0-beta.1"))
        assertFalse(isVersionNewer("1.0.0-beta.1", "1.0.0-beta.2"))
    }
}
