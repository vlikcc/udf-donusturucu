package com.velikececi.udfdonusturucu.ui.tools.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.velikececi.udfdonusturucu.core.tools.TemplateEngine
import com.velikececi.udfdonusturucu.core.tools.TemplateLibrary
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.components.DocumentActionButton
import java.io.File

/**
 * TemplateFormView.swift karşılığı. [templateId] bulunamazsa (beklenmeyen durum) geri döner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateFormScreen(
    container: AppContainer,
    templateId: String,
    onBack: () -> Unit,
    onOpenPreview: (File) -> Unit,
) {
    val template = remember(templateId) { TemplateLibrary.all.firstOrNull { it.id == templateId } }
    if (template == null) {
        onBack()
        return
    }

    val context = LocalContext.current
    val viewModel: TemplateFormViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TemplateFormViewModel(context.applicationContext, container.historyRepository, template) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var showPreview by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template.title) },
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
            Text("Bilgiler", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

            template.fields.forEach { field ->
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = field.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.values[field.key].orEmpty(),
                        onValueChange = { viewModel.setValue(field.key, it) },
                        placeholder = { Text(field.placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = if (field.multiline) 4 else 1,
                    )
                }
            }

            OutlinedButton(onClick = { showPreview = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Metni Önizle")
            }

            Button(
                onClick = { viewModel.createUdf() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !uiState.isWorking,
            ) {
                Text("UDF Oluştur")
            }

            Button(
                onClick = { viewModel.createPdf() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                enabled = !uiState.isWorking,
            ) {
                Text("PDF Oluştur")
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

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("Metin Önizleme") },
            text = {
                Text(
                    text = TemplateEngine.fill(template, uiState.values),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showPreview = false }) { Text("Kapat") }
            },
        )
    }
}
