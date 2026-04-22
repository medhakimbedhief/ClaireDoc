package com.clairedoc.app.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.data.model.ModelVariant
import com.clairedoc.app.ui.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    navController: NavController,
    canNavigateBack: Boolean = false,
    viewModel: ModelManagerViewModel = hiltViewModel()
) {
    val e2bState by viewModel.states[ModelVariant.E2B]!!.collectAsState()
    val e4bState by viewModel.states[ModelVariant.E4B]!!.collectAsState()

    val anyInstalled =
        e2bState is VariantUiState.Installed || e4bState is VariantUiState.Installed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Model") },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Choose and manage the on-device AI model. " +
                        "All processing happens 100% offline after the download.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // E2B card
            ModelVariantCard(
                variant = ModelVariant.E2B,
                state = e2bState,
                otherVariantInstalled = e4bState is VariantUiState.Installed,
                onDownload = { viewModel.download(ModelVariant.E2B) },
                onCancel  = { viewModel.cancelDownload(ModelVariant.E2B) },
                onDelete  = { viewModel.deleteModel(ModelVariant.E2B) },
                onRetry   = { viewModel.retryDownload(ModelVariant.E2B) }
            )

            // E4B card
            ModelVariantCard(
                variant = ModelVariant.E4B,
                state = e4bState,
                otherVariantInstalled = e2bState is VariantUiState.Installed,
                onDownload = { viewModel.download(ModelVariant.E4B) },
                onCancel  = { viewModel.cancelDownload(ModelVariant.E4B) },
                onDelete  = { viewModel.deleteModel(ModelVariant.E4B) },
                onRetry   = { viewModel.retryDownload(ModelVariant.E4B) }
            )

            // "Start Scanning" — only visible once a model is installed
            if (anyInstalled) {
                Button(
                    onClick = {
                        if (canNavigateBack) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(NavRoutes.HOME) {
                                popUpTo(NavRoutes.MODEL_MANAGER) { inclusive = true }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Start Scanning")
                }
            }

            Text(
                text = "Only one model can be active at a time. " +
                        "Delete the current model before downloading a different variant.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Per-variant card
// ─────────────────────────────────────────────────────────────

@Composable
private fun ModelVariantCard(
    variant: ModelVariant,
    state: VariantUiState,
    otherVariantInstalled: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val isInstalled = state is VariantUiState.Installed

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isInstalled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row: name + status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = variant.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = variant.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    isInstalled -> AssistChip(
                        onClick = {},
                        label = { Text("Installed") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    state is VariantUiState.NotInstalled -> SuggestionChip(
                        onClick = {},
                        label = { Text(variant.approximateSizeGb) }
                    )
                    else -> Unit
                }
            }

            Spacer(Modifier.height(16.dp))

            // Body: state-dependent content
            when (state) {
                is VariantUiState.NotInstalled -> {
                    if (otherVariantInstalled) {
                        Text(
                            text = "Delete the installed model first to switch variants.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download ${variant.approximateSizeGb}")
                        }
                    }
                }

                is VariantUiState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.progress.displayMb,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.progress.displayPercent,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (state.progress.isPaused) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Waiting for network…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }

                is VariantUiState.Installed -> {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Model")
                    }
                }

                is VariantUiState.Failed -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry Download")
                    }
                }

                is VariantUiState.Deleting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Deleting model file…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
