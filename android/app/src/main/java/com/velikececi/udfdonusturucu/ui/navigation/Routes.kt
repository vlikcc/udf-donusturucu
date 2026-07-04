package com.velikececi.udfdonusturucu.ui.navigation

/** ContentView.swift / navigasyon akışındaki ekranların Compose rota karşılıkları. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val CONVERSION = "conversion"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"

    const val PREVIEW_ARG = "recordId"
    const val PREVIEW = "preview/{$PREVIEW_ARG}"
    fun preview(recordId: String) = "preview/$recordId"
}
