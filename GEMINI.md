# PampaStore - Project Context

PampaStore is a private, cross-platform App Store client and management system built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It allows users to browse, install, and update Android (APK) and Windows (EXE) applications hosted on GitHub.

## 🚀 Quick Start

### Build and Run
- **Desktop:** `./gradlew :desktopApp:run`
- **Android:** `./gradlew :androidApp:installDebug` (requires a connected device)

### Testing
- **Desktop Tests:** `./gradlew :shared:desktopTest`
- **Android Tests:** `./gradlew :shared:androidUnitTest`

### Packaging
- **Desktop Distribution:** `./gradlew :desktopApp:packageDistributionForCurrentOS`
- **Android APK:** `./gradlew :androidApp:assembleRelease`

## 🏗️ Architecture & Modules

The project follows a modular Kotlin Multiplatform structure:

- **`:shared`**: The core of the application.
    - `commonMain`: Shared UI components (Compose), ViewModels, Repositories, and Data Models.
    - `androidMain` / `desktopMain`: Platform-specific implementations for installers, file pickers, and system services.
- **`:androidApp`**: Android-specific configuration and `MainActivity`.
- **`:desktopApp`**: Desktop-specific configuration and JVM entry point.
- **`plugins/pampa-store-publisher`**: A Python-based **MCP (Model Context Protocol)** server that enables AI agents to manage the store catalog, build releases, and publish to GitHub.

## 🛠️ Tech Stack

- **UI:** Compose Multiplatform with Material 3.
- **Networking:** Ktor Client for catalog fetching and asset downloading.
- **Serialization:** kotlinx.serialization (JSON).
- **Persistence:** Jetpack DataStore (Multiplatform) for user preferences.
- **Images:** Coil3 for async image loading.
- **Concurrency:** Kotlin Coroutines & Flow.

## 📖 Key Concepts

- **Manifests:** The store is driven by `manifest.json` files. The root `manifest.json` describes PampaStore itself. A central store index (usually in a separate repo) points to individual app manifests.
- **Channels:** Supports `STABLE` and `BETA` release channels for every app.
- **Automation:** The `AdminViewModel` and `ReleaseAutomationService` (via the MCP plugin) handle the creation of GitHub releases and manifest updates.

## 📂 Important Files

- `shared/src/commonMain/kotlin/com/pampa/store/data/model/Models.kt`: Central data structures (AppEntry, VersionInfo, etc.).
- `shared/src/commonMain/kotlin/com/pampa/store/ui/App.kt`: Root Composable and navigation logic.
- `manifest.json`: Metadata for the PampaStore application.
- `desktop_tasks.txt`: Development roadmap and pending tasks.

## ⚖️ Development Conventions

- **MVI/MVVM:** Use ViewModels in the `shared` module to manage UI state via `StateFlow`.
- **Platform Abstraction:** Use `expect`/`actual` or interface-based DI for platform-specific features like APK installation or file system access.
- **Theme:** Adheres to a custom "Glass" theme defined in `com.pampa.store.ui.theme`.
- **Automation First:** Prefer using the `pampa-store-publisher` MCP tools for tasks like updating the catalog or publishing new versions.
