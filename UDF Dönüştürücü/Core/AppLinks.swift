import Foundation

/// Uygulamanın gösterdiği yasal bağlantılar. App Store Connect metadata'sındaki
/// adreslerle birebir aynı olmalı — App Review 3.1.2(c) bunları karşılaştırıyor.
enum AppLinks {
    /// Apple'ın standart son kullanıcı lisans sözleşmesi (EULA).
    static let appleEULA = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!

    /// Yayındaki gizlilik politikası — App Store Connect > Privacy Policy URL ile aynı.
    static let privacyPolicy = URL(string: "https://velikececi.com/udfdonusturucu/gizlilik.html")!

    /// Yayındaki kullanım koşulları — App Store Connect > License Agreement ile aynı.
    static let termsOfUse = URL(string: "https://velikececi.com/udfdonusturucu/kosullar.html")!

    /// Destek sayfası — App Store Connect > Support URL ile aynı.
    static let support = URL(string: "https://velikececi.com/udfdonusturucu/destek.html")!
}
