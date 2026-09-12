package com.velikececi.udfdonusturucu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.velikececi.udfdonusturucu.ads.ConsentManager
import com.velikececi.udfdonusturucu.data.FileCopier
import com.velikececi.udfdonusturucu.data.IncomingFileFilter
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.navigation.AppNavHost
import com.velikececi.udfdonusturucu.ui.theme.EvrakDonusturucuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * IncomingFileRouter.swift'in (`.onOpenURL`) Android karşılığı: manifest'teki `launchMode="singleTask"`
 * sayesinde hem soğuk başlatma (`onCreate`) hem uygulama zaten açıkken "Birlikte aç"/"Paylaş"
 * (`onNewIntent`) üzerinden gelen dosyalar burada yakalanır, [FileCopier] ile önbelleğe kopyalanır
 * ve gerçekten bir UDF belgesi ise ([IncomingFileFilter]) [AppContainer.incomingFileRepository]'ye
 * yayınlanır.
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as App).container

        // iOS'taki ATT izin isteğinin karşılığı: reklam SDK'sı yalnızca UMP (GDPR) onay akışı
        // tamamlandıktan sonra başlatılır.
        ConsentManager.requestConsentAndInitializeAds(this, container.adsManager)

        handleIncomingIntent(intent)

        setContent {
            EvrakDonusturucuTheme {
                AppNavHost(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val copied = FileCopier.copyToCache(this@MainActivity, uri) ?: return@launch
            if (IncomingFileFilter.looksLikeUdf(copied)) {
                container.analytics.fileOpenedExternal()
                container.incomingFileRepository.submit(copied)
            } else {
                copied.delete()
            }
        }
    }
}
