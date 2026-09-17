package com.velikececi.udfdonusturucu.ui.conversion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ConversionView.swift karşılığı. Gerçek ilerleme [ConversionFlowViewModel.state]'ten okunur;
 * dönüşüm bittiğinde (outcomes doldurulduğunda) otomatik olarak Sonuç ekranına geçilir.
 */
@Composable
fun ConversionScreen(flowViewModel: ConversionFlowViewModel, onFinished: () -> Unit) {
    val state by flowViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        flowViewModel.startConversion()
    }

    LaunchedEffect(state.outcomes) {
        if (state.outcomes.isNotEmpty()) {
            onFinished()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))

            val progress = state.progress
            if (progress != null) {
                Text(
                    text = "${progress.currentIndex} / ${progress.total} — ${progress.currentFileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                LinearProgressIndicator(
                    progress = { progress.currentIndex / progress.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "Dönüştürülüyor…",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}
