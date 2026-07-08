package com.velikececi.udfdonusturucu.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.velikececi.udfdonusturucu.data.ConversionDirection
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.FileCopier
import com.velikececi.udfdonusturucu.data.OutputFormat
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.components.BannerAd
import com.velikececi.udfdonusturucu.ui.conversion.ConversionFlowViewModel
import com.velikececi.udfdonusturucu.util.findActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val UDF_MIME_TYPES = arrayOf("*/*")
private val OTHER_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    container: AppContainer,
    flowViewModel: ConversionFlowViewModel,
    onNavigateHistory: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigatePaywall: () -> Unit,
    onNavigateConversion: () -> Unit,
) {
    val viewModel: MainViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MainViewModel(container.limitRepository, container.historyRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val flowState by flowViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val expectedExtensions = if (flowState.direction == ConversionDirection.UDF_TO_OTHER) {
                setOf("udf")
            } else {
                setOf("pdf", "docx")
            }
            val copied = uris.mapNotNull { uri -> FileCopier.copyToCache(context, uri) }
            val matched = copied.filter { it.extension.lowercase(Locale.ROOT) in expectedExtensions }
            flowViewModel.setSelectedFiles(matched)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evrak Dönüştürücü", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DirectionToggle(
                    direction = flowState.direction,
                    onDirectionChange = flowViewModel::setDirection,
                )
            }

            if (flowState.direction == ConversionDirection.UDF_TO_OTHER) {
                item {
                    FormatToggle(
                        format = flowState.outputFormat,
                        onFormatChange = flowViewModel::setOutputFormat,
                    )
                }
            }

            item {
                DailyLimitCard(
                    isPremium = uiState.isPremium,
                    remaining = uiState.remainingConversions,
                    totalAllowed = uiState.totalAllowedConversions,
                    onUpgradeClick = onNavigatePaywall,
                    onWatchAdClick = {
                        context.findActivity()?.let { activity -> container.adsManager.showRewarded(activity) }
                    },
                )
            }

            item {
                val mimeTypes = if (flowState.direction == ConversionDirection.UDF_TO_OTHER) {
                    UDF_MIME_TYPES
                } else {
                    OTHER_MIME_TYPES
                }
                Button(
                    onClick = { pickerLauncher.launch(mimeTypes) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canConvert,
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Text("  Dosya Seç", modifier = Modifier.padding(vertical = 12.dp))
                }
            }

            if (flowState.files.isNotEmpty()) {
                items(flowState.files, key = { it.absolutePath }) { file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        leadingContent = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { flowViewModel.removeFile(file) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Kaldır")
                            }
                        },
                    )
                }
                item {
                    Button(
                        onClick = onNavigateConversion,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.canConvert,
                    ) {
                        Text("Dönüştür (${flowState.files.size})")
                    }
                }
            }

            if (uiState.recentConversions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Son Dönüşümler", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onNavigateHistory) {
                            Icon(Icons.Filled.History, contentDescription = "Tüm geçmiş")
                        }
                    }
                }
                items(uiState.recentConversions, key = { it.id }) { record: ConversionRecord ->
                    ListItem(
                        headlineContent = { Text(record.originalFileName) },
                        supportingContent = {
                            Text(
                                "${record.outputFormat.uppercase(Locale.ROOT)} · " +
                                    SimpleDateFormat("d MMM, HH:mm", Locale("tr"))
                                        .format(Date(record.dateEpochMillis)),
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }

            if (!uiState.isPremium) {
                item { BannerAd() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionToggle(direction: ConversionDirection, onDirectionChange: (ConversionDirection) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = direction == ConversionDirection.UDF_TO_OTHER,
            onClick = { onDirectionChange(ConversionDirection.UDF_TO_OTHER) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("UDF → PDF/Word") }
        SegmentedButton(
            selected = direction == ConversionDirection.OTHER_TO_UDF,
            onClick = { onDirectionChange(ConversionDirection.OTHER_TO_UDF) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("PDF/Word → UDF") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatToggle(format: OutputFormat, onFormatChange: (OutputFormat) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = format == OutputFormat.PDF,
            onClick = { onFormatChange(OutputFormat.PDF) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("PDF") }
        SegmentedButton(
            selected = format == OutputFormat.DOCX,
            onClick = { onFormatChange(OutputFormat.DOCX) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("Word (.docx)") }
    }
}

@Composable
private fun DailyLimitCard(
    isPremium: Boolean,
    remaining: Int,
    totalAllowed: Int,
    onUpgradeClick: () -> Unit,
    onWatchAdClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("GÜNLÜK LİMİT", style = MaterialTheme.typography.labelMedium)
            Text(
                text = if (isPremium) {
                    "Sınırsız (Premium)"
                } else {
                    "$remaining / $totalAllowed kaldı"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!isPremium) {
                LinearProgressIndicator(
                    progress = {
                        remaining.coerceIn(0, totalAllowed) / totalAllowed.toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                if (remaining <= 0) {
                    Button(
                        onClick = onWatchAdClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null)
                        Text("  Reklam İzle, +1 Hak Kazan")
                    }
                }
                Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                    Text("  Premium'a Geç")
                }
            }
        }
    }
}
