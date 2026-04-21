package com.clairedoc.app.ui.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clairedoc.app.pipeline.DocumentAnalyzer
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanUiState {
    object Idle : ScanUiState()
    object Scanning : ScanUiState()    // scanner UI is open
    object Analyzing : ScanUiState()   // LiteRT inference running
    data class Success(val resultJson: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentAnalyzer: DocumentAnalyzer,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onScannerOpened() {
        _uiState.value = ScanUiState.Scanning
    }

    fun analyzeDocument(imageUri: Uri) {
        _uiState.value = ScanUiState.Analyzing
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = documentAnalyzer.analyze(imageUri, context)) {
                is DocumentAnalyzer.AnalysisResult.Success -> {
                    val json = gson.toJson(result.result)
                    _uiState.value = ScanUiState.Success(json)
                }
                is DocumentAnalyzer.AnalysisResult.Failure -> {
                    _uiState.value = ScanUiState.Error(
                        result.message.ifBlank { "Analysis failed. Please try again." }
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = ScanUiState.Idle
    }
}
