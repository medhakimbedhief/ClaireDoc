---
name: litert-lm
description: >
  LiteRT-LM engine integration for Android: model loading, GPU/CPU backend,
  conversation management, multimodal image input, engine lifecycle.
  Activate for: LiteRTEngine.kt, model download, inference code, EngineState.
---

# LiteRT-LM Integration — Gemma 4

## Model details
- Repo:     litert-community/gemma-4-E2B-it-litert-lm
- File:     gemma-4-E2B-it.litertlm  ← exact filename, no "int4" in name
- Size:     2.58 GB on disk
- Format:   .litertlm (NOT .gguf, NOT .tflite)
- Download: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm
- Store in: context.getExternalFilesDir("models")/gemma-4-E2B-it.litertlm

## Engine initialization
```kotlin
val engineConfig = EngineConfig(
    modelPath = modelFile.absolutePath,
    backend = Backend.GPU   // fallback to CPU on catch
)
// Always on Dispatchers.IO — blocks until model is loaded
engine = Engine(engineConfig).also { it.initialize() }
```

## Conversation with image (multimodal — Gemma 4 supports vision natively)
```kotlin
val conversation = engine.createConversation(
    ConversationConfig(systemInstruction = SYSTEM_PROMPT)
)
// Stream tokens
conversation.sendMessageAsync(buildMessage(imageUri, userText))
    .collect { token -> appendToOutput(token) }
```

## Engine lifecycle rules
- Singleton via Hilt — one engine instance for the app lifetime
- Initialize once at app start (show loading screen while it loads)
- Release in Application.onTerminate()
- Never initialize on main thread — always Dispatchers.IO
- EngineState sealed class: Initializing | Ready | Processing | Error(msg)

## Performance on A52s 5G (Snapdragon 778G / Adreno 642L)
- GPU backend preferred — uses OpenCL via Adreno
- Expected: ~20-40 tok/s decode on GPU (based on comparable mid-range benchmarks)
- If GPU init fails → catch and retry with Backend.CPU (~5-10 tok/s)
- Memory: ~700MB-1GB GPU footprint — fits within 8GB RAM budget

## OpenCL manifest entries (required for GPU on Android 12+)
```xml
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
<uses-native-library android:name="libOpenCL-car.so" android:required="false"/>
<uses-native-library android:name="libOpenCL-pixel.so" android:required="false"/>
```

## Download via DownloadManager
```kotlin
val request = DownloadManager.Request(Uri.parse(MODEL_URL))
    .setTitle("Downloading Gemma 4 AI model")
    .setDescription("Required for offline document analysis (2.6 GB)")
    .setDestinationUri(Uri.fromFile(modelFile))
    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
downloadManager.enqueue(request)
```