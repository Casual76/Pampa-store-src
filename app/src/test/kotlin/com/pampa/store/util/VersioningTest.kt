package com.pampa.store.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersioningTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(isVersionNewer("1.4.1", "1.4.0"))
        assertFalse(isVersionNewer("1.4.0", "1.4.0"))
        assertFalse(isVersionNewer("1.3.9", "1.4.0"))
    }

    @Test
    fun stableWinsOverPreviewAtSameNumbers() {
        assertTrue(isVersionNewer("1.4.0", "1.4.0-beta.2"))
        assertFalse(isVersionNewer("1.4.0-beta.2", "1.4.0"))
    }
}
