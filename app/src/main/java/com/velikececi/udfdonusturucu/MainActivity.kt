package com.velikececi.udfdonusturucu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.velikececi.udfdonusturucu.ads.ConsentManager
import com.velikececi.udfdonusturucu.ui.navigation.Screen
import com.velikececi.udfdonusturucu.ui.screens.HistoryScreen
import com.velikececi.udfdonusturucu.ui.screens.HomeScreen
import com.velikececi.udfdonusturucu.ui.screens.PaywallScreen
import com.velikececi.udfdonusturucu.ui.screens.SettingsScreen
import com.velikececi.udfdonusturucu.ui.theme.EvrakDonusturucuTheme
import com.velikececi.udfdonusturucu.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // UMP & AdMob Başlatma
        val consentManager = ConsentManager(this)
        consentManager.requestConsent {
            (application as UdfApp).adsManager.initialize(this)
        }

        setContent {
            EvrakDonusturucuTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToHistory = { navController.navigate(Screen.History.route) },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                            onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
                        )
                    }

                    composable(Screen.History.route) {
                        HistoryScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
                        )
                    }

                    composable(Screen.Paywall.route) {
                        PaywallScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
