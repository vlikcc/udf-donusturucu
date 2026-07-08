package com.velikececi.udfdonusturucu.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.conversion.ConversionFlowViewModel
import com.velikececi.udfdonusturucu.ui.conversion.ConversionScreen
import com.velikececi.udfdonusturucu.ui.history.HistoryScreen
import com.velikececi.udfdonusturucu.ui.main.MainScreen
import com.velikececi.udfdonusturucu.ui.onboarding.OnboardingScreen
import com.velikececi.udfdonusturucu.ui.paywall.PaywallScreen
import com.velikececi.udfdonusturucu.ui.preview.DocumentFilePreview
import com.velikececi.udfdonusturucu.ui.preview.DocumentPreviewScreen
import com.velikececi.udfdonusturucu.ui.result.ResultScreen
import com.velikececi.udfdonusturucu.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val onboardingDone by container.settingsRepository.isOnboardingDone.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    // Ana ekran → dönüşüm → sonuç arasında paylaşılan durum: AppNavHost'un kendisi navigasyon
    // boyunca yeniden oluşturulmadığından burada tek örnek üretmek, dosya listesini navigasyon
    // argümanlarıyla taşımaktan daha güvenli/basit.
    val conversionFlowViewModel: ConversionFlowViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConversionFlowViewModel(container.conversionRepository) }
        },
    )

    // İlk DataStore okuması gelene kadar hiçbir şey çizilmez (kısa bir an) — yanlış
    // startDestination ile NavHost'un kurulmasını engeller.
    val startDestination = onboardingDone ?: return

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (startDestination) Routes.MAIN else Routes.ONBOARDING,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        scope.launch { container.settingsRepository.setOnboardingDone() }
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.MAIN) {
                MainScreen(
                    container = container,
                    flowViewModel = conversionFlowViewModel,
                    onNavigateHistory = { navController.navigate(Routes.HISTORY) },
                    onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigatePaywall = { navController.navigate(Routes.PAYWALL) },
                    onNavigateConversion = { navController.navigate(Routes.CONVERSION) },
                )
            }
            composable(Routes.CONVERSION) {
                ConversionScreen(
                    flowViewModel = conversionFlowViewModel,
                    onFinished = {
                        navController.navigate(Routes.RESULT) {
                            popUpTo(Routes.CONVERSION) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.RESULT) {
                ResultScreen(
                    container = container,
                    flowViewModel = conversionFlowViewModel,
                    onDone = {
                        conversionFlowViewModel.resetForNewSelection()
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    },
                    onOpenPreview = { file ->
                        navController.navigate(Routes.previewFile(file.absolutePath))
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { recordId -> navController.navigate(Routes.preview(recordId)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onNavigatePaywall = { navController.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.PAYWALL) {
                PaywallScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Routes.PREVIEW) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getString(Routes.PREVIEW_ARG).orEmpty()
                DocumentPreviewScreen(
                    recordId = recordId,
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PREVIEW_FILE) { backStackEntry ->
                val path = backStackEntry.arguments?.getString(Routes.PREVIEW_FILE_ARG).orEmpty()
                val file = path.takeIf { it.isNotEmpty() }?.let(::File)
                DocumentFilePreview(
                    title = file?.name ?: "Önizleme",
                    file = file,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
