package com.clairedoc.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.UrgencyLevel
import com.clairedoc.app.ui.NavRoutes
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val URGENCY_STRIP_COLORS = mapOf(
    UrgencyLevel.RED    to Color(0xFFD32F2F),
    UrgencyLevel.YELLOW to Color(0xFFF57C00),
    UrgencyLevel.GREEN  to Color(0xFF388E3C)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedItem by remember { mutableStateOf<HomeSessionItem?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClaireDoc") },
                actions = {
                    IconButton(onClick = { navController.navigate(NavRoutes.MODEL_MANAGER) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage AI Model"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(NavRoutes.SCAN) }) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "New scan"
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            HomeUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            HomeUiState.Empty -> EmptyHomeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            is HomeUiState.Sessions -> {
                val grouped = state.items.groupBy { it.session.status }
                val statusOrder = listOf(
                    SessionStatus.OVERDUE,
                    SessionStatus.UNREAD,
                    SessionStatus.IN_PROGRESS,
                    SessionStatus.DONE
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statusOrder.forEach { status ->
                        val group = grouped[status] ?: return@forEach
                        stickyHeader(key = status.name) {
                            StatusSectionHeader(status = status)
                        }
                        items(items = group, key = { it.session.id }) { item ->
                            DocumentCard(
                                item = item,
                                onClick = {
                                    navController.navigate(
                                        NavRoutes.resultRoute(item.resultJson, item.session.id)
                                    )
                                },
                                onLongClick = {
                                    selectedItem = item
                                    showSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                selectedItem = null
            },
            sheetState = sheetState
        ) {
            val item = selectedItem
            if (item != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = item.session.userTitle
                            ?: item.session.documentType.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    SheetAction(
                        icon = Icons.Default.DriveFileRenameOutline,
                        label = "Rename",
                        onClick = {
                            renameText = item.session.userTitle ?: ""
                            scope.launch { sheetState.hide() }
                            showSheet = false
                            showRenameDialog = true
                        }
                    )
                    if (item.session.status != SessionStatus.DONE) {
                        SheetAction(
                            icon = Icons.Default.TaskAlt,
                            label = "Mark as Done",
                            onClick = {
                                viewModel.markDone(item.session.id)
                                scope.launch { sheetState.hide() }
                                showSheet = false
                                selectedItem = null
                            }
                        )
                    }
                    SheetAction(
                        icon = Icons.Default.Archive,
                        label = "Archive",
                        onClick = {
                            viewModel.archiveSession(item.session.id)
                            scope.launch { sheetState.hide() }
                            showSheet = false
                            selectedItem = null
                        }
                    )
                    SheetAction(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            scope.launch { sheetState.hide() }
                            showSheet = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        val item = selectedItem
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; selectedItem = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (item != null && renameText.isNotBlank()) {
                        viewModel.renameSession(item.session.id, renameText.trim())
                    }
                    showRenameDialog = false
                    selectedItem = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; selectedItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        val item = selectedItem
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; selectedItem = null },
            title = { Text("Delete document?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (item != null) viewModel.deleteSession(item.session.id)
                    showDeleteDialog = false
                    selectedItem = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Document card
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(
    item: HomeSessionItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val urgencyLevel = runCatching {
        UrgencyLevel.valueOf(item.session.urgencyLevel)
    }.getOrElse { UrgencyLevel.GREEN }
    val stripColor = URGENCY_STRIP_COLORS[urgencyLevel] ?: Color(0xFF388E3C)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(color = stripColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.session.documentType.replace("_", " "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusBadge(status = item.session.status)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.session.userTitle ?: relativeTime(item.session.createdAt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.firstActionDescription?.let { desc ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.nearestDeadlineDays?.let { days ->
                    if (days <= 7) {
                        Spacer(Modifier.height(6.dp))
                        val label = when {
                            days < 0  -> "Overdue by ${-days}d"
                            days == 0 -> "Due today"
                            days == 1 -> "Due tomorrow"
                            else      -> "Due in ${days}d"
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (days <= 0) Color(0x1FD32F2F) else Color(0x1FF57C00),
                                labelColor = if (days <= 0) Color(0xFFD32F2F) else Color(0xFFF57C00)
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Supporting composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun StatusSectionHeader(status: SessionStatus) {
    val label = when (status) {
        SessionStatus.OVERDUE     -> "Overdue"
        SessionStatus.UNREAD      -> "New"
        SessionStatus.IN_PROGRESS -> "In Progress"
        SessionStatus.DONE        -> "Done"
    }
    val color = when (status) {
        SessionStatus.OVERDUE     -> Color(0xFFD32F2F)
        SessionStatus.UNREAD      -> MaterialTheme.colorScheme.primary
        SessionStatus.IN_PROGRESS -> Color(0xFFF57C00)
        SessionStatus.DONE        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun StatusBadge(status: SessionStatus) {
    val label = when (status) {
        SessionStatus.OVERDUE     -> "Overdue"
        SessionStatus.UNREAD      -> "New"
        SessionStatus.IN_PROGRESS -> "In progress"
        SessionStatus.DONE        -> "Done"
    }
    val containerColor = when (status) {
        SessionStatus.OVERDUE     -> Color(0x1FD32F2F)
        SessionStatus.UNREAD      -> MaterialTheme.colorScheme.primaryContainer
        SessionStatus.IN_PROGRESS -> Color(0x1FF57C00)
        SessionStatus.DONE        -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when (status) {
        SessionStatus.OVERDUE     -> Color(0xFFD32F2F)
        SessionStatus.UNREAD      -> MaterialTheme.colorScheme.onPrimaryContainer
        SessionStatus.IN_PROGRESS -> Color(0xFFF57C00)
        SessionStatus.DONE        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        )
    )
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = tint) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun EmptyHomeContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DocumentScanner,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No documents yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap the camera button to scan\nyour first document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Utilities
// ─────────────────────────────────────────────────────────────

private val DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun relativeTime(epochMs: Long): String {
    val diffMin = (System.currentTimeMillis() - epochMs) / 60_000
    return when {
        diffMin < 1     -> "Just now"
        diffMin < 60    -> "${diffMin}min ago"
        diffMin < 1440  -> "${diffMin / 60}h ago"
        diffMin < 2880  -> "Yesterday"
        diffMin < 10080 -> "${diffMin / 1440}d ago"
        else -> Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DISPLAY_DATE_FMT)
    }
}
