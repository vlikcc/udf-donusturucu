package com.velikececi.udfdonusturucu.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPreview: (String) -> Unit,
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(container.historyRepository, container.limitRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geçmiş") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.available.isNotEmpty()) {
                item { SectionHeader("Kullanılabilir") }
                items(uiState.available, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        enabled = true,
                        onClick = { onOpenPreview(record.id) },
                        onShare = { record.resolvedFile(context)?.let { ShareUtils.shareFile(context, it) } },
                        onDelete = { viewModel.delete(record) },
                    )
                }
            }
            if (uiState.unavailable.isNotEmpty()) {
                item { SectionHeader("Kullanılamıyor") }
                items(uiState.unavailable, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        enabled = false,
                        onClick = {},
                        onShare = null,
                        onDelete = { viewModel.delete(record) },
                    )
                }
            }
            if (uiState.available.isEmpty() && uiState.unavailable.isEmpty()) {
                item {
                    Text(
                        text = "Henüz dönüşüm geçmişi yok.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun HistoryRow(
    record: ConversionRecord,
    enabled: Boolean,
    onClick: () -> Unit,
    onShare: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = {
            Text(
                record.originalFileName,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Text(
                "${record.outputFormat.uppercase(Locale.ROOT)} · " +
                    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr")).format(Date(record.dateEpochMillis)),
            )
        },
        trailingContent = {
            Row {
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Paylaş")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Sil")
                }
            }
        },
    )
}
