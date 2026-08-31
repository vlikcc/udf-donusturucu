# UDFShareExtension

Bu klasördeki dosyalar hazır — ama **Xcode target'ı henüz oluşturulmadı.** Aşağıdaki
adımlar tamamlanana kadar bu kod derlemeye dahil değildir.

## Neler hazır

| Dosya | Ne işe yarar |
|---|---|
| `Info.plist` | `NSExtensionActivationRule` en fazla 10 dosya kabul edecek şekilde ayarlı; principal class storyboard yerine `ShareViewController` |
| `UDFShareExtension.entitlements` | App Group: `group.com.velikececi.UDFDonusturucu` |
| `ShareViewController.swift` | Storyboard/`SLComposeServiceViewController` yerine SwiftUI arayüzü |
| `ShareConversion.swift` | Dosya çözümleme, dönüştürme, App Group'a taşıma, bekleyen kayıt yazma |

Ana uygulama tarafında hazır olanlar:

- `UDF Dönüştürücü/Core/AppGroup.swift` — paylaşılan konteyner ve bekleyen kayıt kuyruğu
- `ConversionStorage.importPendingSharedRecords()` — extension çıktısını devralır
- `UDF_Donusturucu_App.swift` — açılışta devralmayı çağırır

## Yapılacaklar (Xcode'da)

1. **Target'ı oluşturun:** proje → TARGETS → **+** → iOS → Application Extension →
   **Share Extension**. Product Name: `UDFShareExtension`. Embed in Application:
   `UDF Dönüştürücü`. Şema aktifleştirme sorusunda **Cancel**.

2. **Xcode'un ürettiği dosyaları bu klasördekilerle değiştirin.** Xcode kendi
   `ShareViewController.swift`, `Info.plist` ve `MainInterface.storyboard` dosyalarını
   yazar. Storyboard'ı silin, diğer ikisini geri alın:
   ```bash
   git checkout -- UDFShareExtension/Info.plist UDFShareExtension/ShareViewController.swift
   ```
   Ardından build settings'te `INFOPLIST_FILE` değerinin `UDFShareExtension/Info.plist`
   olduğunu ve `UIKit Main Storyboard File Base Name` ayarının **boş** olduğunu doğrulayın.

3. **App Groups yetkisini iki target'a da ekleyin:** Signing & Capabilities →
   **+ Capability** → App Groups → `group.com.velikececi.UDFDonusturucu`.
   Extension için `CODE_SIGN_ENTITLEMENTS` değeri
   `UDFShareExtension/UDFShareExtension.entitlements` olmalı.

4. **Dönüştürücüleri extension target'ına da üye yapın.** Aşağıdaki dosyaların File
   Inspector → Target Membership bölümünde `UDFShareExtension` de işaretli olmalı:
   - `Core/UDFParser/` altındaki tüm dosyalar
   - `Core/Converters/PDFConverter.swift`, `Core/Converters/WordConverter.swift` ve
     bunların bağımlılıkları
   - `Core/AppGroup.swift`

   > Ana uygulama klasörü `PBXFileSystemSynchronizedRootGroup` kullandığı için üyelik
   > istisnaları Xcode'dan işaretlenmeli. Paylaşılan çekirdek büyürse bu dosyaları ortak
   > bir framework target'ına taşımak daha temiz olur.

5. **Minimum Deployments** değerini ana uygulamayla eşitleyin.

## Bilinen sınırlar

- Şu an yalnızca **UDF → PDF / DOCX** yönü destekleniyor. Ters yön (PDF/DOCX → UDF)
  eklenebilir; `UDFCreator` tarafının extension'da çalıştığı doğrulanmalı.
- Extension'lar dar bir bellek bütçesinde çalışır. `ShareConversion.maxInputBytes`
  20 MB'ta duruyor; üstündeki belgelerde kullanıcı ana uygulamaya yönlendiriliyor.
  Gerçek cihazda test edip bu eşiği ayarlayın.
- Extension'da paywall gösterilmiyor ve günlük limit uygulanmıyor. Limit kontrolü
  isteniyorsa `LimitService`'in App Group üzerinden paylaşılması gerekir.
