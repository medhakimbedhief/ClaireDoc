package com.clairedoc.app.ui

import android.net.Uri

object NavRoutes {
    const val DOWNLOAD = "download"        // kept for backward compat (dead route)
    const val MODEL_MANAGER = "model_manager"
    const val HOME = "home"
    const val SCAN = "scan"
    const val RESULT = "result/{resultJson}/{sessionId}"

    fun resultRoute(json: String, sessionId: String): String =
        "result/${Uri.encode(json)}/${Uri.encode(sessionId)}"
}
