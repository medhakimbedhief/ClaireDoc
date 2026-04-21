package com.clairedoc.app.engine

sealed class EngineState {
    object Uninitialized : EngineState()
    object Initializing : EngineState()
    data class Ready(val isGpu: Boolean) : EngineState()
    data class Error(val message: String, val cause: Throwable? = null) : EngineState()
}
