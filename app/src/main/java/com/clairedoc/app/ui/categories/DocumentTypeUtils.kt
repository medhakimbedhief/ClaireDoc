package com.clairedoc.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.clairedoc.app.data.model.UrgencyLevel

// ── Icon mapping ───────────────────────────────────────────────────────────────

fun String.toIcon(): ImageVector = when (this.uppercase()) {
    "BILL"              -> Icons.Default.Receipt
    "CONTRACT"          -> Icons.Default.Description
    "LEGAL_NOTICE"      -> Icons.Default.Gavel
    "MEDICAL"           -> Icons.Default.LocalHospital
    "TAX"               -> Icons.Default.AccountBalance
    "INSURANCE"         -> Icons.Default.Shield
    "BANK"              -> Icons.Default.CreditCard
    "VISA_IMMIGRATION"  -> Icons.Default.Flight
    "GOVERNMENT_NOTICE" -> Icons.Default.AccountBalance
    "RENTAL"            -> Icons.Default.Home
    else                -> Icons.Default.Article
}

// ── Display name ───────────────────────────────────────────────────────────────

fun String.toDisplayName(): String = this
    .replace("_", " ")
    .lowercase()
    .split(" ")
    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

// ── UrgencyLevel parsing ───────────────────────────────────────────────────────

fun String.toUrgencyLevel(): UrgencyLevel = when (this.uppercase()) {
    "RED"    -> UrgencyLevel.RED
    "YELLOW" -> UrgencyLevel.YELLOW
    else     -> UrgencyLevel.GREEN
}

// ── All selectable document types ─────────────────────────────────────────────

val ALL_DOCUMENT_TYPES = listOf(
    "BILL", "CONTRACT", "LEGAL_NOTICE", "MEDICAL",
    "TAX", "INSURANCE", "BANK", "VISA_IMMIGRATION",
    "GOVERNMENT_NOTICE", "RENTAL", "OTHER"
)

// ── ChangeTypeBottomSheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeTypeBottomSheet(
    currentType: String,
    onTypeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Change document type",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            ALL_DOCUMENT_TYPES.forEach { type ->
                ListItem(
                    headlineContent = { Text(type.toDisplayName()) },
                    leadingContent = {
                        Icon(imageVector = type.toIcon(), contentDescription = null)
                    },
                    trailingContent = {
                        if (type.uppercase() == currentType.uppercase()) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Current type",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        onTypeSelected(type)
                        onDismiss()
                    }
                )
            }
        }
    }
}
