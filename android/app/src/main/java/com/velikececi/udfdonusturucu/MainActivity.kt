package com.velikececi.udfdonusturucu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.velikececi.udfdonusturucu.ads.ConsentManager
import com.velikececi.udfdonusturucu.ui.navigation.AppNavHost
import com.velikececi.udfdonusturucu.ui.theme.EvrakDonusturucuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as App).container

        // iOS'taki ATT izin isteğinin karşılığı: reklam SDK'sı yalnızca UMP (GDPR) onay akışı
        // tamamlandıktan sonra başlatılır.
        ConsentManager.requestConsentAndInitializeAds(this, container.adsManager)

        setContent {
            EvrakDonusturucuTheme {
                AppNavHost(container = container)
            }
        }
    }
}
