package com.velikececi.udfdonusturucu.ui.tools.encrypt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.velikececi.udfdonusturucu.data.FileCopier
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import kotlinx.coroutines.launch

/**
 * EncryptView.swift karşılığı. PDF için PDF şifreleme, UDF için uygulama içi UDFENC zarfı
 * üretir; UDFENC seçildiğinde parola ile tekrar UDF'ye dönüştürür.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: EncryptViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EncryptViewModel(context.applicationContext, container.historyRepository) }
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
                title = {
                    Text(
                        when {
                            uiState.isEncryptedUdf -> "UDF Şifresini Çöz"
                            uiState.isUdf -> "UDF Şifreleme"
                            else -> "PDF Şifreleme"
                        },
                    )
                },
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
                val isEncryptedUdf = file.extension.lowercase() == "udfenc"
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isUdf || isEncryptedUdf) Icons.Filled.Description else Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = if (isUdf || isEncryptedUdf) Color(0xFF1C3357) else Color(0xFFE53935),
                    )
                    Text("  ${file.name}", modifier = Modifier.weight(1f))
                }

                Text(
                    text = when {
                        isEncryptedUdf -> "UDFENC dosyasını açmak için şifreleme sırasında kullandığınız parolayı girin."
                        isUdf -> "UDF, UYAP yapısını koruyan uygulama içi .udfenc zarfı ile şifrelenir."
                        else -> "Şifrelenmiş PDF yalnızca belirlediğiniz parolayla açılabilir. " +
                            "Parolayı unutursanız dosya kurtarılamaz."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::setPassword,
                    label = { Text("Parola (en az 4 karakter)") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = uiState.isPasswordTooShort,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.isPasswordTooShort) {
                    Text(
                        text = "Parola en az 4 karakter olmalı.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                OutlinedTextField(
                    value = uiState.passwordConfirm,
                    onValueChange = viewModel::setPasswordConfirm,
                    label = { Text("Parola (tekrar)") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = uiState.passwordsMismatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (uiState.passwordsMismatch) {
                    Text(
                        text = "Parolalar eşleşmiyor.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = { viewModel.encrypt() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    enabled = uiState.canEncrypt,
                ) {
                    if (uiState.isEncrypting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Text("  ${if (isEncryptedUdf) "Açılıyor" else "Şifreleniyor"}...")
                    } else {
                        Text(if (isEncryptedUdf) "UDF'nin Şifresini Çöz" else if (isUdf) "UDF'yi Şifrele" else "PDF'yi Şifrele")
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
                        .padding(top = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Text("  ${result.name}", modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = { ShareUtils.shareFile(context, result) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Text("  Paylaş")
                        }
                    }
                }
            }
        }
    }
}
