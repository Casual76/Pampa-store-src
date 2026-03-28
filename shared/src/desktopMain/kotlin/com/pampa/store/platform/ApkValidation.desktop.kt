package com.pampa.store.platform

import com.pampa.store.data.model.ApkValidationMetadata
import com.pampa.store.data.model.ApkValidationResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createApkValidationService(): ApkValidationService = DesktopApkValidationService()

class DesktopApkValidationService internal constructor(
    private val sdkResolver: AndroidSdkToolsResolver = DefaultAndroidSdkToolsResolver,
    private val commandRunner: ExternalCommandRunner = ProcessBuilderCommandRunner
) : ApkValidationService {

    override suspend fun validate(apkPath: String): ApkValidationResult = withContext(Dispatchers.IO) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            return@withContext ApkValidationResult.Invalid(listOf("APK file not found: $apkPath"))
        }
        if (!apkFile.extension.equals("apk", ignoreCase = true)) {
            return@withContext ApkValidationResult.Invalid(listOf("Selected file is not an APK."))
        }

        val buildTools = sdkResolver.resolve()
            ?: return@withContext ApkValidationResult.Unsupported(
                "Android SDK build-tools non trovati. Configura ANDROID_SDK_ROOT o installa build-tools."
            )

        val badgingResult = commandRunner.run(buildTools.aapt, listOf("dump", "badging", apkFile.absolutePath))
        if (badgingResult.exitCode != 0) {
            return@withContext ApkValidationResult.Invalid(
                issues = listOf("Impossibile leggere il manifest APK con aapt.")
            )
        }

        val xmlTreeResult = commandRunner.run(
            buildTools.aapt,
            listOf("dump", "xmltree", apkFile.absolutePath, "AndroidManifest.xml")
        )
        if (xmlTreeResult.exitCode != 0) {
            return@withContext ApkValidationResult.Invalid(
                issues = listOf("Impossibile ispezionare AndroidManifest.xml dell'APK."),
                metadata = parseBadgingMetadata(badgingResult.output, apkFile.length())
            )
        }

        val signerResult = commandRunner.run(
            buildTools.apkSigner,
            listOf("verify", "--verbose", "--print-certs", apkFile.absolutePath)
        )
        if (signerResult.exitCode != 0 || !signerResult.output.contains("Verifies")) {
            return@withContext ApkValidationResult.Invalid(
                issues = listOf("L'APK non supera la verifica di firma con apksigner."),
                metadata = parseBadgingMetadata(badgingResult.output, apkFile.length())
            )
        }

        val metadata = parseValidationMetadata(
            badgingOutput = badgingResult.output,
            xmlTreeOutput = xmlTreeResult.output,
            signerOutput = signerResult.output,
            fileSizeBytes = apkFile.length()
        )

        val issues = mutableListOf<String>()
        if (metadata.packageName.isBlank()) issues += "Package name mancante o non leggibile."
        if (metadata.versionName.isBlank()) issues += "Version name mancante o non leggibile."
        if (metadata.versionCode.isBlank()) issues += "Version code mancante o non leggibile."
        if (metadata.packageName.endsWith(".debug")) {
            issues += "L'APK appartiene alla variante debug (${metadata.packageName}) e non puo' essere pubblicato nello store."
        }
        if (metadata.versionName.endsWith("-debug")) {
            issues += "L'APK selezionato e' una build debug (${metadata.versionName}). Usa la release firmata."
        }
        if (metadata.signerDn.isBlank() || metadata.signerSha256.isBlank()) {
            issues += "Firma release non rilevata nell'APK."
        }
        if (metadata.debuggable) issues += "L'APK e' debuggable e non puo' essere pubblicato nello store."
        if (metadata.testOnly) issues += "L'APK e' marcato testOnly e non puo' essere installato dagli utenti."
        if (metadata.signerDn.contains("Android Debug", ignoreCase = true)) {
            issues += "L'APK e' firmato con Android Debug e deve essere sostituito con una build release-signed."
        }

        if (issues.isEmpty()) {
            ApkValidationResult.Valid(metadata)
        } else {
            ApkValidationResult.Invalid(issues, metadata)
        }
    }

    private fun parseValidationMetadata(
        badgingOutput: String,
        xmlTreeOutput: String,
        signerOutput: String,
        fileSizeBytes: Long
    ): ApkValidationMetadata {
        val baseMetadata = parseBadgingMetadata(badgingOutput, fileSizeBytes)
        val signerDn = signerOutput.lineSequence()
            .firstOrNull { it.startsWith("Signer #1 certificate DN:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
        val signerSha256 = signerOutput.lineSequence()
            .firstOrNull { it.startsWith("Signer #1 certificate SHA-256 digest:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()

        return baseMetadata.copy(
            signerDn = signerDn,
            signerSha256 = signerSha256,
            installHint = buildInstallHint(
                signerDn = signerDn,
                signerSha256 = signerSha256
            ),
            debuggable = baseMetadata.debuggable || xmlTreeOutput.contains("android:debuggable"),
            testOnly = xmlTreeOutput.contains("android:testOnly")
        )
    }

    private fun parseBadgingMetadata(output: String, fileSizeBytes: Long): ApkValidationMetadata {
        val packageLine = output.lineSequence().firstOrNull { it.startsWith("package:") }.orEmpty()
        val minSdkLine = output.lineSequence().firstOrNull { it.startsWith("minSdkVersion:") }.orEmpty()
        val targetSdkLine = output.lineSequence().firstOrNull { it.startsWith("targetSdkVersion:") }.orEmpty()
        val nativeCodeLine = output.lineSequence().firstOrNull { it.startsWith("native-code:") }.orEmpty()

        return ApkValidationMetadata(
            packageName = extractQuotedValue(packageLine, "name"),
            versionCode = extractQuotedValue(packageLine, "versionCode"),
            versionName = extractQuotedValue(packageLine, "versionName"),
            minSdk = extractSingleQuotedInt(minSdkLine),
            targetSdk = extractSingleQuotedInt(targetSdkLine),
            supportedAbis = extractQuotedValues(nativeCodeLine),
            compressedSizeBytes = fileSizeBytes,
            debuggable = output.contains("application-debuggable"),
            testOnly = false
        )
    }

    private fun extractQuotedValue(line: String, key: String): String {
        val marker = "$key='"
        val start = line.indexOf(marker)
        if (start < 0) return ""
        val valueStart = start + marker.length
        val end = line.indexOf('\'', valueStart)
        if (end < 0) return ""
        return line.substring(valueStart, end)
    }

    private fun extractSingleQuotedInt(line: String): Int? {
        return line.substringAfter('\'', "")
            .substringBefore('\'', "")
            .toIntOrNull()
    }

    private fun extractQuotedValues(line: String): List<String> {
        val results = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val start = line.indexOf('\'', startIndex = searchFrom)
            if (start < 0) break
            val end = line.indexOf('\'', startIndex = start + 1)
            if (end < 0) break
            results += line.substring(start + 1, end)
            searchFrom = end + 1
        }
        return results
    }

    private fun buildInstallHint(
        signerDn: String,
        signerSha256: String
    ): String {
        val hasReleaseSigner = signerDn.isNotBlank() &&
            signerSha256.isNotBlank() &&
            !signerDn.contains("Android Debug", ignoreCase = true)
        if (!hasReleaseSigner) return ""
        return "Se sul telefono era gia' presente una build firmata diversamente, disinstallala una sola volta e reinstalla questa release."
    }
}

internal data class AndroidBuildTools(
    val sdkRoot: File,
    val buildToolsDir: File,
    val aapt: File,
    val apkSigner: File
)

internal interface AndroidSdkToolsResolver {
    fun resolve(): AndroidBuildTools?
}

internal object DefaultAndroidSdkToolsResolver : AndroidSdkToolsResolver {
    override fun resolve(): AndroidBuildTools? {
        val sdkRoot = sequenceOf(
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("ANDROID_HOME"),
            System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk" },
            System.getProperty("user.home")?.let { "$it\\AppData\\Local\\Android\\Sdk" }
        )
            .filterNotNull()
            .map(::File)
            .firstOrNull { it.exists() && it.isDirectory }
            ?: return null

        val buildToolsDir = sdkRoot.resolve("build-tools")
            .takeIf { it.exists() && it.isDirectory }
            ?.listFiles()
            ?.filter { it.isDirectory }
            ?.maxWithOrNull(compareByVersionName { it.name })
            ?: return null

        val aapt = buildToolsDir.resolve("aapt.exe").takeIf { it.exists() } ?: return null
        val apkSigner = buildToolsDir.resolve("apksigner.bat").takeIf { it.exists() }
            ?: buildToolsDir.resolve("apksigner").takeIf { it.exists() }
            ?: return null

        return AndroidBuildTools(
            sdkRoot = sdkRoot,
            buildToolsDir = buildToolsDir,
            aapt = aapt,
            apkSigner = apkSigner
        )
    }

    private fun compareByVersionName(selector: (File) -> String): Comparator<File> {
        return Comparator { left, right ->
            compareVersionStrings(selector(left), selector(right))
        }
    }

    private fun compareVersionStrings(left: String, right: String): Int {
        val leftParts = left.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val leftValue = leftParts.getOrElse(index) { 0 }
            val rightValue = rightParts.getOrElse(index) { 0 }
            if (leftValue != rightValue) {
                return leftValue.compareTo(rightValue)
            }
        }
        return left.compareTo(right)
    }
}

internal data class CommandResult(
    val exitCode: Int,
    val output: String
)

internal fun interface ExternalCommandRunner {
    fun run(executable: File, args: List<String>): CommandResult
}

internal object ProcessBuilderCommandRunner : ExternalCommandRunner {
    override fun run(executable: File, args: List<String>): CommandResult {
        val command = mutableListOf<String>()
        if (executable.extension.equals("bat", ignoreCase = true) ||
            executable.extension.equals("cmd", ignoreCase = true)
        ) {
            command += listOf("cmd.exe", "/c", executable.absolutePath)
        } else {
            command += executable.absolutePath
        }
        command += args

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return CommandResult(exitCode = exitCode, output = output)
    }
}
