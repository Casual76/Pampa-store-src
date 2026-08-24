pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PampaStore"

include(":app")

// fluid-engine (inizio)
val engineDir = file("engine")
check(engineDir.exists()) {
    "Fluid Engine non trovato. Esegui git submodule update --init --recursive."
}

listOf(
    "engine-foundation",
    "engine-ui",
    "engine-storage",
    "engine-net",
    "engine-config",
    "engine-update",
).forEach { moduleName ->
    include(":$moduleName")
    project(":$moduleName").projectDir = engineDir.resolve(moduleName)
}
// fluid-engine (fine)
