package com.velikececi.udfdonusturucu.ui.tools.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.core.tools.TemplateLibrary

/** TemplatesView.swift karşılığı — 6 şablonun listesi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit, onOpenTemplate: (templateId: String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dilekçe Şablonları") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(TemplateLibrary.all, key = { it.id }) { template ->
                ListItem(
                    modifier = Modifier.clickable { onOpenTemplate(template.id) },
                    headlineContent = { Text(template.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(template.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                )
                HorizontalDivider()
            }
            item {
                Text(
                    text = "Şablonlar genel bilgilendirme amaçlıdır ve hukuki danışmanlık yerine geçmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
