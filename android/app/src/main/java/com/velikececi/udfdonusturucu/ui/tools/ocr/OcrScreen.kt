package com.velikececi.udfdonusturucu.ui.tools.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif", "bmp", "webp", "gif")

/**
 * OCRView.swift karşılığı. PDF veya görüntü seçilir, tamamen cihaz üzerinde metin tanınır,
 * ardından Word/PDF/UDF olarak dışa aktarılır ya da panoya kopyalanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(container: AppContainer, onBack: () -> Unit, onOpenPreview: (File) -> Unit) {
    val context = LocalContext.current
    val viewModel: OcrViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OcrViewModel(context.applicationContext, container.historyRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = FileCopier.copyToCache(context, uri) ?: return@launch
            val isImage = copied.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
            viewModel.selectFile(copied, isImage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metin Tanıma (OCR)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Button(
                onClick = { pickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Text("  PDF veya Görüntü Seç")
            }

            Text(
                text = "Metin tanıma tamamen cihazınızda çalışır; belgeleriniz hiçbir sunucuya gönderilmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )

            uiState.selectedFile?.let { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (uiState.isImage) Icons.Filled.Description else Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                    )
                    Text("  ${file.name}", modifier = Modifier.weight(1f))
                }

                Button(
                    onClick = { viewModel.recognize() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = !uiState.isRecognizing,
                ) {
                    if (uiState.isRecognizing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Text("  ${uiState.progressText ?: "Metin tanınıyor..."}")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Text("  Metni Tanı")
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

            uiState.recognizedText?.let { text ->
                Text(
                    text = "Tanınan Metin",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }

                Text(
                    text = "Dışa Aktar",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Row {
                    OutlinedButton(
                        onClick = { viewModel.export(OcrExportFormat.DOCX) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Word (DOCX)") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.export(OcrExportFormat.PDF) },
                        modifier = Modifier.weight(1f),
                    ) { Text("PDF") }
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.export(OcrExportFormat.UDF) },
                        modifier = Modifier.weight(1f),
                    ) { Text("UDF") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { copyToClipboard(context, text) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Kopyala")
                    }
                }
            }

            uiState.exportedFile?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("OCR Metni", text))
    Toast.makeText(context, "Metin panoya kopyalandı.", Toast.LENGTH_SHORT).show()
}
