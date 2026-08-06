# VVF Smart Manager 📱

A highly polished, feature-rich, and security-focused Android application designed to scan storage, manage files, secure sensitive documents, detect AI duplicates, and perform smart local searching. Built entirely with **Kotlin** and **Jetpack Compose** following modern Material Design 3 guidelines.

---

## 🚀 Key Features

### 📂 Smart File Manager & Scanner
- **Dynamic Storage Visualizer**: Beautiful circular dashboard showing used and free storage space by file types (Images, Videos, Audios, Documents).
- **Deep Physical Scanning**: Multi-threaded scanner that indexes local files and synchronizes metadata into a secure Room database.
- **Background Indexer**: Runs quiet, system-friendly background tasks using Android's **WorkManager** to keep file indexing up-to-date.

### 🔒 Secure Encrypted Vault
- **Hardware-Backed Cryptography**: Protects private files using AES-GCM encryption with keys generated inside the secure Android **Keystore System**.
- **Visual Privacy Guard**: Completely separate hidden vault screen with PIN protection, custom keyboard layout, and visual feedback for importing/exporting private documents.

### 🧠 AI Duplicate Cleaner
- **TFLite-Powered Scanning**: Leverages machine learning models (`mobile_clip_embedding`) locally on-device to compute high-dimensional vectors and identify semantic duplicates.
- **Memory-Safe Fallback**: Includes a robust fallback mechanism to switch dynamically to traditional similarity matching if TFLite libraries or models are missing/unsupported.
- **Smart Remediation**: View and delete redundant duplicates with real-time UI updates.

### 🔍 Semantic & Multi-Cloud Extensibility
- **Smart Search**: Search files semantically by matching natural-language queries against indexed document headers and properties.
- **Cloud Integration**: Modular screen layout prepared for connecting external cloud storage plugins safely.

---

## 🏗️ Technical Architecture

This application strictly adheres to the **MVVM (Model-View-ViewModel)** architectural pattern and **Clean Architecture** principles:

- **UI Layer**: Jetpack Compose with strict Material Design 3 guidelines, custom dynamic ripple states, responsive touch target sizes (≥ 48dp), and clean typographic hierarchy.
- **Business Logic Layer**: `ViewModel` instances using `StateFlow` and structured coroutine scopes to stream UI states (Loading, Success, Error).
- **Data Layer**: 
  - **Room Database**: Local SQL persistence layer utilizing Flow to provide reactive updates to UI components.
  - **KeystoreVaultManager**: Cryptographic utility manager handling Android Keystore generation and secure file stream pipes.
  - **SemanticEmbeddingProvider**: Neural network embedding interface with support for local on-device inference.

---

## 🛠️ Build and Setup

### Prerequisites
- **Android Studio Koala+** or latest command line tools.
- **JDK 17** configured in your development environment.
- Android device or emulator running **Android API Level 24 (Android 7.0) or higher**.

### Compilation
To compile the debug version of the application:
```bash
gradle :app:assembleDebug
```

### Running Unit Tests
To run JVM-based Unit and Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```

### Static Analysis (Linting)
To check code formatting, safety, and dependencies:
```bash
gradle :app:lintDebug
```

---

## 🤖 Continuous Integration (GitHub Actions)
The repository is fully configured with a **GitHub Actions CI/CD Workflow** located at `.github/workflows/android.yml`. On every `push` or `pull_request` to the main branches, the workflow will automatically:
1. Set up **JDK 17** environment.
2. Initialize and cache **Gradle**.
3. Validate and build the Android debug application package (**APK**).
4. Execute all local unit and architecture verification tests.

---

## 🛡️ License and Credits
Created and maintained under professional Android development standards with ❤️ using Kotlin & Jetpack Compose.
