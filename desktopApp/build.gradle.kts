import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        val appVersion = "1.1.4"
        val desktopJavaHome = System.getenv("DESKTOP_JAVA_HOME")
        if (!desktopJavaHome.isNullOrBlank()) {
            javaHome = desktopJavaHome
        }

        mainClass = "com.pampa.store.MainKt"
        jvmArgs += listOf("-Dpampa.app.version=$appVersion")

        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                }
            }
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Pampa Store"
            packageVersion = appVersion
            description = "Pampa Personal App Store"
            vendor = "Pampa"

            windows {
                val icon = project.file("src/main/resources/icon.ico")
                if (icon.exists()) {
                    iconFile.set(icon)
                }
                menuGroup = "Pampa"
                upgradeUuid = "a8e4c3f2-1b5d-4e6a-9c8f-2d7e3b4a5c6d"
            }
        }
    }
}
