package com.clairedoc.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.clairedoc.app.data.model.UrgencyLevel

// Brand colours
val Blue80 = Color(0xFFBBC8FF)
val BlueGrey80 = Color(0xFFBFC9D4)
val Blue40 = Color(0xFF1565C0)
val BlueGrey40 = Color(0xFF4A6076)

// Urgency colours — defined by document-pipeline skill, never change
val UrgencyRed = Color(0xFFD32F2F)
val UrgencyYellow = Color(0xFFF57C00)
val UrgencyGreen = Color(0xFF388E3C)

val UrgencyRedContainer = Color(0xFFFFCDD2)
val UrgencyYellowContainer = Color(0xFFFFE0B2)
val UrgencyGreenContainer = Color(0xFFC8E6C9)

fun UrgencyLevel.toColor(): Color = when (this) {
    UrgencyLevel.RED -> UrgencyRed
    UrgencyLevel.YELLOW -> UrgencyYellow
    UrgencyLevel.GREEN -> UrgencyGreen
}

fun UrgencyLevel.toContainerColor(): Color = when (this) {
    UrgencyLevel.RED -> UrgencyRedContainer
    UrgencyLevel.YELLOW -> UrgencyYellowContainer
    UrgencyLevel.GREEN -> UrgencyGreenContainer
}

fun UrgencyLevel.toLabel(): String = when (this) {
    UrgencyLevel.RED -> "URGENT"
    UrgencyLevel.YELLOW -> "ATTENTION"
    UrgencyLevel.GREEN -> "OK"
}
