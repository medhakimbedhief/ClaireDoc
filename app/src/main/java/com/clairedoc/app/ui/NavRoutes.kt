package com.clairedoc.app.ui

import android.net.Uri

object NavRoutes {
    const val DOWNLOAD = "download"
    const val SCAN = "scan"
    const val RESULT = "result/{resultJson}"

    /** URL-encodes [json] and builds the full result route string. */
    fun resultRoute(json: String): String = "result/${Uri.encode(json)}"
}
