package com.velikececi.udfdonusturucu.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.velikececi.udfdonusturucu.UdfApp
import com.velikececi.udfdonusturucu.converter.ConversionDirection
import com.velikececi.udfdonusturucu.converter.ConversionState
import com.velikececi.udfdonusturucu.ui.components.AdBanner
import com.velikececi.udfdonusturucu.ui.components.ConversionCard
import com.velikececi.udfdonusturucu.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    val isPremium by viewModel.isPremium.collectAsState()
    val remainingConversions by viewModel.remainingConversions.collectAsState()
    val conversionState by viewModel.conversionState.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    var selectedDirection by remember { mutableStateOf<ConversionDirection?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val direction = selectedDirection ?: return@let
            viewModel.convertFile(it, direction) {
                onNavigateToPaywall()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evrak Dönüştürücü") },
                actions = {
                    if (!isPremium) {
                        IconButton(
                            onClick = onNavigateToPaywall,
                            modifier = Modifier.testTag("premium_button")
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Premium", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Geçmiş")
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                }
            )
        },
        bottomBar = {
            AdBanner(isPremium = isPremium)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    // Limit ve Durum Kartı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPremium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isPremium) "Sınırsız Premium Paket" else "Günlük Kalan Hak: $remainingConversions",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (isPremium) "Tüm belgeler sınırsız ve reklamsız" else "Her gece yarısı 3 yeni hak tanımlanır",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!isPremium) {
                                Button(
                                    onClick = onNavigateToPaywall,
                                    modifier = Modifier.testTag("upgrade_button")
                                ) {
                                    Text("Yükselt")
                                }
                            }
                        }
                    }
                }

                items(ConversionDirection.values()) { direction ->
                    ConversionCard(
                        direction = direction,
                        onClick = {
                            selectedDirection = direction
                            val mimeTypes = when (direction) {
                                ConversionDirection.UDF_TO_PDF,
                                ConversionDirection.UDF_TO_DOCX -> arrayOf("*/*")
                                ConversionDirection.PDF_TO_UDF -> arrayOf("application/pdf")
                                ConversionDirection.DOCX_TO_UDF -> arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "*/*"
                                )
                            }
                            filePicker.launch(mimeTypes)
                        }
                    )
                }
            }

            // Durum Göstergesi / Dialog
            when (val state = conversionState) {
                is ConversionState.Processing -> {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Dönüştürülüyor") },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(state.message)
                            }
                        },
                        confirmButton = {}
                    )
                }
                is ConversionState.Success -> {
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.resetConversionState()
                        },
                        title = { Text("İşlem Başarılı") },
                        text = {
                            Text("Belgeniz başarıyla dönüştürüldü: ${state.outputFile.name}")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        state.outputFile
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "*/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Paylaş"))
                                    viewModel.resetConversionState()

                                    // Reklam göster
                                    (context.applicationContext as UdfApp).adsManager.showInterstitial(activity) {}
                                }
                            ) {
                                Text("Paylaş / Aç")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                viewModel.resetConversionState()
                                (context.applicationContext as UdfApp).adsManager.showInterstitial(activity) {}
                            }) {
                                Text("Tamam")
                            }
                        }
                    )
                }
                is ConversionState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.resetConversionState() },
                        title = { Text("Hata") },
                        text = { Text(state.errorMessage) },
                        confirmButton = {
                            Button(onClick = { viewModel.resetConversionState() }) {
                                Text("Tamam")
                            }
                        }
                    )
                }
                ConversionState.Idle -> {}
            }
        }
    }
}
