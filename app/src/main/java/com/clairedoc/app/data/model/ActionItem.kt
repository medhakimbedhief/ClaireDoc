package com.clairedoc.app.data.model

data class ActionItem(
    val description: String,
    val deadline: String?,      // ISO-8601 date string (YYYY-MM-DD) or null
    val urgency: UrgencyLevel
)
