package com.velikececi.udfdonusturucu.ui.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

/** TermsView.swift karşılığı — uygulama içi, tam metin. Android'e uyarlanan noktalar: mağaza adı (Google Play), günlük 1 hak (App Store yerine). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Koşullar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
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
            Text("Kullanım Koşulları", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Son güncelleme: Nisan 2026",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LegalSection(
                heading = "Hizmet Tanımı",
                body = "Evrak Dönüştürücü, UYAP UDF formatındaki dosyaları PDF ve Microsoft Word (.docx) formatlarına " +
                    "dönüştürme hizmeti sunan bir Android uygulamasıdır.\n\n" +
                    "Önemli: Bu uygulama resmi bir devlet hizmeti değildir ve UYAP ile doğrudan bağlantılı değildir. " +
                    "Bağımsız bir üçüncü taraf uygulamasıdır.",
            )
            LegalSection(
                heading = "Desteklenen Dönüştürme Yönleri",
                body = "• UDF → PDF\n• UDF → Microsoft Word (.docx)\n• PDF → UDF\n• Word (.docx) → UDF",
            )
            LegalSection(
                heading = "Sorumluluk Sınırı",
                body = "Dönüştürme işlemi sırasında oluşabilecek biçimlendirme farklılıklarından dolayı sorumluluk " +
                    "kabul edilmez. Hukuki işlemlerde orijinal belgenin kullanılması tavsiye edilir.\n\n" +
                    "Uygulama \"olduğu gibi\" sunulmaktadır. Dönüştürme sonuçlarının doğruluğu veya eksiksizliği için garanti verilmez.",
            )
            LegalSection(
                heading = "Ücretsiz Kullanım ve Limitler",
                body = "• Ücretsiz kullanıcılar günlük 1 dönüştürme hakkına sahiptir.\n" +
                    "• Reklam izleyerek günde en fazla 2 ek dönüştürme hakkı kazanılabilir.\n" +
                    "• Limit her gün gece yarısı sıfırlanır.\n" +
                    "• Dönüştürme geçmişi 7 gün boyunca saklanır.",
            )
            LegalSection(
                heading = "Premium Üyelik",
                body = "• Premium; aylık abonelik, yıllık abonelik veya tek seferlik \"Ömür Boyu\" satın alma ile edinilebilir. " +
                    "Güncel fiyatlar uygulama içindeki Pro ekranında ve Google Play'de gösterilir.\n" +
                    "• Abonelikler, dönem sonunda otomatik olarak yenilenir. Yenilemeyi Google Play hesap ayarlarınızdan istediğiniz zaman kapatabilirsiniz.\n" +
                    "• Premium; sınırsız dönüştürme sağlar, tüm reklamları kaldırır ve dönüştürme geçmişini 30 güne uzatır.\n\n" +
                    "Satın alma işlemleri Google Play üzerinden gerçekleştirilir ve Google'ın standart iade politikalarına tabidir. " +
                    "İade talepleri için doğrudan Google ile iletişime geçmeniz gerekmektedir.",
            )
            LegalSection(
                heading = "Gizlilik",
                body = "Tüm dönüştürme işlemleri cihazınız üzerinde gerçekleşir. Belgeleriniz hiçbir sunucuya gönderilmez.",
            )
            LegalSection(
                heading = "Değişiklikler",
                body = "Bu kullanım koşulları zaman zaman güncellenebilir. Önemli değişiklikler uygulama içinden bildirilecektir. " +
                    "Uygulamayı kullanmaya devam etmeniz, güncel koşulları kabul ettiğiniz anlamına gelir.",
            )
            LegalSection(
                heading = "İletişim",
                body = "Kullanım koşulları hakkındaki sorularınız için:\nE-posta: info@velikececi.com",
            )
        }
    }
}
