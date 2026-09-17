package com.velikececi.udfdonusturucu.ui.tools.compress

import android.text.format.Formatter
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.velikececi.udfdonusturucu.core.tools.CompressionQuality
import com.velikececi.udfdonusturucu.data.FileCopier
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.components.DocumentActionButton
import kotlinx.coroutines.launch
import java.io.File

/** CompressView.swift karşılığı. PDF veya UDF seçilir ve uygun sıkıştırma uygulanır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(container: AppContainer, onBack: () -> Unit, onOpenPreview: (File) -> Unit) {
    val context = LocalContext.current
    val viewModel: CompressViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CompressViewModel(context.applicationContext, container.historyRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            FileCopier.copyToCache(context, uri)?.let(viewModel::setFile)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.file?.extension?.lowercase() == "udf") "UDF Sıkıştırma" else "PDF Sıkıştırma") },
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
                onClick = { pickerLauncher.launch(arrayOf("application/pdf", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Text("  PDF veya UDF Seç")
            }

            uiState.file?.let { file ->
                val isUdf = file.extension.lowercase() == "udf"
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isUdf) Icons.Filled.Description else Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = if (isUdf) Color(0xFF1C3357) else Color(0xFFE53935),
                    )
                    Text("  ${file.name}", modifier = Modifier.weight(1f))
                }

                Text(
                    text = if (isUdf) {
                        "UDF içindeki XML ve diğer arşiv girdileri yeniden sıkıştırılır; belge yapısı korunur."
                    } else {
                        "Sayfalar sıkıştırılmış görüntülere dönüştürülür; metin seçilebilirliği kaybolur. " +
                            "Taranmış veya çok büyük PDF'ler için uygundur."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                if (!isUdf) {
                    Text(
                        text = "Sıkıştırma Düzeyi",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    CompressionQuality.entries.forEach { quality ->
                        QualityRow(
                            quality = quality,
                            selected = uiState.quality == quality,
                            onClick = { viewModel.setQuality(quality) },
                        )
                    }
                }

                Button(
                    onClick = { viewModel.compress() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    enabled = !uiState.isCompressing,
                ) {
                    if (uiState.isCompressing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Text("  ${if (isUdf) "UDF" else "PDF"} sıkıştırılıyor...")
                    } else {
                        Text(if (isUdf) "UDF'yi Sıkıştır" else "PDF'yi Sıkıştır")
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

            uiState.result?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sonuç", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Önce: ${Formatter.formatShortFileSize(context, result.originalBytes)}",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = "Sonra: ${Formatter.formatShortFileSize(context, result.compressedBytes)}",
                            color = if (uiState.didNotShrink) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color(0xFF2E7D32)
                            },
                        )
                        Text(
                            text = uiState.savingsPercent?.let { "%$it küçüldü" }
                                ?: "Bu dosya daha fazla küçültülemedi.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Row(modifier = Modifier.padding(top = 12.dp)) {
                            DocumentActionButton(
                                icon = Icons.Filled.Visibility,
                                label = "Görüntüle",
                                onClick = { onOpenPreview(result.outputFile) },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DocumentActionButton(
                                icon = Icons.Filled.Share,
                                label = "Paylaş",
                                onClick = { ShareUtils.shareFile(context, result.outputFile) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityRow(quality: CompressionQuality, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(quality.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = quality.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
