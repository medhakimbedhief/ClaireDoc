package com.clairedoc.app.data.model

enum class ModelVariant(
    val displayName: String,
    val tagline: String,
    val filename: String,
    val url: String,
    val approximateSizeGb: String
) {
    E2B(
        displayName = "Gemma 4 E2B",
        tagline = "2B effective parameters · 2.6 GB · Fast & efficient",
        filename = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        approximateSizeGb = "2.6 GB"
    ),
    E4B(
        displayName = "Gemma 4 E4B",
        tagline = "4B effective parameters · ~5 GB · Higher accuracy",
        filename = "gemma-4-E4B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        approximateSizeGb = "~5 GB"
    )
}
