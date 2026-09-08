import java.util.Properties
import org.gradle.api.GradleException

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::exists)?.inputStream()?.use(::load)
}

fun releaseProperty(propertyName: String, envName: String): String? =
    localProperties.getProperty(propertyName)?.trim()?.takeIf(String::isNotBlank)
        ?: System.getenv(envName)?.trim()?.takeIf(String::isNotBlank)

val releaseStoreFile = releaseProperty("pampa.release.storeFile", "PAMPA_RELEASE_STORE_FILE")
val releaseStorePassword = releaseProperty("pampa.release.storePassword", "PAMPA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseProperty("pampa.release.keyAlias", "PAMPA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseProperty("pampa.release.keyPassword", "PAMPA_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pampa.store"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pampa.store"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "1.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(checkNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // local.properties is intentionally untracked and can contain Windows-native paths.
        disable += "PropertyEscape"
    }
}

dependencies {
    implementation(project(":engine-foundation"))
    implementation(project(":engine-ui"))
    implementation(project(":engine-storage"))
    implementation(project(":engine-net"))
    implementation(project(":engine-config"))
    implementation(project(":engine-update"))
    // I tipi dei tool e il bridge: lo Store espone i suoi strumenti a PampAI/Aria.
    implementation(project(":engine-ai"))
    implementation(project(":engine-ai-bridge"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Verifica che la configurazione di firma release sia completa."
    doLast {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Configura pampa.release.storeFile, storePassword, keyAlias e keyPassword " +
                    "oppure le equivalenti variabili PAMPA_RELEASE_*.",
            )
        }
        val keyStore = rootProject.file(checkNotNull(releaseStoreFile))
        if (!keyStore.exists()) {
            throw GradleException("Keystore release non trovato: ${keyStore.absolutePath}")
        }
    }
}
