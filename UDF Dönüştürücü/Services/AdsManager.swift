import Foundation
import UIKit
import GoogleMobileAds
import AppTrackingTransparency
import AdSupport

@MainActor
final class AdsManager: NSObject {
    static let shared = AdsManager()

    // MARK: - Ad Unit IDs

    #if DEBUG
    // Google'ın resmi test ID'leri — geliştirme sırasında kullanılır.
    static let bannerUnitID                = "ca-app-pub-3940256099942544/2934735716"
    static let interstitialUnitID          = "ca-app-pub-3940256099942544/4411468910"
    static let rewardedInterstitialUnitID  = "ca-app-pub-3940256099942544/5354046379"
    #else
    static let bannerUnitID                = "ca-app-pub-1041738122428212/3416612776"
    static let interstitialUnitID          = "ca-app-pub-1041738122428212/4329554409"
    static let rewardedInterstitialUnitID  = "ca-app-pub-1041738122428212/2380645547"
    #endif

    // MARK: - State

    private var loadedInterstitial: InterstitialAd?
    private var loadedRewardedInterstitial: RewardedInterstitialAd?
    private var isConfigured = false

    private let interstitialCounterKey = "ads_interstitialCounter"
    /// Her N başarılı ResultView gösteriminde bir interstitial gösterilir.
    private let interstitialFrequency = 2

    // MARK: - Public API

    var shouldShowAds: Bool {
        !LimitService.shared.isPremium
    }

    func configure() {
        guard !isConfigured else { return }
        isConfigured = true
        MobileAds.shared.start(completionHandler: nil)
        preloadInterstitial()
        preloadRewardedInterstitial()
    }

    func requestATTIfNeeded() async {
        if #available(iOS 14, *) {
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
    }

    // MARK: - Interstitial

    func preloadInterstitial() {
        guard shouldShowAds, loadedInterstitial == nil else { return }
        let request = Request()
        InterstitialAd.load(with: Self.interstitialUnitID, request: request) { [weak self] ad, error in
            guard let self else { return }
            if let error {
                print("[AdsManager] Interstitial load error: \(error.localizedDescription)")
                return
            }
            self.loadedInterstitial = ad
            self.loadedInterstitial?.fullScreenContentDelegate = self
        }
    }

    /// `interstitialFrequency` eşiğine göre, hazırsa gösterir; değilse sessizce atlar.
    func showInterstitialIfReady(from rootVC: UIViewController, onDismiss: @escaping () -> Void = {}) {
        guard shouldShowAds else { onDismiss(); return }

        let counter = UserDefaults.standard.integer(forKey: interstitialCounterKey) + 1
        UserDefaults.standard.set(counter, forKey: interstitialCounterKey)

        guard counter % interstitialFrequency == 0,
              let ad = loadedInterstitial else {
            onDismiss()
            return
        }

        interstitialDismissHandler = onDismiss
        ad.present(from: rootVC)
    }

    private var interstitialDismissHandler: (() -> Void)?

    // MARK: - Rewarded Interstitial

    func preloadRewardedInterstitial() {
        guard shouldShowAds, loadedRewardedInterstitial == nil else { return }
        let request = Request()
        RewardedInterstitialAd.load(with: Self.rewardedInterstitialUnitID, request: request) { [weak self] ad, error in
            guard let self else { return }
            if let error {
                print("[AdsManager] Rewarded interstitial load error: \(error.localizedDescription)")
                return
            }
            self.loadedRewardedInterstitial = ad
            self.loadedRewardedInterstitial?.fullScreenContentDelegate = self
        }
    }

    /// Ödüllü geçiş reklamını gösterir. Kullanıcı ödülü kazanırsa `LimitService.addBonusConversions(1)` çağrılır
    /// ve `onReward` çalıştırılır. Reklam hazır değilse `onUnavailable` çalıştırılır.
    func showRewarded(from rootVC: UIViewController,
                      onReward: @escaping () -> Void = {},
                      onUnavailable: @escaping () -> Void = {}) {
        guard shouldShowAds, let ad = loadedRewardedInterstitial else {
            onUnavailable()
            return
        }
        rewardHandler = onReward
        ad.present(from: rootVC) { [weak self] in
            LimitService.shared.addBonusConversions(1)
            self?.rewardHandler?()
            self?.rewardHandler = nil
        }
    }

    private var rewardHandler: (() -> Void)?
}

// MARK: - FullScreenContentDelegate

extension AdsManager: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        if ad === loadedInterstitial {
            loadedInterstitial = nil
            interstitialDismissHandler?()
            interstitialDismissHandler = nil
            preloadInterstitial()
        } else if ad === loadedRewardedInterstitial {
            loadedRewardedInterstitial = nil
            preloadRewardedInterstitial()
        }
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("[AdsManager] Ad failed to present: \(error.localizedDescription)")
        if ad === loadedInterstitial {
            loadedInterstitial = nil
            interstitialDismissHandler?()
            interstitialDismissHandler = nil
            preloadInterstitial()
        } else if ad === loadedRewardedInterstitial {
            loadedRewardedInterstitial = nil
            preloadRewardedInterstitial()
        }
    }
}

// MARK: - Top View Controller helper

extension UIApplication {
    /// SwiftUI içinden modal sunum için kullanılabilecek aktif top view controller.
    static func topViewController(base: UIViewController? = nil) -> UIViewController? {
        let baseVC = base ?? UIApplication.shared
            .connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })?
            .rootViewController

        if let nav = baseVC as? UINavigationController {
            return topViewController(base: nav.visibleViewController)
        }
        if let tab = baseVC as? UITabBarController, let selected = tab.selectedViewController {
            return topViewController(base: selected)
        }
        if let presented = baseVC?.presentedViewController {
            return topViewController(base: presented)
        }
        return baseVC
    }
}
