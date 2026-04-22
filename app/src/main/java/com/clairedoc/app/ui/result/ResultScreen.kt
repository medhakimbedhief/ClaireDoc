package com.clairedoc.app.ui.result

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
import androidx.compose.foundation.lazy.LazyColumn
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.data.model.ActionItem
import com.clairedoc.app.data.model.DocumentResult
import com.clairedoc.app.data.model.UrgencyLevel
import com.clairedoc.app.ui.NavRoutes
import com.clairedoc.app.ui.theme.UrgencyRed
import com.clairedoc.app.ui.theme.toColor
import com.clairedoc.app.ui.theme.toContainerColor
import com.clairedoc.app.ui.theme.toLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    resultJson: String,  // unused here — ViewModel reads from SavedStateHandle
    navController: NavController,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                onScanAnother = {
                    navController.popBackStack(NavRoutes.HOME, inclusive = false)
                }
            )
        }
    }
}

@Composable
private fun ResultContent(
    result: DocumentResult,
    paddingValues: PaddingValues,
    onScanAnother: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(bottom = 88.dp)  // room for FAB
    ) {
        item { UrgencyBanner(urgencyLevel = result.urgencyLevel) }
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
        if (result.actions.isNotEmpty()) {
            item { ActionsCard(actions = result.actions) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (result.risks.isNotEmpty()) {
            item { RisksCard(risks = result.risks) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        item {
            OutlinedButton(
                onClick = onScanAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Done")
            }
        }
    }
}

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
