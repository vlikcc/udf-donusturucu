plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // `apply false`: gerçek uygulama app/build.gradle.kts'te koşullu yapılır (google-services.json
    // yoksa bu plugin build'i patlatır — AnalyticsService.swift'in "GoogleService-Info.plist yoksa
    // Firebase başlatılmaz" davranışının Android karşılığı, bkz. analytics/Analytics.kt).
    alias(libs.plugins.google.services) apply false
}
