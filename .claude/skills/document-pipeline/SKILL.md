---
name: document-pipeline
description: >
  Document analysis pipeline: ML Kit Document Scanner, FC SDK structured output,
  JSON schema parsing, prompt templates, DocumentResult data model.
  Activate for: ScanScreen, DocumentAnalyzer, PromptBuilder, ResultScreen,
  urgency colors, TTS output.
---

# Document Analysis Pipeline — ClaireDoc

## ML Kit Document Scanner
```kotlin
val options = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(true)
    .setPageLimit(1)
    .setResultFormats(RESULT_FORMAT_JPEG)
    .setScannerMode(SCANNER_MODE_FULL)  // includes auto-crop + perspective correction
    .build()
val scanner = GmsDocumentScanning.getClient(options)
// launch via rememberLauncherForActivityResult in Compose
```

## System prompt (NEVER modify wording — this is the output contract)
You are a document assistant helping people understand official documents.
Analyze the provided document image.
Respond ONLY with a valid JSON object. No explanation, no markdown fences, no preamble.
Use this exact schema:
{
"documentType": "BILL|CONTRACT|LEGAL_NOTICE|MEDICAL|OTHER",
"summary": ["plain bullet 1", "plain bullet 2", "plain bullet 3"],
"actions": [
{"description": "action text", "deadline": "YYYY-MM-DD or null", "urgency": "RED|YELLOW|GREEN"}
],
"risks": ["risk description"],
"urgencyLevel": "RED|YELLOW|GREEN"
}
Rules:

Use plain language a 12-year-old can understand
  Extract all amounts (€, $, £) and dates precisely
  deadline field: ISO date string if found, null if not
  urgencyLevel RED = legal deadline / eviction / visa risk
  urgencyLevel YELLOW = action needed, no immediate deadline
  urgencyLevel GREEN = informational only

## Defensive JSON parsing (model sometimes wraps in markdown despite instructions)
```kotlin
fun parseResult(raw: String): Result<DocumentResult> = runCatching {
    val cleaned = raw
        .trimIndent()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val dto = gson.fromJson(cleaned, DocumentResultDto::class.java)
    dto.toDomain()
}
```

## Urgency UI mapping
```kotlin
fun UrgencyLevel.toColor(): Color = when (this) {
    UrgencyLevel.RED    -> Color(0xFFD32F2F)  // urgent — legal deadline, eviction
    UrgencyLevel.YELLOW -> Color(0xFFF57C00)  // action needed, no hard deadline
    UrgencyLevel.GREEN  -> Color(0xFF388E3C)  // informational
}
```

## TTS output contract
Speak in this order:
1. Document type: "This is a [type]"
2. Each summary bullet
3. First action item if present: "Important: [description]" + deadline if non-null
4. First risk if present: "Warning: [risk]"
Language: match device locale, fallback to English.
Use Android TextToSpeech — no external library.

## ResultScreen required components
- UrgencyBanner: full-width colored banner at top (RED/YELLOW/GREEN)
- DocumentTypeBadge: chip/pill with document type label
- SummaryCard: bulleted list of summary items
- ActionsCard: each ActionItem as row with deadline chip if non-null
- RisksCard: amber warning card, collapsible if >1 risk
- ListenButton: FAB or button → triggers TTS
- ShareButton: export summary as plain text (Tier 3, stub ok)