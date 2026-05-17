package com.clairedoc.app.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.clairedoc.app.ui.NavRoutes
import com.clairedoc.app.ui.home.DocumentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredCategoryScreen(
    navController: NavController,
    viewModel: FilteredCategoryViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val isIndexingActive by viewModel.isIndexingActive.collectAsState()
    val docType = viewModel.documentType
    val count = items.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(docType.toDisplayName())
                        Text(
                            text = "$count ${if (count == 1) "document" else "documents"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ${docType.toDisplayName()} documents yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = items, key = { it.session.id }) { item ->
                    DocumentCard(
                        item = item,
                        isIndexingActive = isIndexingActive,
                        onClick = {
                            navController.navigate(
                                NavRoutes.resultRoute(item.resultJson, item.session.id)
                            )
                        },
                        onLongClick = {}   // no long-press actions in this screen
                    )
                }
            }
        }
    }
}
