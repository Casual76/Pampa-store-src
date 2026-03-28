package com.pampa.store.platform

import com.pampa.store.data.model.ApkValidationResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DesktopApkValidationServiceTest {

    @Test
    fun validApkIsPublishable() = runTest {
        val apkFile = createTempApk()
        val service = DesktopApkValidationService(
            sdkResolver = FakeSdkToolsResolver,
            commandRunner = FakeCommandRunner(
                badgingOutput = """
                    package: name='com.example.release' versionCode='42' versionName='1.2.3'
                    sdkVersion:'24'
                    targetSdkVersion:'35'
                    native-code: 'arm64-v8a' 'armeabi-v7a'
                """.trimIndent(),
                xmlTreeOutput = "N: android=http://schemas.android.com/apk/res/android",
                signerOutput = """
                    Verifies
                    Signer #1 certificate DN: CN=Release Signer
                    Signer #1 certificate SHA-256 digest: abc123
                """.trimIndent()
            )
        )

        val result = service.validate(apkFile.absolutePath)

        val valid = assertIs<ApkValidationResult.Valid>(result)
        assertEquals("com.example.release", valid.metadata.packageName)
        assertEquals("1.2.3", valid.metadata.versionName)
        assertEquals("42", valid.metadata.versionCode)
        assertEquals("CN=Release Signer", valid.metadata.signerDn)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), valid.metadata.supportedAbis)
        assertTrue(valid.metadata.installHint.contains("disinstallala"))
    }

    @Test
    fun debuggableAndAndroidDebugApkIsRejected() = runTest {
        val apkFile = createTempApk()
        val service = DesktopApkValidationService(
            sdkResolver = FakeSdkToolsResolver,
            commandRunner = FakeCommandRunner(
                badgingOutput = """
                    package: name='com.example.debug' versionCode='7' versionName='0.2.4-debug'
                    sdkVersion:'24'
                    targetSdkVersion:'36'
                    application-debuggable
                """.trimIndent(),
                xmlTreeOutput = "A: android:debuggable(0x0101000f)=0xffffffff",
                signerOutput = """
                    Verifies
                    Signer #1 certificate DN: CN=Android Debug,O=Android,C=US
                    Signer #1 certificate SHA-256 digest: def456
                """.trimIndent()
            )
        )

        val result = service.validate(apkFile.absolutePath)

        val invalid = assertIs<ApkValidationResult.Invalid>(result)
        assertTrue(invalid.issues.any { it.contains("debuggable") })
        assertTrue(invalid.issues.any { it.contains("variante debug") })
        assertTrue(invalid.issues.any { it.contains("build debug") })
        assertTrue(invalid.issues.any { it.contains("Android Debug") })
    }

    @Test
    fun testOnlyApkIsRejected() = runTest {
        val apkFile = createTempApk()
        val service = DesktopApkValidationService(
            sdkResolver = FakeSdkToolsResolver,
            commandRunner = FakeCommandRunner(
                badgingOutput = """
                    package: name='com.example.testonly' versionCode='9' versionName='1.0.0'
                    sdkVersion:'24'
                    targetSdkVersion:'35'
                """.trimIndent(),
                xmlTreeOutput = "A: android:testOnly(0x01010272)=0xffffffff",
                signerOutput = """
                    Verifies
                    Signer #1 certificate DN: CN=Release Signer
                    Signer #1 certificate SHA-256 digest: ghi789
                """.trimIndent()
            )
        )

        val result = service.validate(apkFile.absolutePath)

        val invalid = assertIs<ApkValidationResult.Invalid>(result)
        assertTrue(invalid.issues.any { it.contains("testOnly") })
    }

    @Test
    fun apkWithoutSignerMetadataIsRejected() = runTest {
        val apkFile = createTempApk()
        val service = DesktopApkValidationService(
            sdkResolver = FakeSdkToolsResolver,
            commandRunner = FakeCommandRunner(
                badgingOutput = """
                    package: name='com.example.release' versionCode='10' versionName='2.0.0'
                    sdkVersion:'24'
                    targetSdkVersion:'35'
                """.trimIndent(),
                xmlTreeOutput = "N: android=http://schemas.android.com/apk/res/android",
                signerOutput = "Verifies"
            )
        )

        val result = service.validate(apkFile.absolutePath)

        val invalid = assertIs<ApkValidationResult.Invalid>(result)
        assertTrue(invalid.issues.any { it.contains("Firma release") })
    }

    private fun createTempApk(): File {
        return kotlin.io.path.createTempFile("pampastore-", ".apk").toFile().apply {
            writeBytes(byteArrayOf(0x50.toByte(), 0x4b.toByte(), 0x03.toByte(), 0x04.toByte()))
        }
    }
}

private object FakeSdkToolsResolver : AndroidSdkToolsResolver {
    override fun resolve(): AndroidBuildTools {
        return AndroidBuildTools(
            sdkRoot = File("C:/Android/Sdk"),
            buildToolsDir = File("C:/Android/Sdk/build-tools/35.0.0"),
            aapt = File("aapt.exe"),
            apkSigner = File("apksigner.bat")
        )
    }
}

private class FakeCommandRunner(
    private val badgingOutput: String,
    private val xmlTreeOutput: String,
    private val signerOutput: String
) : ExternalCommandRunner {
    override fun run(executable: File, args: List<String>): CommandResult {
        return when {
            args.take(2) == listOf("dump", "badging") -> CommandResult(0, badgingOutput)
            args.take(2) == listOf("dump", "xmltree") -> CommandResult(0, xmlTreeOutput)
            args.take(2) == listOf("verify", "--verbose") -> CommandResult(0, signerOutput)
            else -> error("Unexpected command: ${executable.name} ${args.joinToString(" ")}")
        }
    }
}
