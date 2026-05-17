# ClaireDoc

**100% offline AI document assistant for Android.**

Photograph any official document — letter, bill, contract, prescription, legal notice — and Gemma 4 analyses it on-device in seconds. No cloud, no account, no data leaving your phone.

Built for the **Kaggle Gemma 4 Good — Digital Equity** hackathon.

---

## What it does

| Feature | Description |
|---|---|
| **Scan** | Use the camera or import from gallery / PDF. ML Kit auto-crops and corrects perspective. |
| **Analyse** | Gemma 4 E2B (2.6 GB, on-device) extracts document type, plain-language summary, action items with deadlines, risks, urgency level, key contacts, and a glossary of jargon. |
| **Track** | Every scanned document is saved with status (New → In Progress → Done / Overdue). |
| **Categories** | Browse documents grouped by type (Bill, Contract, Medical, …). Tap a category to filter. Reclassify any document from the same screen. |
| **Ask AI** | Cross-document Q&A powered by a local HNSW vector index (EmbeddingGemma-300M + ObjectBox). Ask questions across your entire document library. |
| **Listen** | Full TTS read-back of any document summary using the device's built-in speech engine. |
| **Follow-up chat** | Per-document Q&A with full conversation history, stored in Room. |

---

## Screenshots

_Coming soon._

---

## Architecture

```
UI (Jetpack Compose)
  ├── HomeScreen        — document list, inline search, status grouping
  ├── CategoriesScreen  — type grid + FilteredCategoryScreen
  ├── ResultScreen      — full analysis view + per-doc Q&A
  ├── RagChatScreen     — cross-document Ask AI
  └── SettingsScreen    — model info, storage stats, privacy

ViewModel (Hilt + StateFlow)
Repository
  ├── DocumentSessionRepository  — Room (SQLite)
  └── ChunkRepository            — ObjectBox (HNSW vector store)

AI Stack
  ├── LiteRT-LM — Gemma 4 E2B inference (GPU / CPU fallback)
  └── EmbeddingGemma-300M TFLite — sentence embeddings for RAG
```

**Single-module** · **MVVM** · **Kotlin only** · **minSdk 26 / targetSdk 34**

---

## AI stack

| Component | Library | Model |
|---|---|---|
| Document analysis | LiteRT-LM `0.1.0` | `gemma-4-E2B-it.litertlm` (2.6 GB) |
| Embeddings | MediaPipe Tasks GenAI `0.10.24` | `EmbeddingGemma-300M` (~180 MB) |
| Document scanning | ML Kit Document Scanner `16.0.0-beta1` | — |
| Vector store | ObjectBox | HNSW approximate nearest-neighbour |

All inference runs on-device. Internet is used **only** for the one-time model download.

---

## Getting started

### Prerequisites
- Android Studio Hedgehog or newer
- Android device / emulator with **minSdk 26** (Android 8.0)
- ~3 GB free storage for the Gemma 4 E2B model

### Build

1. Clone the repo.
2. Create `local.properties` (gitignored) and add your HuggingFace read token — required to download the gated EmbeddingGemma-300M model:
   ```
   HUGGINGFACE_TOKEN=hf_...
   ```
3. Open in Android Studio and run on a device.  
   On first launch the app will prompt you to download the Gemma 4 model (~2.6 GB, Wi-Fi recommended).

### First launch flow
1. **Download** — the Model Manager screen guides you through the one-time download.
2. **Scan** — tap the camera FAB on the Documents tab.
3. **Ask** — once at least one document is scanned, tap **Ask** to query across your library.

---

## Document types

The app recognises and categorises these types:

`BILL` · `CONTRACT` · `LEGAL_NOTICE` · `MEDICAL` · `TAX` · `INSURANCE` · `BANK` · `VISA_IMMIGRATION` · `GOVERNMENT_NOTICE` · `RENTAL` · `OTHER`

Users can reclassify any document at any time from the overflow menu (⋮) in the document view or from the long-press menu on the document list.

---

## Privacy

- All document processing happens **on-device**.
- No analytics, no Firebase, no ads, no authentication.
- Network permission is used **only** to download models on first launch.
- Scanned images are stored locally in app-private storage and never uploaded.

---

## Hackathon context

**Competition:** Kaggle Gemma 4 Good  
**Category:** Digital Equity  
**Goal:** Help people who struggle with official paperwork — those with literacy barriers, language barriers, or limited access to legal/administrative support — understand documents that affect their lives.

---

## License

MIT — see [LICENSE](LICENSE).
