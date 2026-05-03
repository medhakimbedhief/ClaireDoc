package com.clairedoc.app.actions

data class EmailDraft(
    val to: String,
    val subject: String,
    val body: String
)
