---
name: android-dev
description: >
  Modern Android development with Kotlin, Jetpack Compose, MVVM, Hilt DI,
  Room database, Navigation Component, and Gradle version catalogs.
  Activate for: creating screens, ViewModels, Hilt modules, Room entities,
  navigation graphs, or any Kotlin Android boilerplate.
---

# Android Development Patterns for ClaireDoc

## UI Layer — Jetpack Compose only
- All screens are @Composable functions, no XML layouts except NavHost container
- Screen naming: XxxScreen.kt — receives UiState, emits events upward via lambdas
- ViewModel exposes: StateFlow<XxxUiState> — sealed class with Loading/Success/Error

## ViewModel pattern
```kotlin
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val documentAnalyzer: DocumentAnalyzer
) : ViewModel() {
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun analyzeDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading
            documentAnalyzer.analyze(uri)
                .onSuccess { _uiState.value = ScanUiState.Success(it) }
                .onFailure { _uiState.value = ScanUiState.Error(it.message ?: "Unknown") }
        }
    }
}
```

## Hilt setup
- @AndroidEntryPoint on MainActivity
- @HiltViewModel on all ViewModels
- AppModule.kt provides: DocumentAnalyzer, LiteRTEngine, TTSManager
- DatabaseModule.kt provides: AppDatabase, DocumentDao

## Navigation — Compose Navigation
- NavHost in MainActivity with composable destinations
- Routes: "scan" → "result/{documentJson}"
- Pass DocumentResult as JSON-encoded string argument

## Gradle (libs.versions.toml) — always use version catalog
```toml
[versions]
compose-bom = "2024.04.01"
hilt = "2.51"
room = "2.6.1"
navigation = "2.7.7"
litertlm = "0.1.0"
mlkit-scanner = "16.0.0-beta1"
mediapipe = "0.10.24"
localagents-fc = "0.1.0"
gson = "2.10.1"

[libraries]
litert-lm = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
mlkit-document-scanner = { group = "com.google.android.gms", name = "play-services-mlkit-document-scanner", version.ref = "mlkit-scanner" }
mediapipe-tasks-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipe" }
localagents-fc = { group = "com.google.ai.edge.localagents", name = "localagents-fc", version.ref = "localagents-fc" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
```