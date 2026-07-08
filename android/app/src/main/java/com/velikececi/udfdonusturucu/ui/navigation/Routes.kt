package com.velikececi.udfdonusturucu.ui.navigation

import android.net.Uri

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

    // Sonuç ekranından dosya yoluyla önizleme (Geçmiş'teki recordId rotasından bağımsız).
    // Uri.encode: yol ayracı ve Türkçe karakterler rota deseniyle çakışmasın diye.
    const val PREVIEW_FILE_ARG = "filePath"
    const val PREVIEW_FILE = "previewFile/{$PREVIEW_FILE_ARG}"
    fun previewFile(path: String) = "previewFile/${Uri.encode(path)}"
}
