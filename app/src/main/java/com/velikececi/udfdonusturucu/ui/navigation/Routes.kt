package com.velikececi.udfdonusturucu.ui.navigation

import android.net.Uri

/**
 * ContentView.swift / navigasyon akışındaki ekranların Compose rota karşılıkları.
 *
 * Onboarding artık bu grafiğin bir parçası değil — [com.velikececi.udfdonusturucu.ui.navigation.AppNavHost]
 * onboarding tamamlanana kadar grafiği hiç kurmaz (iOS'taki `if hasCompletedOnboarding { MainTabView() }
 * else { OnboardingView() }` dallanmasının karşılığı). [TOP_LEVEL] rotaları, iOS'un 4 sekmeli
 * `MainTabView`'ine karşılık gelen alt sekme çubuğunun görünür olduğu rotalardır.
 */
object Routes {
    const val MAIN = "main"
    const val TOOLS = "tools"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    val TOP_LEVEL = listOf(MAIN, TOOLS, HISTORY, SETTINGS)

    const val CONVERSION = "conversion"
    const val RESULT = "result"

    // ToolsView.swift'teki 7 Pro aracın rotaları — sıra ProTool.kt ile birebir.
    const val TOOL_MERGE = "tools/merge"
    const val TOOL_COMPRESS = "tools/compress"
    const val TOOL_ENCRYPT = "tools/encrypt"
    const val TOOL_EDITOR = "tools/editor"
    const val TOOL_OCR = "tools/ocr"
    const val TOOL_SIGNATURE = "tools/signature"
    const val TOOL_TEMPLATES = "tools/templates"

    const val TEMPLATE_ID_ARG = "templateId"
    const val TOOL_TEMPLATE_FORM = "tools/templates/{$TEMPLATE_ID_ARG}"
    fun templateForm(templateId: String) = "tools/templates/${Uri.encode(templateId)}"

    const val PRIVACY = "legal/privacy"
    const val TERMS = "legal/terms"

    // Paywall kaynağı (analytics + hangi akıştan açıldığını anlamak için) — iOS PaywallView'daki
    // `source` parametresiyle aynı sözleşme: "onboarding", "limit_card", "limit_alert", "batch",
    // "result_limit", "history", "settings", "tools", "tools_<toolId>".
    const val PAYWALL_SOURCE_ARG = "source"
    const val PAYWALL = "paywall?source={$PAYWALL_SOURCE_ARG}"
    fun paywall(source: String) = "paywall?source=${Uri.encode(source)}"

    const val PREVIEW_ARG = "recordId"
    const val PREVIEW = "preview/{$PREVIEW_ARG}"
    fun preview(recordId: String) = "preview/$recordId"

    // Sonuç ekranından dosya yoluyla önizleme (Geçmiş'teki recordId rotasından bağımsız).
    // Uri.encode: yol ayracı ve Türkçe karakterler rota deseniyle çakışmasın diye.
    const val PREVIEW_FILE_ARG = "filePath"
    const val PREVIEW_FILE = "previewFile/{$PREVIEW_FILE_ARG}"
    fun previewFile(path: String) = "previewFile/${Uri.encode(path)}"
}
