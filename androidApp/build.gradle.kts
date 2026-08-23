import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import org.gradle.api.GradleException

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun requiredReleaseProperty(propertyName: String, envName: String): String {
    val value = localProperties.getProperty(propertyName)?.trim()
        ?: System.getenv(envName)?.trim()
    if (value.isNullOrBlank()) {
        throw GradleException(
            "Missing required release signing property '$propertyName'. " +
                "Configure it in local.properties or via $envName."
        )
    }
    return value
}

data class ReleaseSigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

fun resolveAndroidSdkDir(): File {
    val configuredPath = sequenceOf(
        localProperties.getProperty("sdk.dir")?.trim(),
        System.getenv("ANDROID_SDK_ROOT")?.trim(),
        System.getenv("ANDROID_HOME")?.trim()
    ).firstOrNull { !it.isNullOrBlank() }
        ?: throw GradleException("Android SDK not found. Configure sdk.dir or ANDROID_SDK_ROOT.")

    val sdkDir = File(configuredPath)
    if (!sdkDir.exists()) {
        throw GradleException("Android SDK directory does not exist: ${sdkDir.absolutePath}")
    }
    return sdkDir
}

fun resolveLatestBuildToolsDir(sdkDir: File): File {
    val buildToolsRoot = sdkDir.resolve("build-tools")
    val buildToolsDir = buildToolsRoot
        .takeIf { it.exists() && it.isDirectory }
        ?.listFiles()
        ?.filter { it.isDirectory }
        ?.maxWithOrNull(Comparator { left, right ->
            compareVersionStrings(left.name, right.name)
        })
        ?: throw GradleException("Android build-tools not found under ${buildToolsRoot.absolutePath}")
    return buildToolsDir
}

fun compareVersionStrings(left: String, right: String): Int {
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

fun isReleaseTaskRequested(): Boolean {
    return gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("release", ignoreCase = true) ||
            taskName.contains("bundle", ignoreCase = true) ||
            taskName.contains("publish", ignoreCase = true)
    }
}

fun resolveReleaseSigningConfigOrNull(): ReleaseSigningConfig? {
    if (!isReleaseTaskRequested()) {
        return null
    }

    val releaseStoreFilePath = requiredReleaseProperty("pampa.release.storeFile", "PAMPA_RELEASE_STORE_FILE")
    val releaseStorePassword = requiredReleaseProperty("pampa.release.storePassword", "PAMPA_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = requiredReleaseProperty("pampa.release.keyAlias", "PAMPA_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = requiredReleaseProperty("pampa.release.keyPassword", "PAMPA_RELEASE_KEY_PASSWORD")

    val releaseStoreFile = rootProject.file(releaseStoreFilePath).also { file ->
        if (!file.exists()) {
            throw GradleException("Release keystore not found at ${file.absolutePath}")
        }
    }

    return ReleaseSigningConfig(
        storeFile = releaseStoreFile,
        storePassword = releaseStorePassword,
        keyAlias = releaseKeyAlias,
        keyPassword = releaseKeyPassword
    )
}

val releaseSigningConfig = resolveReleaseSigningConfigOrNull()
val releaseApk = layout.buildDirectory.file("outputs/apk/release/androidApp-release.apk")
val releaseManifest = layout.buildDirectory.file(
    "intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml"
)
val debugManifest = layout.buildDirectory.file(
    "intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"
)

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}

android {
    namespace = "com.pampa.store"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.pampa.store"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.3.0"
    }

    signingConfigs {
        create("release") {
            releaseSigningConfig?.let { config ->
                storeFile = config.storeFile
                storePassword = config.storePassword
                keyAlias = config.keyAlias
                keyPassword = config.keyPassword
            }
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val verifyReleaseApk by tasks.registering {
    group = "verification"
    description = "Verifica che l'APK release sia firmato, non debuggable e con signer rilevato."

    doLast {
        val buildToolsDir = resolveLatestBuildToolsDir(resolveAndroidSdkDir())
        val apkFile = releaseApk.get().asFile
        val manifestFile = releaseManifest.get().asFile
        if (!apkFile.exists()) {
            throw GradleException("Release APK not found at ${apkFile.absolutePath}")
        }
        if (!manifestFile.exists()) {
            throw GradleException("Release manifest not found at ${manifestFile.absolutePath}")
        }

        val manifestText = manifestFile.readText()
        if (manifestText.contains("android:debuggable=\"true\"")) {
            throw GradleException("Release manifest is debuggable.")
        }
        if (manifestText.contains("android:testOnly=\"true\"")) {
            throw GradleException("Release manifest is testOnly.")
        }

        val apksigner = buildToolsDir.resolve("apksigner.bat").takeIf { it.exists() }
            ?: buildToolsDir.resolve("apksigner").takeIf { it.exists() }
            ?: throw GradleException("apksigner not found under ${buildToolsDir.absolutePath}")

        val verifyCommand = if (apksigner.extension.equals("bat", ignoreCase = true)) {
            listOf("cmd.exe", "/c", apksigner.absolutePath, "verify", "--verbose", "--print-certs", apkFile.absolutePath)
        } else {
            listOf(apksigner.absolutePath, "verify", "--verbose", "--print-certs", apkFile.absolutePath)
        }

        val verifyProcess = ProcessBuilder(verifyCommand)
            .redirectErrorStream(true)
            .start()
        val verifyOutput = verifyProcess.inputStream.bufferedReader().use { it.readText() }
        val verifyExitCode = verifyProcess.waitFor()
        val signerDetected = verifyOutput.contains("Signer #1") ||
            verifyOutput.contains("Number of signers: 1") ||
            verifyOutput.contains("V2 Signer:")
        if (verifyExitCode != 0 || !verifyOutput.contains("Verifies") || !signerDetected) {
            throw GradleException("Release APK signature verification failed.\n$verifyOutput")
        }
    }
}

val verifyDebugApplicationId by tasks.registering {
    group = "verification"
    description = "Controlla che la build debug usi il package suffix .debug."
    dependsOn("assembleDebug")

    doLast {
        val manifestFile = debugManifest.get().asFile
        if (!manifestFile.exists()) {
            throw GradleException("Debug manifest not found at ${manifestFile.absolutePath}")
        }
        val manifestText = manifestFile.readText()
        if (!manifestText.contains("package=\"com.pampa.store.debug\"")) {
            throw GradleException("Debug build must use package com.pampa.store.debug.")
        }
    }
}

afterEvaluate {
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        finalizedBy(verifyReleaseApk)
    }
}
