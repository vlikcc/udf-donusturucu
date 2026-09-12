package com.velikececi.udfdonusturucu.ui.tools.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditBlock
import com.velikececi.udfdonusturucu.data.FileCopier
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.components.DocumentActionButton
import com.velikececi.udfdonusturucu.ui.tools.editor.richtext.EditorFocusCoordinator
import com.velikececi.udfdonusturucu.ui.tools.editor.richtext.FormattingToolbar
import kotlinx.coroutines.launch
import java.io.File

/**
 * UDFEditorView.swift karşılığı. Ekran ilk açıldığında iOS gibi dosya seçici otomatik açılır;
 * kaydedilmemiş değişiklik varken dosya değiştirilmek istenirse onay istenir; e-imzalı dosyada
 * turuncu uyarı banner'ı gösterilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdfEditorScreen(container: AppContainer, onBack: () -> Unit, onOpenPreview: (File) -> Unit) {
    val context = LocalContext.current
    val viewModel: UdfEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { UdfEditorViewModel(context.applicationContext, container.historyRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val coordinator = remember { EditorFocusCoordinator() }
    val scope = rememberCoroutineScope()

    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showInsertField by remember { mutableStateOf(false) }
    var fieldNameInput by remember { mutableStateOf("") }
    var didAutoPresentPicker by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            FileCopier.copyToCache(context, uri)?.let(viewModel::load)
        }
    }

    LaunchedEffect(Unit) {
        if (!didAutoPresentPicker) {
            didAutoPresentPicker = true
            pickerLauncher.launch(arrayOf("*/*"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.editable?.sourceFile?.name ?: "UDF Düzenleme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (uiState.editable != null) {
                        IconButton(
                            onClick = {
                                if (uiState.hasChanges) showDiscardConfirm = true else pickerLauncher.launch(arrayOf("*/*"))
                            },
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Dosya Değiştir")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.document != null) {
                FormattingToolbar(coordinator = coordinator, onInsertField = { showInsertField = true })
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val document = uiState.document
            when {
                uiState.isLoading -> LoadingState()
                document == null -> EmptyState(
                    errorMessage = uiState.errorMessage,
                    onPick = { pickerLauncher.launch(arrayOf("*/*")) },
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.editable?.hasSignature == true) {
                        Surface(color = Color(0xFFFFF3E0), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Bu dosyada elektronik imza var. Kaydettiğinizde imza geçersiz olur.",
                                modifier = Modifier.padding(12.dp),
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        item {
                            HeaderFooterSection(
                                title = "Üst Bilgi (Header)",
                                items = document.headers,
                                coordinator = coordinator,
                                onAdd = viewModel::addHeader,
                                onRemove = viewModel::removeHeader,
                                onAddParagraph = viewModel::addHeaderParagraph,
                                onItemChange = viewModel::updateHeader,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }

                        itemsIndexed(document.blocks, key = { _, block -> block.id }) { index, block ->
                            when (block) {
                                is UdfEditBlock.ParagraphBlock -> ParagraphBlockEditor(
                                    paragraph = block.paragraph,
                                    coordinator = coordinator,
                                    onChange = { updated -> viewModel.updateBlock(index, UdfEditBlock.ParagraphBlock(updated)) },
                                    onRemove = { viewModel.removeBlock(index) },
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                is UdfEditBlock.TableBlock -> TableBlockEditor(
                                    table = block.table,
                                    coordinator = coordinator,
                                    onTableChange = { updated -> viewModel.updateBlock(index, UdfEditBlock.TableBlock(updated)) },
                                    onAddRow = { viewModel.addTableRow(index) },
                                    onAddColumn = { viewModel.addTableColumn(index) },
                                    onRemoveRow = { rowIndex -> viewModel.removeTableRow(index, rowIndex) },
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }

                        item {
                            HeaderFooterSection(
                                title = "Alt Bilgi (Footer)",
                                items = document.footers,
                                coordinator = coordinator,
                                onAdd = viewModel::addFooter,
                                onRemove = viewModel::removeFooter,
                                onAddParagraph = viewModel::addFooterParagraph,
                                onItemChange = viewModel::updateFooter,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                OutlinedButton(onClick = viewModel::addParagraphBlock, modifier = Modifier.weight(1f)) {
                                    Text("Paragraf")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(onClick = viewModel::addTableBlock, modifier = Modifier.weight(1f)) {
                                    Text("Tablo")
                                }
                            }
                        }

                        uiState.errorMessage?.let { message ->
                            item {
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        uiState.savedFile?.let { result ->
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
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

                    Surface(tonalElevation = 1.dp) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (uiState.hasChanges) {
                                Text(
                                    text = "Kaydedilmemiş değişiklikler var.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Button(
                                onClick = viewModel::save,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                enabled = uiState.canSave,
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Text("UDF Olarak Kaydet")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Değişiklikler Kaybolacak") },
            text = { Text("Kaydedilmemiş değişiklikleriniz var. Yine de dosya değiştirilsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    viewModel.reset()
                    pickerLauncher.launch(arrayOf("*/*"))
                }) { Text("Dosya Değiştir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Vazgeç") }
            },
        )
    }

    if (showInsertField) {
        AlertDialog(
            onDismissRequest = { showInsertField = false },
            title = { Text("UYAP Alanı Ekle") },
            text = {
                OutlinedTextField(
                    value = fieldNameInput,
                    onValueChange = { fieldNameInput = it.uppercase() },
                    placeholder = { Text("Alan adı (ör. TARIH, AD_SOYAD)") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fieldNameInput.isNotBlank()) {
                        coordinator.activeHandle?.markField(fieldNameInput)
                    }
                    showInsertField = false
                    fieldNameInput = ""
                }) { Text("Ekle") }
            },
            dismissButton = {
                TextButton(onClick = { showInsertField = false; fieldNameInput = "" }) { Text("İptal") }
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Belge yükleniyor...", modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun EmptyState(errorMessage: String?, onPick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("UDF Seçin", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                text = "Düzenlemek istediğiniz UDF dosyasını seçin.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = onPick, modifier = Modifier.padding(top = 16.dp)) {
                Text("UDF Dosyası Seç")
            }
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
