package com.velikececi.udfdonusturucu.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.billing.PaywallPlan
import com.velikececi.udfdonusturucu.billing.PlanBadge
import com.velikececi.udfdonusturucu.billing.PurchaseState
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.util.findActivity
import kotlinx.coroutines.launch

private data class FeatureChip(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

// PaywallView.swift "Pro ile neler var?" bölümündeki 6 özellik çipi — metinler birebir.
private val features = listOf(
    FeatureChip(Icons.Filled.AllInclusive, "Sınırsız dönüştürme"),
    FeatureChip(Icons.Filled.Block, "Reklamsız"),
    FeatureChip(Icons.Filled.Check, "Toplu dönüştürme"),
    FeatureChip(Icons.Filled.WorkspacePremium, "Pro Araçlar"),
    FeatureChip(Icons.Filled.EditNote, "UDF düzenleme"),
    FeatureChip(Icons.Filled.History, "30 gün geçmiş"),
)

/**
 * PaywallView.swift / PurchaseService.swift karşılığı — 3 planlı yapı (Aylık/Yıllık abonelik +
 * Ömür Boyu tek seferlik), gerçek Play Billing akışına bağlı.
 *
 * [source]: hangi akıştan açıldığı (`onboarding`, `limit_card`, `limit_alert`, `batch`,
 * `result_limit`, `history`, `settings`, `tools`, `tools_<toolId>`) — iOS `PaywallView(source:)`
 * ile aynı sözleşme; `paywall_shown`/`paywall_plan_selected`/`purchase_*` olaylarına eklenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(container: AppContainer, source: String = "unknown", onBack: () -> Unit) {
    val billingState by container.billingManager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(source) {
        container.analytics.paywallShown(source)
    }

    // Aynı durumun tekrar tekrar loglanmasını önlemek için yalnızca gerçek bir geçişte
    // (idle/loading → purchased/failed) event gönderilir.
    var lastLoggedState by remember { mutableStateOf<PurchaseState?>(null) }
    LaunchedEffect(billingState.purchaseState, billingState.selectedProductId) {
        val current = billingState.purchaseState
        if (current != lastLoggedState) {
            val productId = billingState.selectedProductId
            when (current) {
                PurchaseState.PURCHASED -> if (productId != null) {
                    container.analytics.purchaseCompleted(
                        productId = productId,
                        priceDisplay = billingState.plans.firstOrNull { it.productId == productId }?.formattedPrice,
                        source = source,
                    )
                }
                PurchaseState.FAILED -> container.analytics.purchaseFailed(
                    productId = productId,
                    reason = billingState.errorMessage ?: "unknown_error",
                    source = source,
                )
                else -> Unit
            }
            lastLoggedState = current
        }
        if (current == PurchaseState.PURCHASED) {
            onBack()
        }
    }

    val selectedPlan = billingState.plans.firstOrNull { it.productId == billingState.selectedProductId }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValuesCompat(horizontal = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .size(48.dp),
                    )
                    Text(
                        text = "Evrak Dönüştürücü Pro",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = "Sınırsız dönüştürme, reklamsız deneyim ve tüm Pro araçlar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                    )
                }
            }

            item {
                Text(
                    text = "PLAN SEÇİN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            if (billingState.plans.isEmpty() && billingState.purchaseState == PurchaseState.LOADING) {
                item {
                    Text(
                        text = "Planlar yükleniyor…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(billingState.plans, key = { it.productId }) { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan.productId == billingState.selectedProductId,
                    onClick = {
                        container.billingManager.selectPlan(plan.productId)
                        container.analytics.paywallPlanSelected(plan.productId, source)
                    },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            item {
                Text(
                    text = "PRO İLE NELER VAR?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                )
            }

            item {
                // LazyVerticalGrid iç içe LazyColumn'da çalışmaz; 2 sütunlu sabit grid elle örülür.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    features.chunked(2).forEach { rowFeatures ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowFeatures.forEach { feature ->
                                FeatureRow(feature, modifier = Modifier.weight(1f))
                            }
                            if (rowFeatures.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                if (billingState.errorMessage != null) {
                    Text(
                        text = billingState.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                Button(
                    onClick = {
                        val activity = context.findActivity() ?: return@Button
                        val productId = selectedPlan?.productId ?: return@Button
                        container.analytics.purchaseStarted(productId, source)
                        scope.launch { container.billingManager.purchase(activity, productId) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    enabled = billingState.purchaseState != PurchaseState.LOADING && selectedPlan != null,
                ) {
                    if (billingState.purchaseState == PurchaseState.LOADING) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(
                            when {
                                selectedPlan == null -> "Devam Et"
                                !selectedPlan.isSubscription -> "Ömür Boyu Satın Al"
                                else -> "Pro'ya Geç"
                            },
                        )
                    }
                }

                if (selectedPlan?.isSubscription == true) {
                    Text(
                        text = "Play Store > Abonelikler bölümünden istediğiniz zaman iptal edebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                Text(
                    text = "Abonelikler, iptal edilmediği sürece otomatik olarak yenilenir. " +
                        "Satın alma işlemi Google Hesabınız üzerinden gerçekleştirilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                TextButton(
                    onClick = {
                        scope.launch {
                            val found = container.billingManager.restorePurchases()
                            container.analytics.restoreCompleted(found)
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Geri Yükle")
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PaywallPlan,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    plan.badge?.let { badge ->
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        BadgeChip(badge)
                    }
                }
                Text(
                    text = plan.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (plan.strikethroughPrice != null) {
                    Text(
                        text = plan.strikethroughPrice,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = plan.formattedPrice,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (plan.periodSuffix != null) {
                        Text(
                            text = " ${plan.periodSuffix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (plan.savingsPercent != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = "-%${plan.savingsPercent}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(badge: PlanBadge) {
    val (label, color) = when (badge) {
        PlanBadge.EN_AVANTAJLI -> "EN AVANTAJLI" to Color(0xFFEF6C00)
        PlanBadge.TEK_SEFER -> "TEK SEFER" to MaterialTheme.colorScheme.primary
    }
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FeatureRow(feature: FeatureChip, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            feature.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = feature.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** [androidx.compose.foundation.layout.PaddingValues] için kısa yardımcı — okunabilirlik. */
private fun PaddingValuesCompat(horizontal: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp) =
    androidx.compose.foundation.layout.PaddingValues(start = horizontal, end = horizontal, top = 0.dp, bottom = bottom)
