package com.velikececi.udfdonusturucu.analytics

/**
 * AnalyticsService.swift'teki 13 tipli olayın Kotlin karşılığı — olay adı ve parametre anahtarları
 * iOS ile birebir aynıdır (aynı Firebase projesinde birleşik bir BigQuery şeması oluşsun diye).
 * Çağıranlar ham [Analytics.log] yerine bu facade'ı kullanır; isim/parametre hataları tek yerde
 * yakalanır.
 */
class AnalyticsEvents(private val analytics: Analytics) {

    fun paywallShown(source: String) =
        analytics.log("paywall_shown", mapOf("source" to source))

    fun paywallPlanSelected(productId: String, source: String) =
        analytics.log("paywall_plan_selected", mapOf("product_id" to productId, "source" to source))

    fun purchaseStarted(productId: String, source: String) =
        analytics.log("purchase_started", mapOf("product_id" to productId, "source" to source))

    fun purchaseCompleted(productId: String, priceDisplay: String?, source: String) =
        analytics.log(
            "purchase_completed",
            mapOf("product_id" to productId, "price_display" to priceDisplay, "source" to source),
        )

    /** [reason]: "network_error" | "storekit_error" | "unknown_error" (iOS ile aynı sözleşme). */
    fun purchaseFailed(productId: String?, reason: String, source: String) =
        analytics.log(
            "purchase_failed",
            mapOf("product_id" to productId, "reason" to reason, "source" to source),
        )

    fun restoreCompleted(found: Boolean) =
        analytics.log("restore_completed", mapOf("found" to found))

    fun limitHit() = analytics.log("limit_hit")

    fun rewardedAdWatched() = analytics.log("rewarded_ad_watched")

    fun onboardingCompleted() = analytics.log("onboarding_completed")

    fun conversionCompleted(count: Int, direction: String) =
        analytics.log("conversion_completed", mapOf("count" to count.toLong(), "direction" to direction))

    fun toolOpened(tool: String) = analytics.log("tool_opened", mapOf("tool" to tool))

    fun toolLockedTap(tool: String) = analytics.log("tool_locked_tap", mapOf("tool" to tool))

    fun fileOpenedExternal() = analytics.log("file_opened_external")
}
