import Foundation
import FirebaseAnalytics

/// Dönüşüm hunisini (paywall, satın alma, limit, reklam) ölçmek için ince bir Firebase Analytics sarmalayıcısı.
/// DEBUG derlemelerinde konsola loglar; RELEASE'de doğrudan Firebase'e gönderir.
enum AnalyticsService {

    static func logPaywallShown(source: String) {
        log("paywall_shown", params: ["source": source])
    }

    static func logPaywallPlanSelected(productID: String, source: String) {
        log("paywall_plan_selected", params: ["product_id": productID, "source": source])
    }

    static func logPurchaseStarted(productID: String, source: String) {
        log("purchase_started", params: ["product_id": productID, "source": source])
    }

    static func logPurchaseCompleted(productID: String, priceDisplay: String, source: String) {
        log("purchase_completed", params: [
            "product_id": productID,
            "price_display": priceDisplay,
            "source": source
        ])
    }

    static func logPurchaseFailed(productID: String, reason: String, source: String) {
        log("purchase_failed", params: ["product_id": productID, "reason": reason, "source": source])
    }

    static func logRestoreCompleted(found: Bool) {
        log("restore_completed", params: ["found": found])
    }

    static func logLimitHit() {
        log("limit_hit", params: nil)
    }

    static func logRewardedAdWatched() {
        log("rewarded_ad_watched", params: nil)
    }

    static func logOnboardingCompleted() {
        log("onboarding_completed", params: nil)
    }

    static func logConversionCompleted(count: Int, direction: String) {
        log("conversion_completed", params: ["count": count, "direction": direction])
    }

    static func logToolOpened(_ tool: String) {
        log("tool_opened", params: ["tool": tool])
    }

    /// Kilitli araca dokunma — güçlü satın alma niyeti sinyali.
    static func logToolLockedTap(_ tool: String) {
        log("tool_locked_tap", params: ["tool": tool])
    }

    static func logFileOpenedExternal() {
        log("file_opened_external", params: nil)
    }

    // MARK: - Core

    private static func log(_ name: String, params: [String: Any]?) {
        #if DEBUG
        print("[Analytics] \(name) \(params ?? [:])")
        #endif
        Analytics.logEvent(name, parameters: params)
    }
}
