package com.velikececi.udfdonusturucu.ui.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.billing.PurchaseState
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.util.findActivity
import kotlinx.coroutines.launch

private val premiumFeatures = listOf(
    "Sınırsız dönüşüm",
    "Reklamsız deneyim",
    "30 günlük geçmiş kaydı",
)

/** PaywallView.swift / PurchaseService.swift karşılığı — gerçek Play Billing akışına bağlı. */
@Composable
fun PaywallScreen(container: AppContainer, onBack: () -> Unit) {
    val billingState by container.billingManager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(billingState.purchaseState) {
        if (billingState.purchaseState == PurchaseState.PURCHASED) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Kapat")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Premium'a Geç",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tek seferlik satın alma, sınırsız kullanım",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )

            premiumFeatures.forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(feature, modifier = Modifier.padding(start = 12.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (billingState.errorMessage != null) {
                Text(
                    text = billingState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Button(
                onClick = {
                    val activity = context.findActivity() ?: return@Button
                    scope.launch { container.billingManager.purchase(activity) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = billingState.purchaseState != PurchaseState.LOADING,
            ) {
                if (billingState.purchaseState == PurchaseState.LOADING) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text(billingState.priceText?.let { "Satın Al — $it" } ?: "Satın Al")
                }
            }
        }
    }
}
