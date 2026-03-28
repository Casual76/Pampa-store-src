package com.pampa.store.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilePickerAndroidTest {

    @Test
    fun cleanupRemovesOnlyStaleTempFiles() {
        val tempDir = Files.createTempDirectory("pampa-picker-test").toFile()
        val staleFile = File(tempDir, "stale.tmp").apply {
            writeText("old")
            setLastModified(1_000L)
        }
        val freshFile = File(tempDir, "fresh.tmp").apply {
            writeText("new")
            setLastModified(9_000L)
        }

        cleanupStalePickerTempFiles(
            tempDir = tempDir,
            nowMillis = 10_000L,
            maxAgeMillis = 5_000L
        )

        assertFalse(staleFile.exists())
        assertTrue(freshFile.exists())
        tempDir.deleteRecursively()
    }
}
