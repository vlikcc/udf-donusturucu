package com.velikececi.udfdonusturucu.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.velikececi.udfdonusturucu.BuildConfig
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.review.InAppReview
import com.velikececi.udfdonusturucu.util.findActivity
import kotlinx.coroutines.launch

/** SettingsView.swift karşılığı — bölüm sırası ve içerikleri birebir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onNavigatePaywall: (source: String) -> Unit,
    onNavigatePrivacy: () -> Unit,
    onNavigateTerms: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val limitState by container.limitRepository.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // MARK: - Hesap Durumu
            item {
                ListItem(
                    headlineContent = { Text(if (limitState.isPremium) "Premium Kullanıcı" else "Ücretsiz Kullanıcı") },
                    supportingContent = {
                        if (!limitState.isPremium) {
                            Text("Günlük ${limitState.remainingConversions} dönüşüm hakkı kaldı")
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
                )
            }
            if (!limitState.isPremium) {
                item {
                    ListItem(
                        modifier = Modifier.clickable(onClick = { onNavigatePaywall("settings") }),
                        headlineContent = { Text("Premium'a Yükselt") },
                        leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                    )
                }
            }

            // MARK: - Uygulama
            item {
                ListItem(
                    headlineContent = { Text("Sürüm") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Geliştirici") },
                    supportingContent = { Text("Veli KEÇECİ") },
                )
            }

            // MARK: - Gizlilik ve Yasal
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onNavigatePrivacy),
                    headlineContent = { Text("Gizlilik Politikası") },
                    leadingContent = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onNavigateTerms),
                    headlineContent = { Text("Kullanım Koşulları") },
                    leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                )
            }

            // MARK: - Destek
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@velikececi.com"))
                        runCatching { context.startActivity(intent) }
                    },
                    headlineContent = { Text("Bize Yazın") },
                    leadingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        context.findActivity()?.let { InAppReview.request(it) }
                    },
                    headlineContent = { Text("Uygulamayı Değerlendirin") },
                    leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                )
            }

            // MARK: - Geri Yükle
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        scope.launch {
                            val found = container.billingManager.restorePurchases()
                            container.analytics.restoreCompleted(found)
                            Toast.makeText(
                                context,
                                if (found) "Satın alma geri yüklendi." else "Geri yüklenecek bir satın alma bulunamadı.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    headlineContent = { Text("Satın Almaları Geri Yükle") },
                    leadingContent = { Icon(Icons.Filled.Restore, contentDescription = null) },
                )
            }
        }
    }
}
