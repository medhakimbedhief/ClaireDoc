package com.clairedoc.app.ui.scan

import android.app.Activity.RESULT_OK
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.ui.NavRoutes
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavController,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── ML Kit camera / gallery scanner ────────────────────────
    val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            val scanResult = GmsDocumentScanningResult
                .fromActivityResultIntent(activityResult.data)
            val imageUri = scanResult?.pages?.firstOrNull()?.imageUri
            if (imageUri != null) viewModel.analyzeDocument(imageUri)
            else viewModel.clearError()
        } else {
            viewModel.clearError()
        }
    }

    // ── PDF file picker (Storage Access Framework) ─────────────
    // Opens the system file picker filtered to PDFs only.
    // The returned URI is permanent-read granted by SAF — no MANAGE_EXTERNAL_STORAGE needed.
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.analyzePdf(uri)
        else viewModel.clearError()
    }

    // ── Navigation / error side-effects ────────────────────────
    LaunchedEffect(state) {
        if (state is ScanUiState.Success) {
            navController.navigate(
                NavRoutes.resultRoute((state as ScanUiState.Success).resultJson)
            ) { launchSingleTop = true }
        }
    }

    LaunchedEffect(state) {
        if (state is ScanUiState.Error) {
            snackbarHostState.showSnackbar(
                message = (state as ScanUiState.Error).message,
                actionLabel = "Retry"
            )
            viewModel.clearError()
        }
    }

    // ── Layout ─────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClaireDoc") },
                actions = {
                    IconButton(
                        onClick = { navController.navigate(NavRoutes.MODEL_MANAGER) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage AI Model"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is ScanUiState.Analyzing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Analysing document…",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This may take 20–60 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val buttonsEnabled = state !is ScanUiState.Scanning &&
                            state !is ScanUiState.Analyzing

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Scan a Document",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Point your camera at any bill, letter, contract, or medical document.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(40.dp))

                        // Camera scan
                        Button(
                            onClick = {
                                viewModel.onScannerOpened()
                                scanner.getStartScanIntent(context as android.app.Activity)
                                    .addOnSuccessListener { intentSender ->
                                        scannerLauncher.launch(
                                            IntentSenderRequest.Builder(intentSender).build()
                                        )
                                    }
                                    .addOnFailureListener { viewModel.clearError() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled
                        ) {
                            Text("Scan Document")
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Gallery image import (ML Kit)
                        OutlinedButton(
                            onClick = {
                                viewModel.onScannerOpened()
                                scanner.getStartScanIntent(context as android.app.Activity)
                                    .addOnSuccessListener { intentSender ->
                                        scannerLauncher.launch(
                                            IntentSenderRequest.Builder(intentSender).build()
                                        )
                                    }
                                    .addOnFailureListener { viewModel.clearError() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled
                        ) {
                            Text("Choose from Gallery")
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // PDF file import
                        OutlinedButton(
                            onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Pick a PDF")
                        }
                    }
                }
            }
        }
    }
}
