# Yayına Alma Yapılacaklar Listesi

## 1. Google AdMob Reklam Sistemi Kurulumu

### 1.1 AdMob Hesap ve Uygulama Kaydı
- [x] [Google AdMob](https://admob.google.com) hesabı oluştur veya mevcut hesaba giriş yap ✅
- [x] AdMob panelinde iOS uygulaması oluşturuldu: **Udf Dönüştürücü** ✅
- [x] Interstitial reklam birimi oluşturuldu: `ca-app-pub-1041738122428212/1329289578` ✅
- [x] Banner reklam birimi oluşturuldu: `ca-app-pub-1041738122428212/4530167981` ✅

### 1.2 SDK Entegrasyonu
- [ ] Google Mobile Ads SDK'yı projeye ekle (SPM veya CocoaPods):
  - **SPM:** Xcode → File → Add Package → `https://github.com/googleads/swift-package-manager-google-mobile-ads`
  - **CocoaPods:** `pod 'Google-Mobile-Ads-SDK'` ekleyip `pod install` çalıştır
- [x] `Info.plist` dosyasına `GADApplicationIdentifier` key'ini ekle ve AdMob App ID değerini gir ✅
  - **Dosya:** `UDF Belge Dönüştürücü/Info.plist`
  - **Gerçek AdMob App ID girildi:** `ca-app-pub-1041738122428212~5886914438`
- [x] `Info.plist` dosyasına `SKAdNetworkItems` ekle ✅ (49 adet SKAdNetwork identifier eklendi)

### 1.3 SDK Başlatma
- [x] `UDF_Belge_Do_nu_s_tu_ru_cu_App.swift` dosyasında uygulama başlangıcında SDK başlatıldı ✅
  ```swift
  import GoogleMobileAds
  init() {
      MobileAds.shared.start(completionHandler: nil)
  }
  ```

### 1.4 Test Reklam ID'sini Gerçek ID ile Değiştir
- [x] `InterstitialAdManager.swift` dosyasındaki test ID gerçek Ad Unit ID ile değiştirildi ✅
  - **Dosya:** `Services/InterstitialAdManager.swift`
  - **Gerçek ID:** `ca-app-pub-1041738122428212/1329289578`
- [x] `BannerAdView.swift` dosyasındaki test ID gerçek Banner Ad Unit ID ile değiştirildi ✅
  - **Dosya:** `Services/BannerAdView.swift`
  - **Gerçek ID:** `ca-app-pub-1041738122428212/4530167981`

### 1.5 Banner Reklam
- [x] `ContentView.swift` içindeki placeholder `adBanner` view'ı gerçek `GADBannerView` ile değiştirildi ✅
  - `Services/BannerAdView.swift` dosyası oluşturuldu
  - `ContentView.swift`'teki adBanner gerçek `BannerAdView()` ile güncellendi

---

## 2. Gizlilik ve İzin Ayarları

### 2.1 App Tracking Transparency (ATT)
- [x] `Info.plist` dosyasına kullanıcı izleme açıklaması eklendi ✅
  ```xml
  <key>NSUserTrackingUsageDescription</key>
  <string>Size daha uygun reklamlar gösterebilmek için izleme izninizi istiyoruz.</string>
  ```
- [x] Uygulama başlangıcında ATT izin dialogu gösterimi eklendi ✅
  - `UDF_Belge_Do_nu_s_tu_ru_cu_App.swift` dosyasında `ATTrackingManager.requestTrackingAuthorization` çağrısı eklendi

### 2.2 Apple Privacy Manifest
- [x] `PrivacyInfo.xcprivacy` dosyası oluşturuldu ✅
- [x] Kullanılan API kategorileri bildirildi ✅:
  - `NSPrivacyAccessedAPICategoryUserDefaults` (UserDefaults kullanımı)
  - `NSPrivacyAccessedAPICategoryFileTimestamp` (dosya işlemleri)
- [x] Üçüncü parti SDK'ların (AdMob) veri toplama bilgileri eklendi ✅
- [x] Reklam kişiselleştirme için veri kullanımı bildirildi ✅

### 2.3 App Privacy (App Store Connect)
- [ ] App Store Connect'te gizlilik uygulamalarını doldur:
  - Reklam verisi toplama (AdMob)
  - Satın alma geçmişi (StoreKit)
  - Kullanım verileri
  - Cihaz tanımlayıcıları (IDFA — reklam için)

---

## 3. App Store Connect Hazırlığı

### 3.1 Hesap ve Uygulama Kaydı
- [ ] Apple Developer Program üyeliğinin aktif olduğunu doğrula
- [ ] [App Store Connect](https://appstoreconnect.apple.com)'te yeni uygulama oluştur:
  - Bundle ID: `com.velikececi.UDF-Belge-Do-nu-s-tu-ru-cu-`
  - Uygulama adı: UDF Belge Dönüştürücü (veya tercih edilen isim)
  - Birincil dil: Türkçe
  - SKU: benzersiz bir tanımlayıcı belirle

### 3.2 App Store Bilgileri
- [ ] Uygulama açıklaması yaz (Türkçe)
- [ ] Anahtar kelimeler belirle (UDF, belge dönüştürücü, PDF, DOCX, evrak vb.)
- [ ] Kategori seç (Verimlilik / Utilities)
- [ ] Destek URL'si ekle
- [ ] Gizlilik politikası URL'si ekle (SettingsView'da zaten mevcut, web'de de yayınla)

### 3.3 Ekran Görüntüleri ve Medya
- [ ] iPhone ekran görüntüleri hazırla (minimum 3 adet):
  - 6.7" (iPhone 15 Pro Max / 16 Pro Max) — zorunlu
  - 6.5" (iPhone 11 Pro Max / XS Max) — zorunlu
  - 5.5" (iPhone 8 Plus) — opsiyonel
- [ ] iPad ekran görüntüleri hazırla (eğer iPad destekleniyorsa):
  - 12.9" iPad Pro
  - 13" iPad Air
- [ ] Uygulama önizleme videosu (opsiyonel ama önerilen)
- [ ] App ikonu son halini kontrol et (1024x1024, alfa kanalı olmadan)

### 3.4 StoreKit / Uygulama İçi Satın Alma
- [ ] App Store Connect'te uygulama içi satın alma ürünü oluştur:
  - Product ID: `com.evrakdonus.unlimited`
  - Tür: Non-Consumable (tek seferlik satın alma)
  - Fiyat belirle
  - Açıklama ve görünen adı yaz
- [ ] Satın alma inceleme bilgilerini ekle (ekran görüntüsü + açıklama)
- [ ] Sandbox test hesabı oluştur ve satın alma akışını test et

---

## 4. Xcode Proje Ayarları

### 4.1 Sürüm ve Build Numarası
- [x] `Marketing Version` (CFBundleShortVersionString) `1.0.0` olarak ayarlandı ✅
- [x] `Current Project Version` (CFBundleVersion) `1` olarak ayarlandı ✅
- [ ] Her App Store gönderiminde build numarasını artır

### 4.2 Deployment Target
- [ ] iOS Deployment Target'ı kontrol et (şu an `26.2` — gerçekçi bir minimum sürüm seç, ör. iOS 17.0)
  - ⚠️ Bu değişiklik Xcode'dan yapılmalı: Proje → General → Minimum Deployments
- [ ] Desteklenen cihaz yönelimlerini kontrol et

### 4.3 Signing ve Capabilities
- [x] Code Signing: Automatic signing aktif, Development Team `G7YN588RY7` olarak ayarlanmış ✅
- [ ] In-App Purchase capability'nin ekli olduğunu doğrula (Signing & Capabilities → + Capability → In-App Purchase)

---

## 5. Test

### 5.1 Reklam Testi
- [ ] Test reklam ID'si ile interstitial reklamın düzgün gösterildiğini doğrula
- [ ] Test reklam ID'si ile banner reklamın düzgün gösterildiğini doğrula
- [ ] Reklam yüklenemezse indirme/paylaşma işleminin yine de çalıştığını doğrula
- [ ] Premium kullanıcıya reklam gösterilmediğini doğrula
- [ ] Reklam kapandıktan sonra dosya paylaşım/kaydetme işleminin başladığını doğrula

### 5.2 Uygulama İçi Satın Alma Testi
- [ ] Sandbox ortamında satın alma akışını test et
- [ ] Satın alma sonrası reklamların kaldırıldığını doğrula
- [ ] Restore purchases (satın alma geri yükleme) işlemini test et
- [ ] Limit kaldırma (sınırsız dönüştürme) işlevini doğrula

### 5.3 Genel Test
- [ ] Tüm dönüştürme yönlerini test et (UDF→PDF, UDF→DOCX, PDF→UDF, DOCX→UDF)
- [ ] Günlük 5 ücretsiz limit sayacının doğru çalıştığını doğrula
- [ ] Gün değişiminde sayacın sıfırlandığını doğrula
- [ ] Farklı iPhone modellerinde UI'ın düzgün göründüğünü kontrol et
- [ ] Dark mode ve light mode'da test et
- [ ] İnternet bağlantısı olmadan uygulamanın çökmediğini doğrula

---

## 6. Yayına Gönderme

### 6.1 Archive ve Upload
- [ ] Xcode'da scheme'i "Any iOS Device (arm64)" olarak seç
- [ ] Product → Archive ile uygulama arşivi oluştur
- [ ] Organizer'dan App Store Connect'e yükle (Distribute App → App Store Connect)

### 6.2 App Store Connect Son Kontroller
- [ ] Build'in App Store Connect'te göründüğünü doğrula
- [ ] Build'i sürüme ekle
- [ ] Yaş sınıflandırması anketini doldur
- [ ] İhracat uyumluluk bilgilerini doldur (şifreleme kullanımı)
- [ ] Uygulama içi satın almayı sürüme ekle
- [ ] İnceleme notları ekle (gerekiyorsa demo hesap bilgileri)

### 6.3 Gönder
- [ ] Son kez tüm bilgileri gözden geçir
- [ ] "Submit for Review" ile incelemeye gönder
- [ ] İnceleme sürecini takip et (genellikle 24-48 saat)

---

## Hızlı Referans: Kritik Dosyalar

| Dosya | Açıklama |
|-------|----------|
| `Services/InterstitialAdManager.swift` | Interstitial reklam yöneticisi — ✅ Gerçek ID: `ca-app-pub-1041738122428212/1329289578` |
| `Services/BannerAdView.swift` | Banner reklam view'ı — ✅ Gerçek ID: `ca-app-pub-1041738122428212/4530167981` |
| `Services/LimitService.swift` | Günlük dönüştürme limiti (5 ücretsiz) |
| `Services/PurchaseService.swift` | Premium satın alma — Product ID: `com.evrakdonus.unlimited` |
| `UDF_Belge_Do_nu_s_tu_ru_cu_App.swift` | Ana giriş noktası — ✅ AdMob SDK başlatma + ATT izin dialogu eklendi |
| `Info.plist` | ✅ GADApplicationIdentifier + NSUserTrackingUsageDescription + SKAdNetworkItems |
| `PrivacyInfo.xcprivacy` | ✅ Apple Privacy Manifest — API kullanımları ve veri toplama bildirimi |
| `Features/Result/ResultView.swift` | Sonuç ekranı — indirme öncesi reklam entegre |
| `Features/History/HistoryView.swift` | Geçmiş ekranı — indirme öncesi reklam entegre |
| `ContentView.swift` | Ana ekran — ✅ banner reklam entegre + indirme öncesi reklam entegre |
