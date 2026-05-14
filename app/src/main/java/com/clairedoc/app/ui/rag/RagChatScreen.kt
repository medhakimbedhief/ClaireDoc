package com.clairedoc.app.ui.rag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.data.model.Role
import com.clairedoc.app.data.model.UrgencyLevel
import com.clairedoc.app.ui.NavRoutes
import com.clairedoc.app.ui.theme.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagChatScreen(
    navController: NavController,
    viewModel: RagChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val indexedDocCount by viewModel.indexedDocCount.collectAsState()
    val embeddingReady by viewModel.embeddingReady.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    // Auto-start embedding model download when the screen opens and token is available.
    LaunchedEffect(embeddingReady) {
        if (!embeddingReady && downloadProgress == null) {
            viewModel.triggerEmbedderDownload()
        }
    }

    val lastIsAssistant = messages.lastOrNull()?.role == Role.ASSISTANT

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ask your documents",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (indexedDocCount > 0) {
                            Text(
                                text = "$indexedDocCount document${if (indexedDocCount == 1) "" else "s"} indexed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearHistory) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear conversation"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (lastIsAssistant && !isLoading) {
                FloatingActionButton(onClick = viewModel::speakLastAnswer) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Listen to answer"
                    )
                }
            }
        },
        bottomBar = {
            if (indexedDocCount > 0 && embeddingReady) {
                ChatInputBar(
                    isLoading = isLoading,
                    onSend = { question ->
                        if (question.isNotBlank()) viewModel.sendQuery(question)
                    }
                )
            }
        }
    ) { paddingValues ->
        when {
            // ── Empty state: no documents indexed ──────────────────────────────
            indexedDocCount == 0 -> {
                EmptyStateNoDocuments(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onScanTapped = { navController.navigate(NavRoutes.SCAN) }
                )
            }

            // ── Embedder not ready: auto-downloading ──────────────────────────
            !embeddingReady -> {
                EmptyStateDownloading(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    progress = downloadProgress
                )
            }

            // ── Chat view ──────────────────────────────────────────────────────
            else -> {
                ChatContent(
                    messages = messages,
                    streamingText = streamingText,
                    isLoading = isLoading,
                    paddingValues = paddingValues,
                    onSourceTapped = { source ->
                        val route = viewModel.getResultRouteForSource(source) ?: return@ChatContent
                        navController.navigate(route)
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateNoDocuments(
    modifier: Modifier = Modifier,
    onScanTapped: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FindInPage,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No documents indexed yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Scan a document first, then come back to ask questions across all of them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onScanTapped) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Scan a document")
            }
        }
    }
}

@Composable
private fun EmptyStateDownloading(
    modifier: Modifier = Modifier,
    progress: Float?
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Setting up search model…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "One-time download (~180 MB). This only happens once.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chat content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatContent(
    messages: List<RagMessage>,
    streamingText: String,
    isLoading: Boolean,
    paddingValues: PaddingValues,
    onSourceTapped: (SourceReference) -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive or streaming text updates
    LaunchedEffect(messages.size, streamingText) {
        val lastIndex = messages.size + (if (isLoading) 1 else 0) - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    if (messages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ask anything about your documents",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                RagMessageBubble(message = msg, onSourceTapped = onSourceTapped)
            }

            // Streaming / loading indicator
            if (isLoading) {
                item {
                    if (streamingText.isNotEmpty()) {
                        StreamingBubble(text = streamingText)
                    } else {
                        LoadingBubble()
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Message bubbles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RagMessageBubble(
    message: RagMessage,
    onSourceTapped: (SourceReference) -> Unit
) {
    val isUser = message.role == Role.USER

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp, topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 4.dp,
                    bottomEnd   = if (isUser) 4.dp  else 12.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sources card — only for ASSISTANT messages with cited documents
        if (!isUser && message.sources.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SourcesCard(sources = message.sources, onSourceTapped = onSourceTapped)
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Searching documents…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sources card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SourcesCard(
    sources: List<SourceReference>,
    onSourceTapped: (SourceReference) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .padding(start = 8.dp, end = 16.dp)
            .fillMaxWidth(0.85f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📚 Sources (${sources.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse sources" else "Expand sources",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Expandable source list ────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    sources.forEach { source ->
                        SourceRow(source = source, onTapped = { onSourceTapped(source) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: SourceReference, onTapped: () -> Unit) {
    val urgencyColor = runCatching {
        UrgencyLevel.valueOf(source.urgencyLevel).toColor()
    }.getOrElse { MaterialTheme.colorScheme.outline }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTapped)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Urgency colour strip
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(urgencyColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.documentTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = source.relevantSnippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Tap to open →",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Input bar (shown in Scaffold.bottomBar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    isLoading: Boolean,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    fun sendAndClear() {
        val q = inputText.trim()
        if (q.isNotBlank() && !isLoading) {
            onSend(q)
            inputText = ""
        }
    }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Ask anything about your documents…",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendAndClear() })
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { sendAndClear() },
                enabled = !isLoading && inputText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (!isLoading && inputText.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}
