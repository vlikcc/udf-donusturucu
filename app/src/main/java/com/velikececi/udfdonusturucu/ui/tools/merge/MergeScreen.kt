package com.velikececi.udfdonusturucu.ui.tools.merge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.velikececi.udfdonusturucu.data.FileCopier
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.components.DocumentActionButton
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * MergeView.swift karşılığı. Birden fazla UDF/PDF tek bir PDF'te birleştirilir; sıra
 * yukarı/aşağı düğmeleriyle değiştirilebilir (iOS'taki sürükle-sırala listenin Compose'da
 * ek bağımlılık gerektirmeyen karşılığı).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(container: AppContainer, onBack: () -> Unit, onOpenPreview: (File) -> Unit) {
    val context = LocalContext.current
    val viewModel: MergeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MergeViewModel(context.applicationContext, container.historyRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = uris.mapNotNull { FileCopier.copyToCache(context, it) }
                .filter { it.extension.lowercase(Locale.ROOT) in setOf("pdf", "udf") }
            viewModel.addFiles(files)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Belge Birleştirme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Button(
                onClick = { pickerLauncher.launch(arrayOf("application/pdf", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Text("  Dosya Ekle (UDF / PDF)")
            }

            if (uiState.files.isNotEmpty()) {
                Text(
                    text = "Birleştirilecek Dosyalar (${uiState.files.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(uiState.files, key = { _, f -> f.absolutePath }) { index, file ->
                        val isUdf = file.extension.lowercase(Locale.ROOT) == "udf"
                        ListItem(
                            headlineContent = { Text(file.name) },
                            leadingContent = {
                                Icon(
                                    if (isUdf) Icons.Filled.Description else Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = if (isUdf) Color(0xFF1C3357) else Color(0xFFE53935),
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { viewModel.moveFile(index, index - 1) },
                                        enabled = index > 0,
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Yukarı taşı")
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveFile(index, index + 1) },
                                        enabled = index < uiState.files.lastIndex,
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Aşağı taşı")
                                    }
                                    IconButton(onClick = { viewModel.removeFile(file) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Kaldır")
                                    }
                                }
                            },
                        )
                    }
                }
                Text(
                    text = "Sıra önemlidir: dosyalar burada göründükleri sırayla birleştirilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (uiState.files.size >= 2) {
                Button(
                    onClick = { viewModel.merge() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = !uiState.isMerging,
                ) {
                    if (uiState.isMerging) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Text("  Birleştiriliyor...")
                    } else {
                        Text("Birleştir")
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            uiState.resultFile?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Text("  ${result.name}", modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.padding(top = 12.dp)) {
                            DocumentActionButton(
                                icon = Icons.Filled.Visibility,
                                label = "Görüntüle",
                                onClick = { onOpenPreview(result) },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DocumentActionButton(
                                icon = Icons.Filled.Share,
                                label = "Paylaş",
                                onClick = { ShareUtils.shareFile(context, result) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
