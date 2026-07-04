import SwiftUI
import UIKit
import GoogleMobileAds

/// Premium kullanıcılarda hiç render edilmeyen, ücretsiz kullanıcılarda
/// adaptive AdMob banner gösteren container.
struct BannerAdContainer: View {
    @ObservedObject private var limitService = LimitService.shared

    var body: some View {
        if !limitService.isPremium {
            BannerAdView()
                .frame(height: 60)
        }
    }
}

/// AdMob `BannerView`'in SwiftUI sarmalayıcısı.
struct BannerAdView: UIViewRepresentable {
    func makeUIView(context: Context) -> BannerView {
        let width = UIScreen.main.bounds.width
        let adSize = currentOrientationAnchoredAdaptiveBanner(width: width)
        let banner = BannerView(adSize: adSize)
        banner.adUnitID = AdsManager.bannerUnitID
        banner.rootViewController = UIApplication.topViewController()
        banner.load(Request())
        return banner
    }

    func updateUIView(_ uiView: BannerView, context: Context) {
        if uiView.rootViewController == nil {
            uiView.rootViewController = UIApplication.topViewController()
        }
    }
}
