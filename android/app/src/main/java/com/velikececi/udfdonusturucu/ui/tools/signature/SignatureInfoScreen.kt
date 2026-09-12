package com.velikececi.udfdonusturucu.ui.tools.signature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.velikececi.udfdonusturucu.data.FileCopier
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SignatureInfoView.swift karşılığı. Salt bilgi amaçlıdır — dosya üretmez, geçmişe yazmaz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SignatureInfoViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        FileCopier.copyToCache(context, uri)?.let(viewModel::inspect)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("E-İmza Bilgisi") },
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
                onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Text("  UDF Dosyası Seç")
            }

            Text(
                text = "Bilgi amaçlıdır; imzanın hukuki geçerliliği veya sertifika zinciri doğrulanmaz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }

            uiState.result?.let { result ->
                if (result.hasSignature) {
                    if (result.certificates.isNotEmpty()) {
                        Text(
                            text = "Sertifikalar",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        result.certificates.forEach { cert ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null)
                                Text("  ${cert.subjectSummary}")
                            }
                        }
                    }

                    if (result.signingDates.isNotEmpty()) {
                        Text(
                            text = "İmza Zamanı",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                        val formatter = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr")) }
                        result.signingDates.forEach { date ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null)
                                Text("  ${formatter.format(date)}")
                            }
                        }
                    }

                    if (result.certificates.isEmpty() && result.signingDates.isEmpty()) {
                        Text(
                            text = "İmza verisi bulundu ancak sertifika bilgisi okunamadı. " +
                                "Dosya farklı bir imza biçimi kullanıyor olabilir.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Text(
                        text = "İmza Dosyaları",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    result.signatureEntryNames.forEach { name ->
                        Text(name, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Draw,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "İmza Bulunamadı",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                text = "Bu UDF dosyasında elektronik imza verisi tespit edilemedi.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
