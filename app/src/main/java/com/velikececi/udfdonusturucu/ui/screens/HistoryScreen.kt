package com.velikececi.udfdonusturucu.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.velikececi.udfdonusturucu.ui.components.AdBanner
import com.velikececi.udfdonusturucu.ui.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val history by viewModel.conversionHistory.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dönüştürme Geçmişi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllHistory() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Tümünü Temizle")
                        }
                    }
                }
            )
        },
        bottomBar = {
            AdBanner(isPremium = isPremium)
        }
    ) { paddingValues ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz dönüştürülmüş bir belge yok.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.outputFileName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = item.conversionType,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Kaynak: ${item.sourceFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = dateFormat.format(Date(item.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = {
                                    val file = File(item.outputFilePath)
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "*/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Paylaş"))
                                    }
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Paylaş")
                                }

                                IconButton(onClick = { viewModel.deleteHistoryItem(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
