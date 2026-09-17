package com.velikececi.udfdonusturucu.ui.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** PrivacyPolicyView.swift karşılığı — uygulama içi, tam metin (harici URL yerine). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gizlilik") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Gizlilik Politikası", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Son güncelleme: Mart 2026",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LegalSection(
                heading = "Veri Toplama",
                body = "Evrak Dönüştürücü uygulaması, yüklediğiniz UDF dosyalarının içeriğini hiçbir sunucuya göndermez. " +
                    "Tüm dönüştürme işlemleri tamamen cihazınız üzerinde gerçekleştirilir. Belgeleriniz üçüncü taraflarla paylaşılmaz.",
            )
            LegalSection(
                heading = "Yerel Depolama",
                body = "Dönüştürme geçmişi (yalnızca dosya adı ve tarih bilgisi) cihazınızda yerel olarak saklanır. " +
                    "Bu bilgiler üçüncü taraflarla paylaşılmaz ve yalnızca uygulamanın geçmiş ekranında görülebilir.",
            )
            LegalSection(
                heading = "Uygulama İçi Satın Alma",
                body = "Premium özellikler aylık/yıllık abonelik veya tek seferlik ödeme ile satın alınabilir. " +
                    "Satın alma işlemleri Google Play üzerinden gerçekleştirilir. Ödeme ve işlem bilgileri yalnızca Google tarafından yönetilir; uygulama bu bilgilere erişemez.",
            )
            LegalSection(
                heading = "Analitik Veriler",
                body = "Uygulamayı geliştirmek amacıyla anonim kullanım istatistikleri (örn. ekran görüntülenme ve satın alma olayları) " +
                    "Google Firebase Analytics aracılığıyla toplanabilir. Bu veriler kimliğinizi tanımlamaz ve belge içeriklerinizle ilişkilendirilmez.",
            )
            LegalSection(
                heading = "KVKK Uyumu",
                body = "Evrak Dönüştürücü, 6698 sayılı Kişisel Verilerin Korunması Kanunu'na uygun olarak çalışır. " +
                    "Kişisel veri toplanmaz ve işlenmez. Dosyalarınız tamamen cihazınızda kalır.",
            )
            LegalSection(
                heading = "İletişim",
                body = "Gizlilik politikamız hakkındaki sorularınız için bizimle iletişime geçebilirsiniz.\nE-posta: info@velikececi.com",
            )
        }
    }
}
