package com.clairedoc.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.ui.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {

                // ── AI MODEL ─────────────────────────────────────────────────
                item { SettingsSectionHeader("AI MODEL") }

                item {
                    SettingsInfoRow(label = "Model", value = uiState.modelName)
                }
                item {
                    SettingsInfoRow(label = "Backend", value = uiState.backend)
                }
                item {
                    SettingsInfoRow(
                        label = "Embedding model",
                        value = if (uiState.embeddingReady) "Ready" else "Not installed"
                    )
                }
                item {
                    SettingsNavRow(
                        label = "Manage model",
                        onClick = { navController.navigate(NavRoutes.MODEL_MANAGER) }
                    )
                }

                // ── STORAGE ───────────────────────────────────────────────────
                item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
                item { SettingsSectionHeader("STORAGE") }

                item {
                    SettingsInfoRow(
                        label = "Documents scanned",
                        value = uiState.docCount.toString()
                    )
                }
                item {
                    val indexLabel = buildString {
                        append("${uiState.chunkCount} chunk${if (uiState.chunkCount != 1) "s" else ""}")
                        if (uiState.indexStorageMb > 0f) {
                            append(" · %.1f MB".format(uiState.indexStorageMb))
                        }
                    }
                    SettingsInfoRow(label = "AI index", value = indexLabel)
                }

                // ── PRIVACY ────────────────────────────────────────────────────
                item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
                item { SettingsSectionHeader("PRIVACY") }

                item {
                    SettingsInfoRow(
                        label = "Processing",
                        value = "On-device only — no data ever leaves your phone"
                    )
                }
                item {
                    SettingsInfoRow(
                        label = "Accounts",
                        value = "Not required — ClaireDoc collects no personal data"
                    )
                }
                item {
                    SettingsInfoRow(
                        label = "Network",
                        value = "Used only for the first-time model download"
                    )
                }

                // ── ABOUT ─────────────────────────────────────────────────────
                item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
                item { SettingsSectionHeader("ABOUT") }

                item {
                    SettingsInfoRow(label = "Version", value = uiState.appVersion)
                }
                item {
                    SettingsInfoRow(
                        label = "Hackathon",
                        value = "Kaggle Gemma 4 Good — Digital Equity"
                    )
                }
                item {
                    SettingsInfoRow(
                        label = "AI model",
                        value = "Google Gemma 4 (on-device, LiteRT-LM runtime)"
                    )
                }
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun SettingsNavRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
