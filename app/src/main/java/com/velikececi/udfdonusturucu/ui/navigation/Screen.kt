package com.velikececi.udfdonusturucu.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Dönüştür")
    object History : Screen("history", "Geçmiş")
    object Settings : Screen("settings", "Ayarlar")
    object Paywall : Screen("paywall", "Premium")
}
