# ClaireDoc — Project Rules

## What this app does
Android app: user photographs an official document (letter, bill, contract, prescription)
→ on-device Gemma 4 analyzes it → returns plain-language summary, action items,
urgency level, and risks. 100% offline after first model download.
Hackathon: Kaggle Gemma 4 Good — Digital Equity category.

## Hard constraints
- ALL inference is on-device. No cloud API calls for document processing.
- INTERNET permission is only for first-time model download (DownloadManager).
- No Firebase, no analytics, no ads, no authentication.
- Target device: Samsung Galaxy A52s 5G (Snapdragon 778G, Adreno 642L, 8GB RAM, Android 13).
- minSdk = 26, targetSdk = 34, Kotlin only (no Java).

## Architecture
- Single-module for now (hackathon speed). No over-engineering.
- MVVM: ViewModel + StateFlow + Jetpack Compose UI.
- Hilt for DI.
- Room for document history (scaffold only at start).

## AI stack (non-negotiable)
- Runtime:   LiteRT-LM (com.google.ai.edge.litertlm:litertlm-android)
- Model:     Gemma 4 E2B — litert-community/gemma-4-E2B-it-litert-lm
- File:      gemma-4-E2B-it.litertlm (2.58 GB, .litertlm format)
- Download:  https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm
- FC SDK:    localagents-fc:0.1.0 + tasks-genai:0.10.24 (deprecated but functional for hackathon)
- Scanner:   ML Kit Document Scanner
- TTS:       Android built-in TextToSpeech only — no external dependency

## Output contract (never change this schema)
DocumentResult {
    documentType: String,       // "BILL|CONTRACT|LEGAL_NOTICE|MEDICAL|OTHER"
    summary: List<String>,      // 3-4 plain language bullets
    actions: List<ActionItem>,  // extracted obligations with deadlines
    risks: List<String>,        // warnings
    urgencyLevel: UrgencyLevel  // RED | YELLOW | GREEN
}
ActionItem { description: String, deadline: String?, urgency: UrgencyLevel }
enum UrgencyLevel { RED, YELLOW, GREEN }

## Code style
- Coroutines everywhere, no RxJava
- StateFlow for reactive state
- Sealed classes for state modeling (Loading/Success/Error)
- No TODO comments — use clearly named placeholder functions