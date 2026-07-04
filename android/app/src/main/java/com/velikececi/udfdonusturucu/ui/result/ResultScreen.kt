package com.velikececi.udfdonusturucu.ui.result

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.velikececi.udfdonusturucu.data.ConversionOutcome
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.conversion.ConversionFlowViewModel
import com.velikececi.udfdonusturucu.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/**
 * ResultView.swift karşılığı. Her dosya için başarı/başarısızlık durumu, paylaş
 * ([ShareUtils]/`ACTION_SEND`) ve cihaza kaydet (`CreateDocument`) aksiyonları gösterilir.
 * Ekran açıldığında [com.velikececi.udfdonusturucu.ads.AdsManager.showInterstitialIfDue]
 * çağrılır (her 2. sonuç ekranında bir geçişli reklam — iOS `AdsManager` ile aynı sıklık kuralı).
 */
@Composable
fun ResultScreen(container: AppContainer, flowViewModel: ConversionFlowViewModel, onDone: () -> Unit) {
    val state by flowViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        container.adsManager.showInterstitialIfDue(activity)
    }

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

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> persistToUri(uri) }
    val docxSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DOCX_MIME),
    ) { uri -> persistToUri(uri) }
    val udfSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> persistToUri(uri) }

    fun triggerSave(file: File) {
        pendingSaveFile = file
        when (file.extension.lowercase(Locale.ROOT)) {
            "pdf" -> pdfSaveLauncher.launch(file.name)
            "docx" -> docxSaveLauncher.launch(file.name)
            else -> udfSaveLauncher.launch(file.name)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sonuç") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val successCount = state.outcomes.count { it.success }
            Text(
                text = "$successCount / ${state.outcomes.size} dosya başarıyla dönüştürüldü",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.outcomes) { outcome ->
                    OutcomeRow(
                        outcome = outcome,
                        onShare = { outcome.outputFile?.let { ShareUtils.shareFile(context, it) } },
                        onSave = { outcome.outputFile?.let { triggerSave(it) } },
                    )
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Tamam")
            }
        }
    }
}

@Composable
private fun OutcomeRow(outcome: ConversionOutcome, onShare: () -> Unit, onSave: () -> Unit) {
    ListItem(
        headlineContent = { Text(outcome.originalFileName) },
        supportingContent = {
            Text(outcome.errorMessage ?: if (outcome.success) "Başarılı" else "Başarısız")
        },
        leadingContent = {
            if (outcome.success) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
            } else {
                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        },
        trailingContent = {
            if (outcome.success && outcome.outputFile != null) {
                Row {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Paylaş")
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = "Kaydet")
                    }
                }
            }
        },
    )
}
