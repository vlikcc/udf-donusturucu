package com.velikececi.udfdonusturucu.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.velikececi.udfdonusturucu.di.AppContainer
import com.velikececi.udfdonusturucu.ui.conversion.ConversionFlowViewModel
import com.velikececi.udfdonusturucu.ui.conversion.ConversionScreen
import com.velikececi.udfdonusturucu.ui.history.HistoryScreen
import com.velikececi.udfdonusturucu.ui.legal.PrivacyPolicyScreen
import com.velikececi.udfdonusturucu.ui.legal.TermsScreen
import com.velikececi.udfdonusturucu.ui.main.MainScreen
import com.velikececi.udfdonusturucu.ui.onboarding.OnboardingScreen
import com.velikececi.udfdonusturucu.ui.paywall.PaywallScreen
import com.velikececi.udfdonusturucu.ui.preview.DocumentFilePreview
import com.velikececi.udfdonusturucu.ui.preview.DocumentPreviewScreen
import com.velikececi.udfdonusturucu.ui.result.ResultScreen
import com.velikececi.udfdonusturucu.ui.settings.SettingsScreen
import com.velikececi.udfdonusturucu.ui.tools.ToolsScreen
import com.velikececi.udfdonusturucu.ui.tools.compress.CompressScreen
import com.velikececi.udfdonusturucu.ui.tools.editor.UdfEditorScreen
import com.velikececi.udfdonusturucu.ui.tools.encrypt.EncryptScreen
import com.velikececi.udfdonusturucu.ui.tools.merge.MergeScreen
import com.velikececi.udfdonusturucu.ui.tools.ocr.OcrScreen
import com.velikececi.udfdonusturucu.ui.tools.signature.SignatureInfoScreen
import com.velikececi.udfdonusturucu.ui.tools.templates.TemplateFormScreen
import com.velikececi.udfdonusturucu.ui.tools.templates.TemplatesScreen
import kotlinx.coroutines.launch
import java.io.File

/**
 * Uygulama kök kompozisyonu — iOS `UDF_Donusturucu_App.swift`'teki kök dallanmanın karşılığı:
 * `if hasCompletedOnboarding { MainTabView() } else { OnboardingView() }`. Onboarding, alt sekme
 * navigasyon grafiğinin (`MainNavHost`) BİR PARÇASI DEĞİLDİR — onboarding graph içinde olsaydı
 * `graph.findStartDestination()` "onboarding" olurdu ve sekme geçişlerindeki
 * `popUpTo(findStartDestination())` artık stack'te olmayan bir hedefi işaret ederdi.
 *
 * Onboarding bittikten hemen sonra, premium olmayan kullanıcıya bir kez tanıtım paywall'ı
 * gösterilir (iOS `hasSeenIntroPaywall` + `fullScreenCover` karşılığı).
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val onboardingDone by container.settingsRepository.isOnboardingDone.collectAsState(initial = null)
    val hasSeenIntroPaywall by container.settingsRepository.hasSeenIntroPaywall.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val done = onboardingDone ?: return
    if (!done) {
        OnboardingScreen(
            onFinished = {
                scope.launch { container.settingsRepository.setOnboardingDone() }
                container.analytics.onboardingCompleted()
            },
        )
        return
    }

    val seenIntro = hasSeenIntroPaywall ?: return
    var showIntroPaywall by remember { mutableStateOf(false) }
    LaunchedEffect(seenIntro) {
        if (!seenIntro) {
            val isPremium = container.limitRepository.state.value.isPremium
            if (!isPremium) {
                showIntroPaywall = true
                container.settingsRepository.setHasSeenIntroPaywall()
            }
        }
    }

    if (showIntroPaywall) {
        PaywallScreen(
            container = container,
            source = "onboarding",
            onBack = { showIntroPaywall = false },
        )
    } else {
        MainNavHost(container = container)
    }
}

@Composable
private fun MainNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    // Ana ekran → dönüşüm → sonuç arasında paylaşılan durum: bu kompozisyon navigasyon boyunca
    // yeniden oluşturulmadığından burada tek örnek üretmek, dosya listesini navigasyon
    // argümanlarıyla taşımaktan daha güvenli/basit.
    val conversionFlowViewModel: ConversionFlowViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConversionFlowViewModel(container.conversionRepository) }
        },
    )

    // IncomingFileRouter.swift karşılığı: başka bir uygulamadan gelen .udf dosyası her an
    // yayınlanabilir — geldiğinde hangi sekmede olursa olsun ANA SAYFA'ya düşülür.
    val incomingFile by container.incomingFileRepository.incoming.collectAsState()
    LaunchedEffect(incomingFile) {
        if (incomingFile != null) {
            navController.navigate(Routes.MAIN) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = Routes.TOP_LEVEL.any { route ->
        backStackEntry?.destination?.hierarchy?.any { it.route == route } == true
    }

    Scaffold(
        bottomBar = { if (showBottomBar) AppBottomBar(navController) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Routes.MAIN) {
                MainScreen(
                    container = container,
                    flowViewModel = conversionFlowViewModel,
                    onNavigateHistory = { navController.navigate(Routes.HISTORY) },
                    onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigatePaywall = { source -> navController.navigate(Routes.paywall(source)) },
                    onNavigateConversion = { navController.navigate(Routes.CONVERSION) },
                )
            }
            composable(Routes.TOOLS) {
                ToolsScreen(
                    container = container,
                    onNavigateTool = { route -> navController.navigate(route) },
                    onNavigatePaywall = { source -> navController.navigate(Routes.paywall(source)) },
                )
            }
            composable(Routes.TOOL_MERGE) {
                MergeScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { file -> navController.navigate(Routes.previewFile(file.absolutePath)) },
                )
            }
            composable(Routes.TOOL_COMPRESS) {
                CompressScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { file -> navController.navigate(Routes.previewFile(file.absolutePath)) },
                )
            }
            composable(Routes.TOOL_ENCRYPT) {
                EncryptScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(Routes.TOOL_EDITOR) {
                UdfEditorScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { file -> navController.navigate(Routes.previewFile(file.absolutePath)) },
                )
            }
            composable(Routes.TOOL_OCR) {
                OcrScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { file -> navController.navigate(Routes.previewFile(file.absolutePath)) },
                )
            }
            composable(Routes.TOOL_SIGNATURE) {
                SignatureInfoScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TOOL_TEMPLATES) {
                TemplatesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTemplate = { templateId -> navController.navigate(Routes.templateForm(templateId)) },
                )
            }
            composable(Routes.TOOL_TEMPLATE_FORM) { backStackEntry ->
                val templateId = backStackEntry.arguments?.getString(Routes.TEMPLATE_ID_ARG).orEmpty()
                TemplateFormScreen(
                    container = container,
                    templateId = templateId,
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { file -> navController.navigate(Routes.previewFile(file.absolutePath)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { recordId -> navController.navigate(Routes.preview(recordId)) },
                    onNavigatePaywall = { source -> navController.navigate(Routes.paywall(source)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onNavigatePaywall = { source -> navController.navigate(Routes.paywall(source)) },
                    onNavigatePrivacy = { navController.navigate(Routes.PRIVACY) },
                    onNavigateTerms = { navController.navigate(Routes.TERMS) },
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TERMS) {
                TermsScreen(onBack = { navController.popBackStack() })
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
                    onNavigatePaywall = { source -> navController.navigate(Routes.paywall(source)) },
                    onOpenPreview = { file ->
                        navController.navigate(Routes.previewFile(file.absolutePath))
                    },
                )
            }
            composable(
                route = Routes.PAYWALL,
                arguments = listOf(
                    navArgument(Routes.PAYWALL_SOURCE_ARG) {
                        type = NavType.StringType
                        defaultValue = "unknown"
                    },
                ),
            ) { backStackEntry ->
                val source = backStackEntry.arguments?.getString(Routes.PAYWALL_SOURCE_ARG) ?: "unknown"
                PaywallScreen(container = container, source = source, onBack = { navController.popBackStack() })
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
