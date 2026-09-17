package com.velikececi.udfdonusturucu.ui.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** PrivacyPolicyView/TermsView.swift'teki `Text(...).font(.headline)` + gövde çiftinin karşılığı. */
@Composable
fun LegalSection(heading: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
