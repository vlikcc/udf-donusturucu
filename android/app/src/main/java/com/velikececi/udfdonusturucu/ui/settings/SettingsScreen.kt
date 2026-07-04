package com.velikececi.udfdonusturucu.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Restore
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
import kotlinx.coroutines.launch

private data class SettingsLink(val label: String, val url: String)

// TODO(Faz 6): web/gizlilik.html, kosullar.html, destek.html yayına alındığında gerçek
// üretim URL'leriyle güncellenecek (Play Console Data Safety / gizlilik politikası URL'si).
private val links = listOf(
    SettingsLink("Gizlilik Politikası", "https://velikececi.com/gizlilik.html"),
    SettingsLink("Kullanım Koşulları", "https://velikececi.com/kosullar.html"),
    SettingsLink("Destek", "https://velikececi.com/destek.html"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onNavigatePaywall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val limitState by container.limitRepository.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Hesap Durumu") },
                    supportingContent = { Text(if (limitState.isPremium) "Premium" else "Ücretsiz") },
                    leadingContent = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
                )
            }

            if (!limitState.isPremium) {
                item {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onNavigatePaywall),
                        headlineContent = { Text("Premium'a Geç") },
                        supportingContent = { Text("Sınırsız dönüşüm ve reklamsız deneyim") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    )
                }
            }

            item {
                ListItem(
                    modifier = Modifier.clickable {
                        scope.launch {
                            val found = container.billingManager.restorePurchases()
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

            items(links) { link ->
                ListItem(
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    },
                    headlineContent = { Text(link.label) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Sürüm") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                )
            }
        }
    }
}
