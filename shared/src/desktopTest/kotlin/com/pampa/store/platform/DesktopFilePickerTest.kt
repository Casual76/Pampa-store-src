package com.pampa.store.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFilePickerTest {

    @Test
    fun acceptsOnlyConfiguredExtensions() {
        val allowed = normalizeExtensions(listOf("apk", ".msi"))

        assertTrue(isAllowedExtension("store-release.apk", allowed))
        assertTrue(isAllowedExtension("store-installer.MSI", allowed))
        assertFalse(isAllowedExtension("notes.txt", allowed))
        assertFalse(isAllowedExtension("archive.zip", allowed))
    }
}
