package com.velikececi.udfdonusturucu.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.UdfApp
import com.velikececi.udfdonusturucu.ads.ConsentManager
import com.velikececi.udfdonusturucu.ui.components.AdBanner
import com.velikececi.udfdonusturucu.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val consentManager = ConsentManager(activity)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        bottomBar = {
            AdBanner(isPremium = isPremium)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsItem(
                    title = "Premium Üyelik",
                    subtitle = if (isPremium) "Sınırsız sürüm aktif" else "Sınırsız dönüştürme ve reklamsız deneyim",
                    icon = Icons.Default.Star,
                    onClick = onNavigateToPaywall
                )
            }

            if (consentManager.isPrivacyOptionsRequired) {
                item {
                    SettingsItem(
                        title = "Gizlilik ve Çerez Tercihleri",
                        subtitle = "AdMob ve veri paylaşım ayarlarını düzenleyin",
                        icon = Icons.Default.Security,
                        onClick = {
                            consentManager.showPrivacyOptionsForm {}
                        }
                    )
                }
            }

            item {
                SettingsItem(
                    title = "Gizlilik Politikası",
                    subtitle = "Kişisel verilerinizin nasıl korunduğunu inceleyin",
                    icon = Icons.Default.PrivacyTip,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"))
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingsItem(
                    title = "Geliştirici İletişim",
                    subtitle = "vli.kcc@gmail.com",
                    icon = Icons.Default.Email,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:vli.kcc@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Evrak Dönüştürücü Destek")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingsItem(
                    title = "Uygulama Sürümü",
                    subtitle = "1.0.0 (Build 1)",
                    icon = Icons.Default.Info,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
