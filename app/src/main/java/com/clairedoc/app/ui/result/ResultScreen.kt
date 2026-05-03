package com.clairedoc.app.ui.result

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.data.model.ActionItem
import com.clairedoc.app.data.model.ChatMessage
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.Role
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.UrgencyLevel
import com.clairedoc.app.ui.NavRoutes
import com.clairedoc.app.ui.theme.UrgencyRed
import com.clairedoc.app.ui.theme.toColor
import com.clairedoc.app.ui.theme.toLabel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.clairedoc.app.actions.EmailDraft
import com.clairedoc.app.data.model.Confidence
import com.clairedoc.app.data.model.GlossaryTerm

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    resultJson: String,  // unused here — ViewModel reads from SavedStateHandle
    navController: NavController,
    viewModel: ResultViewModel = hiltViewModel(),
    emailViewModel: EmailActionViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val sessionStatus by viewModel.sessionStatus.collectAsState()
    val userTitle by viewModel.userTitle.collectAsState()
    val showConfetti by viewModel.showConfetti.collectAsState()
    val emailUiState by emailViewModel.uiState.collectAsState()
    val emailAddresses by emailViewModel.emailAddresses.collectAsState()
    val selectedEmail by emailViewModel.selectedEmail.collectAsState()

    var isEditingTitle by remember { mutableStateOf(false) }
    var editTitleText by remember(userTitle) { mutableStateOf(userTitle ?: "") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isEditingTitle) {
                            OutlinedTextField(
                                value = editTitleText,
                                onValueChange = { editTitleText = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (editTitleText.isNotBlank()) {
                                        viewModel.renameSession(editTitleText.trim())
                                    }
                                    isEditingTitle = false
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = userTitle ?: "Document",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (!isEditingTitle) {
                            IconButton(onClick = {
                                editTitleText = userTitle ?: ""
                                isEditingTitle = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename"
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Retake document") },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.deleteSessionIfUnread()
                                            navController.popBackStack()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (result != null) {
                    ListenFAB(isSpeaking = isSpeaking, onClick = viewModel::toggleTTS)
                }
            }
        ) { paddingValues ->
            when (val r = result) {
                null -> EmptyResultContent(paddingValues)
                else -> ResultContent(
                    result = r,
                    paddingValues = paddingValues,
                    chatMessages = chatMessages,
                    streamingText = streamingText,
                    isChatLoading = isChatLoading,
                    sessionStatus = sessionStatus,
                    onSendQuestion = viewModel::askFollowUp,
                    onUpdateStatus = viewModel::updateStatus,
                    onSpeakTerm = viewModel::speakTerm,
                    onRetake = {
                        viewModel.deleteSessionIfUnread()
                        navController.popBackStack()
                    },
                    onDone = { navController.popBackStack(NavRoutes.HOME, inclusive = false) },
                    context = context,
                    emailAddresses = emailAddresses,
                    selectedEmail = selectedEmail,
                    emailUiState = emailUiState,
                    onSelectEmail = emailViewModel::selectEmail,
                    onGenerateDraft = emailViewModel::generateDraft,
                    onConfirmRegenerate = emailViewModel::confirmRegenerate,
                    onUpdateDraftBody = emailViewModel::updateDraftBody,
                    onResetDraft = emailViewModel::resetDraft
                )
            }
        }

        if (showConfetti) {
            ConfettiOverlay(onEnd = viewModel::dismissConfetti)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultContent(
    result: DocumentResult,
    paddingValues: PaddingValues,
    chatMessages: List<ChatMessage>,
    streamingText: String,
    isChatLoading: Boolean,
    sessionStatus: SessionStatus,
    onSendQuestion: (String) -> Unit,
    onUpdateStatus: (SessionStatus) -> Unit,
    onSpeakTerm: (GlossaryTerm) -> Unit,
    onRetake: () -> Unit,
    onDone: () -> Unit,
    context: android.content.Context,
    emailAddresses: List<String>,
    selectedEmail: String,
    emailUiState: EmailDraftUiState,
    onSelectEmail: (String) -> Unit,
    onGenerateDraft: (String) -> Unit,
    onConfirmRegenerate: (String) -> Unit,
    onUpdateDraftBody: (String) -> Unit,
    onResetDraft: () -> Unit
) {
    var selectedTerm by remember { mutableStateOf<GlossaryTerm?>(null) }
    val clipboardManager = LocalClipboardManager.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(bottom = 88.dp)  // room for FAB
    ) {
        item { UrgencyBanner(urgencyLevel = result.urgencyLevel) }
        val confidence = result.confidence
        if (confidence != null && confidence != Confidence.HIGH) {
            item { ConfidenceBanner(confidence = confidence, onRetake = onRetake) }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            StatusSection(status = sessionStatus, onStepTapped = onUpdateStatus)
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            QuickActionButtons(
                status = sessionStatus,
                onMarkDone = { onUpdateStatus(SessionStatus.DONE) },
                onHandlingIt = { onUpdateStatus(SessionStatus.IN_PROGRESS) },
                onRemindMe = { launchAlarmIntent(context) }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DocumentTypeBadge(documentType = result.documentType)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { SummaryCard(summary = result.summary) }
        item { Spacer(Modifier.height(12.dp)) }
        val glossaryTerms = result.glossaryTerms.orEmpty()
        if (glossaryTerms.isNotEmpty()) {
            item {
                GlossaryChipsRow(
                    terms = glossaryTerms,
                    onTermTapped = { selectedTerm = it }
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (result.actions.isNotEmpty()) {
            item { ActionsCard(actions = result.actions) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (result.risks.isNotEmpty()) {
            item { RisksCard(risks = result.risks) }
            item { Spacer(Modifier.height(12.dp)) }
        }

        // Q&A section
        item {
            QASection(
                chatMessages = chatMessages,
                streamingText = streamingText,
                isLoading = isChatLoading,
                onSendQuestion = onSendQuestion
            )
        }
        item { Spacer(Modifier.height(12.dp)) }

        // Email reply section — only shown when the document contains at least one email contact
        if (emailAddresses.isNotEmpty()) {
            item {
                EmailCard(
                    emailAddresses = emailAddresses,
                    selectedEmail = selectedEmail,
                    uiState = emailUiState,
                    onSelectEmail = onSelectEmail,
                    onGenerateDraft = onGenerateDraft,
                    onConfirmRegenerate = onConfirmRegenerate,
                    onUpdateBody = onUpdateDraftBody,
                    onReset = onResetDraft,
                    onCopyToClipboard = { draft ->
                        clipboardManager.setText(
                            AnnotatedString("Subject: ${draft.subject}\n\n${draft.body}")
                        )
                    },
                    onOpenEmailApp = { draft ->
                        val uri = Uri.parse(
                            "mailto:${Uri.encode(draft.to)}" +
                            "?subject=${Uri.encode(draft.subject)}" +
                            "&body=${Uri.encode(draft.body)}"
                        )
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO, uri), null))
                    }
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        item {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Done")
            }
        }
    }

    // TermExplanationBottomSheet is shown outside the LazyColumn so it
    // overlays the entire screen correctly and isn't clipped by the list.
    selectedTerm?.let { term ->
        TermExplanationBottomSheet(
            term = term,
            onListen = { onSpeakTerm(term) },
            onDismiss = { selectedTerm = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Status stepper & quick actions
// ─────────────────────────────────────────────────────────────

@Composable
private fun StatusSection(status: SessionStatus, onStepTapped: (SessionStatus) -> Unit) {
    val steps = listOf(
        SessionStatus.UNREAD to "Unread",
        SessionStatus.IN_PROGRESS to "In Progress",
        SessionStatus.DONE to "Done"
    )
    val linearOrder = mapOf(
        SessionStatus.UNREAD to 0,
        SessionStatus.IN_PROGRESS to 1,
        SessionStatus.DONE to 2
    )
    val currentOrder = linearOrder[status] ?: -1  // OVERDUE → -1, no step highlighted

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (status == SessionStatus.OVERDUE) {
            Surface(
                color = Color(0xFFD32F2F),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "OVERDUE",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, (stepStatus, label) ->
                val stepOrder = linearOrder[stepStatus]!!
                val isActive = status == stepStatus
                val isPast = currentOrder > stepOrder && currentOrder >= 0

                val bg = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isPast   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else     -> Color.Transparent
                }
                val textColor = when {
                    isActive -> MaterialTheme.colorScheme.onPrimary
                    isPast   -> MaterialTheme.colorScheme.primary
                    else     -> MaterialTheme.colorScheme.outline
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .then(
                            if (!isActive && !isPast)
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            else Modifier
                        )
                        .clickable { onStepTapped(stepStatus) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                if (index < steps.lastIndex) {
                    val connectorColor = if (isPast || isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .weight(0.25f)
                            .height(2.dp)
                            .background(connectorColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButtons(
    status: SessionStatus,
    onMarkDone: () -> Unit,
    onHandlingIt: () -> Unit,
    onRemindMe: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = onMarkDone,
            enabled = status != SessionStatus.DONE,
            modifier = Modifier.weight(1f)
        ) {
            Text("Done", maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
            onClick = onHandlingIt,
            enabled = status != SessionStatus.IN_PROGRESS,
            modifier = Modifier.weight(1f)
        ) {
            Text("Handling it", maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
            onClick = onRemindMe,
            modifier = Modifier.weight(1f)
        ) {
            Text("Remind me", maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Confetti overlay
// ─────────────────────────────────────────────────────────────

@Composable
private fun ConfettiOverlay(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 3000))
        onEnd()
    }
    val colors = remember {
        listOf(
            Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4CAF50),
            Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF00BCD4)
        )
    }
    val particles = remember {
        List(60) { Triple(Random.nextFloat(), Random.nextFloat() * 0.3f, Random.nextFloat()) }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val p = progress.value
        particles.forEachIndexed { i, (xFrac, yStart, phase) ->
            val alpha = if (p > 0.7f) (1f - (p - 0.7f) / 0.3f).coerceIn(0f, 1f) else 1f
            val x = xFrac * size.width + sin((p * 3f + phase) * Math.PI.toFloat()) * 24f
            val y = yStart * size.height + p * size.height * 0.8f
            drawCircle(
                color = colors[i % colors.size].copy(alpha = alpha),
                radius = 6f * (1f - p * 0.3f),
                center = Offset(x, y)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Q&A section
// ─────────────────────────────────────────────────────────────

@Composable
private fun QASection(
    chatMessages: List<ChatMessage>,
    streamingText: String,
    isLoading: Boolean,
    onSendQuestion: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(chatMessages.isNotEmpty()) }
    var inputText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            // Header row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ask a follow-up question",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    // Chat history bubbles
                    chatMessages.forEach { msg ->
                        ChatBubble(message = msg)
                        Spacer(Modifier.height(4.dp))
                    }

                    // Streaming or loading bubble
                    if (isLoading) {
                        if (streamingText.isNotEmpty()) {
                            StreamingBubble(text = streamingText)
                        } else {
                            LoadingBubble()
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    // Input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "Ask anything about this document…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (inputText.isNotBlank() && !isLoading) {
                                    onSendQuestion(inputText.trim())
                                    inputText = ""
                                }
                            })
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() && !isLoading) {
                                    onSendQuestion(inputText.trim())
                                    inputText = ""
                                }
                            },
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
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp, topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 4.dp,
                    bottomEnd   = if (isUser) 4.dp  else 12.dp
                ),
                color = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
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
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Existing result card composables (unchanged)
// ─────────────────────────────────────────────────────────────

@Composable
private fun UrgencyBanner(urgencyLevel: UrgencyLevel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = urgencyLevel.toColor()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (urgencyLevel == UrgencyLevel.RED || urgencyLevel == UrgencyLevel.YELLOW) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = urgencyLevel.toLabel(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ConfidenceBanner(confidence: Confidence, onRetake: () -> Unit) {
    when (confidence) {
        Confidence.MEDIUM -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF8E1)
            ) {
                Text(
                    text = "ℹ️ Some parts of this document were unclear. Review the results carefully.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D4037),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        Confidence.LOW -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRetake() },
                color = Color(0xFFFBE9E7)
            ) {
                Text(
                    text = "⚠️ Low image quality detected. Results may be inaccurate. Tap to retake.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBF360C),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun DocumentTypeBadge(documentType: String) {
    val label = documentType.replace("_", " ")
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}

@Composable
private fun SummaryCard(summary: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            summary.forEach { bullet ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                    )
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsCard(actions: List<ActionItem>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What you need to do",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            actions.forEach { action ->
                ActionRow(action = action)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(action: ActionItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = action.urgency.toColor(),
                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
            )
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        action.deadline?.let { deadline ->
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.padding(start = 16.dp)) {
                DeadlineChip(title = action.description, deadline = deadline)
            }
        }
    }
}

@Composable
private fun DeadlineChip(title: String, deadline: String) {
    val isUrgent = isDeadlineWithin7Days(deadline)
    val context = LocalContext.current
    SuggestionChip(
        onClick = { addToCalendar(context, title, deadline) },
        label = { Text("Due $deadline") },
        icon = {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Add to calendar",
                modifier = Modifier.size(16.dp)
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (isUrgent) UrgencyRed.copy(alpha = 0.12f)
                             else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (isUrgent) UrgencyRed
                         else MaterialTheme.colorScheme.onSurfaceVariant,
            iconContentColor = if (isUrgent) UrgencyRed
                               else MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

private fun addToCalendar(context: android.content.Context, title: String, deadline: String) {
    val epochMillis = runCatching {
        LocalDate.parse(deadline)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull() ?: return

    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, epochMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, epochMillis + 3_600_000L)
        putExtra(CalendarContract.Events.ALL_DAY, true)
        putExtra(CalendarContract.Events.DESCRIPTION, "Added by ClaireDoc")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

private fun launchAlarmIntent(context: android.content.Context) {
    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.HOUR_OF_DAY, 1) }
    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_MESSAGE, "ClaireDoc reminder")
        putExtra(AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
        putExtra(AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
}

@Composable
private fun RisksCard(risks: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)  // amber-50 equivalent
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF57C00),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Risks & Warnings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4E2600)
                )
            }
            Spacer(Modifier.height(12.dp))
            risks.forEach { risk ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF57C00),
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                    )
                    Text(
                        text = risk,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4E2600)
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenFAB(isSpeaking: Boolean, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(
            imageVector = if (isSpeaking) Icons.Default.MicOff else Icons.Default.RecordVoiceOver,
            contentDescription = if (isSpeaking) "Stop reading" else "Listen"
        )
    }
}

@Composable
private fun EmptyResultContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No document analysed yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Glossary
// ─────────────────────────────────────────────────────────────

@Composable
private fun GlossaryChipsRow(
    terms: List<GlossaryTerm>,
    onTermTapped: (GlossaryTerm) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Tap to understand",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            terms.forEach { term ->
                SuggestionChip(
                    onClick = { onTermTapped(term) },
                    label = { Text(term.term) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermExplanationBottomSheet(
    term: GlossaryTerm,
    onListen: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = term.term,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = term.plainExplanation,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onListen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                    Text("Listen")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Got it")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Email card
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailCard(
    emailAddresses: List<String>,
    selectedEmail: String,
    uiState: EmailDraftUiState,
    onSelectEmail: (String) -> Unit,
    onGenerateDraft: (String) -> Unit,
    onConfirmRegenerate: (String) -> Unit,
    onUpdateBody: (String) -> Unit,
    onReset: () -> Unit,
    onCopyToClipboard: (EmailDraft) -> Unit,
    onOpenEmailApp: (EmailDraft) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var userIntent by rememberSaveable { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val isGenerating = uiState is EmailDraftUiState.Generating
    val readyState = uiState as? EmailDraftUiState.Ready
    val errorMessage = (uiState as? EmailDraftUiState.Error)?.message

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Regenerate draft?") },
            text = { Text("Your edits will be lost. Generate a new draft?") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmRegenerate(userIntent)
                    showConfirmDialog = false
                }) { Text("Regenerate") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            // ── Header — always visible ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📧", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Reply to this document",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

                    // ── Recipient ────────────────────────────────────────
                    Text(
                        text = "To:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    if (emailAddresses.size > 1) {
                        // Multiple addresses: show selectable chips
                        emailAddresses.chunked(2).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chunk.forEach { email ->
                                    FilterChip(
                                        selected = email == selectedEmail,
                                        onClick = { onSelectEmail(email) },
                                        label = {
                                            Text(
                                                email,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // pad last row if odd number of emails
                                if (chunk.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        Text(
                            text = selectedEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── User intent ──────────────────────────────────────
                    OutlinedTextField(
                        value = userIntent,
                        onValueChange = { userIntent = it },
                        label = { Text("What do you want to say?") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating,
                        maxLines = 3
                    )

                    Spacer(Modifier.height(8.dp))

                    // Suggestion chips — 2 per row
                    val suggestions = listOf(
                        "I want to dispute this",
                        "I need more time to pay",
                        "I have already paid",
                        "I need clarification"
                    )
                    suggestions.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { userIntent = suggestion },
                                    label = {
                                        Text(
                                            suggestion,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Generate button ──────────────────────────────────
                    Button(
                        onClick = {
                            if (readyState?.isDirty == true) {
                                showConfirmDialog = true
                            } else {
                                onGenerateDraft(userIntent)
                            }
                        },
                        enabled = !isGenerating && userIntent.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate draft")
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Generating indicator ─────────────────────────────
                    AnimatedVisibility(visible = isGenerating) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Drafting email…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── Draft display ────────────────────────────────────
                    AnimatedVisibility(visible = readyState != null) {
                        readyState?.let { state ->
                            Column {
                                Text(
                                    text = "Subject: ${state.draft.subject}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = state.draft.body,
                                    onValueChange = onUpdateBody,
                                    label = { Text("Email body (you can edit)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 6,
                                    maxLines = 12
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { onOpenEmailApp(state.draft) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "Open in email app",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onCopyToClipboard(state.draft) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Copy", maxLines = 1)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    // ── Error state ──────────────────────────────────────
                    AnimatedVisibility(visible = errorMessage != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            errorMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            TextButton(onClick = onReset) {
                                Text("Try again")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Utilities
// ─────────────────────────────────────────────────────────────

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTimestamp(epochMs: Long): String = TIME_FMT.format(Date(epochMs))

/** Returns true if [deadline] (YYYY-MM-DD) is today or within the next 7 days. */
private fun isDeadlineWithin7Days(deadline: String): Boolean {
    return try {
        val date = LocalDate.parse(deadline)
        val today = LocalDate.now()
        !date.isBefore(today) && date.isBefore(today.plusDays(8))
    } catch (_: DateTimeParseException) {
        false
    }
}
