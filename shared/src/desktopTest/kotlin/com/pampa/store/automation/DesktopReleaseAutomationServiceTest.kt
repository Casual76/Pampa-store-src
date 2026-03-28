package com.pampa.store.automation

import com.pampa.store.data.model.ApkValidationResult
import com.pampa.store.data.model.ReleaseArtifactKind
import com.pampa.store.data.model.ReleaseAutomationProfile
import com.pampa.store.data.model.ReleaseAutomationTarget
import com.pampa.store.platform.ApkValidationService
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DesktopReleaseAutomationServiceTest {

    @Test
    fun rescanRecoversMissedArtifact() = runBlocking {
        val tempDir = kotlin.io.path.createTempDirectory("automation-rescan").toFile()
        val service = createService(StaticWindowsVersionReader("1.2.0"))
        val events = mutableListOf<ReleaseAutomationEvent>()
        val collectJob = launch {
            service.events.collect { events += it }
        }

        try {
            service.updateProfiles(
                listOf(
                    ReleaseAutomationProfile(
                        id = "store-release",
                        name = "Store release",
                        target = ReleaseAutomationTarget.Store,
                        windowsDirectory = tempDir.absolutePath
                    )
                )
            )
            service.start()
            File(tempDir, "Pampa Store-1.2.0.exe").writeBytes("installer".encodeToByteArray())

            withTimeout(3000) {
                while (events.filterIsInstance<ReleaseAutomationEvent.ArtifactDetected>().isEmpty()) {
                    delay(50)
                }
            }

            val detected = events.filterIsInstance<ReleaseAutomationEvent.ArtifactDetected>().single()
            assertEquals(ReleaseArtifactKind.WINDOWS, detected.artifact.kind)
            assertEquals("1.2.0", detected.artifact.version)
        } finally {
            service.stop()
            collectJob.cancelAndJoin()
        }
    }

    @Test
    fun debounceWaitsForStableFileBeforeEmitting() = runBlocking {
        val tempDir = kotlin.io.path.createTempDirectory("automation-debounce").toFile()
        val targetFile = File(tempDir, "Pampa Store.exe")
        val service = createService(
            windowsVersionReader = StaticWindowsVersionReader("2.0.0"),
            scanIntervalMs = 40,
            stableWindowMs = 120
        )
        val events = mutableListOf<ReleaseAutomationEvent>()
        val collectJob = launch {
            service.events.collect { events += it }
        }

        try {
            service.updateProfiles(
                listOf(
                    ReleaseAutomationProfile(
                        id = "store-release",
                        name = "Store release",
                        target = ReleaseAutomationTarget.Store,
                        windowsDirectory = tempDir.absolutePath
                    )
                )
            )
            service.start()

            targetFile.writeBytes("a".encodeToByteArray())
            delay(50)
            targetFile.writeBytes("ab".encodeToByteArray())
            delay(70)
            assertTrue(events.filterIsInstance<ReleaseAutomationEvent.ArtifactDetected>().isEmpty())

            withTimeout(3000) {
                while (events.filterIsInstance<ReleaseAutomationEvent.ArtifactDetected>().isEmpty()) {
                    delay(40)
                }
            }

            val detectedEvents = events.filterIsInstance<ReleaseAutomationEvent.ArtifactDetected>()
            assertEquals(1, detectedEvents.size)
            assertEquals("2.0.0", detectedEvents.single().artifact.version)
        } finally {
            service.stop()
            collectJob.cancelAndJoin()
        }
    }

    @Test
    fun msiFallsBackToVersionFromFileName() = runBlocking {
        val tempDir = kotlin.io.path.createTempDirectory("automation-msi").toFile()
        val service = createService(StaticWindowsVersionReader(null))
        val events = mutableListOf<ReleaseAutomationEvent>()
        val collectJob = launch {
            service.events.collect { events += it }
        }

        try {
            service.updateProfiles(
                listOf(
                    ReleaseAutomationProfile(
                        id = "store-release",
                        name = "Store release",
                        target = ReleaseAutomationTarget.Store,
                        windowsDirectory = tempDir.absolutePath
                    )
                )
            )
            service.start()
            File(tempDir, "Pampa Store-1.0.8.msi").writeBytes("installer".encodeToByteArray())

            withTimeout(3000) {
                while (events.filterIsInstance<ReleaseAutomationEvent.ArtifactDetected>().isEmpty()) {
                    delay(50)
                }
            }

            val detected = events.filterIsInstance<ReleaseAutomationEvent.ArtifactDetected>().single()
            assertEquals("1.0.8", detected.artifact.version)
        } finally {
            service.stop()
            collectJob.cancelAndJoin()
        }
    }

    private fun createService(
        windowsVersionReader: WindowsVersionReader,
        scanIntervalMs: Long = 60,
        stableWindowMs: Long = 120
    ): DesktopReleaseAutomationService {
        return DesktopReleaseAutomationService(
            apkValidationService = UnsupportedApkValidationService,
            windowsVersionReader = windowsVersionReader,
            dispatcher = Dispatchers.IO,
            scanIntervalMs = scanIntervalMs,
            stableWindowMs = stableWindowMs,
            watchServiceFactory = { ImmediateClosedWatchService() }
        )
    }
}

private object UnsupportedApkValidationService : ApkValidationService {
    override suspend fun validate(apkPath: String): ApkValidationResult {
        return ApkValidationResult.Unsupported("unused")
    }
}

private class StaticWindowsVersionReader(
    private val version: String?
) : WindowsVersionReader {
    override suspend fun readVersion(file: File): String? = version
}

private class ImmediateClosedWatchService : WatchService {
    override fun poll(): WatchKey? = null

    override fun poll(timeout: Long, unit: TimeUnit): WatchKey? = null

    override fun take(): WatchKey {
        throw ClosedWatchServiceException()
    }

    override fun close() = Unit
}
