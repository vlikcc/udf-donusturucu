package com.velikececi.udfdonusturucu.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** HistoryView.swift karşılığı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPreview: (String) -> Unit,
    onNavigatePaywall: (source: String) -> Unit = {},
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(container.historyRepository, container.limitRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    fun persistToUri(uri: android.net.Uri?) {
        val file = pendingSaveFile
        pendingSaveFile = null
        if (uri == null || file == null) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }
        }
    }

    val pdfSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { persistToUri(it) }
    val docxSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)) { persistToUri(it) }
    val udfSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { persistToUri(it) }

    fun triggerSave(file: File) {
        pendingSaveFile = file
        when (file.extension.lowercase(Locale.ROOT)) {
            "pdf" -> pdfSaveLauncher.launch(file.name)
            "docx" -> docxSaveLauncher.launch(file.name)
            else -> udfSaveLauncher.launch(file.name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geçmiş") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (uiState.available.isNotEmpty() || uiState.unavailable.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Text("Temizle", color = MaterialTheme.colorScheme.error)
                        }
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
            if (!uiState.isPremium) {
                item {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        ListItem(
                            modifier = Modifier.clickable { onNavigatePaywall("history") },
                            headlineContent = { Text("Pro ile geçmişiniz 30 gün saklanır") },
                            supportingContent = { Text("Ücretsiz sürümde geçmiş 7 gün sonra silinir.") },
                            leadingContent = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
                            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            if (uiState.available.isNotEmpty()) {
                item { SectionHeader("Dosyalar (${uiState.available.size})") }
                items(uiState.available, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        enabled = true,
                        onClick = { onOpenPreview(record.id) },
                        onShare = { record.resolvedFile(context)?.let { ShareUtils.shareFile(context, it) } },
                        onSave = { record.resolvedFile(context)?.let { triggerSave(it) } },
                        onDelete = { viewModel.delete(record) },
                    )
                }
            }
            if (uiState.unavailable.isNotEmpty()) {
                item { SectionHeader("Diğer") }
                items(uiState.unavailable, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        enabled = false,
                        onClick = {},
                        onShare = null,
                        onSave = null,
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
    onSave: (() -> Unit)?,
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
            val dateText = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr")).format(Date(record.dateEpochMillis))
            Text(
                if (enabled) {
                    "${record.outputFormat.uppercase(Locale.ROOT)} · $dateText"
                } else if (!record.success) {
                    "$dateText · Başarısız"
                } else {
                    "$dateText · Dosya silinmiş"
                },
                color = if (!enabled) {
                    if (record.success) Color(0xFFEF6C00) else MaterialTheme.colorScheme.error
                } else {
                    Color.Unspecified
                },
            )
        },
        trailingContent = {
            Row {
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Paylaş")
                    }
                }
                if (onSave != null) {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "Kaydet", tint = Color(0xFF2E7D32))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Sil")
                }
            }
        },
    )
}
