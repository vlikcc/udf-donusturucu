package com.velikececi.udfdonusturucu.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.UdfApp
import com.velikececi.udfdonusturucu.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val billingManager = (context.applicationContext as UdfApp).billingManager
    val price by billingManager.premiumPrice.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sınırsız Evrak Dönüştürme",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Günlük sınırları kaldırın, tüm reklamları engelleyin.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                FeatureRow("Sınırsız günlük dosya dönüştürme")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureRow("Tamamen reklamsız çalışma deneyimi")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureRow("UDF, PDF ve Word (DOCX) tam desteği")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureRow("Tek seferlik ömür boyu erişim")
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (isPremium) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = "Tebrikler! Premium üyeliğiniz aktif.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            billingManager.launchBillingFlow(activity)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("buy_premium_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (price != null) "Satın Al - $price" else "Satın Al",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
