# Evrak Dönüştürücü - Google Play Hazırlık ve Yapılacaklar Listesi

## 1. AdMob Entegrasyonu (Tamamlandı)
- **Yayıncı Kimliği:** `pub-1041738122428212`
- **Uygulama Kimliği:** `ca-app-pub-1041738122428212~5886914438`
- **Banner Reklam Birimi:** `ca-app-pub-1041738122428212/3416612776`
- **Geçiş (Interstitial) Reklam Birimi:** `ca-app-pub-1041738122428212/4329554409`
- **Ödüllü Geçiş Reklam Birimi:** `ca-app-pub-1041738122428212/2380645547`
- **Debug / Test:** Debug modunda Google test ID'leri yüklenir (hesap banlanmasını önlemek için). İstenirse `forceRealAds=true` ile gerçek reklamlar test edilebilir.

## 2. Google Play Console Yükleme
- AAB oluşturma: `gradle :app:bundleRelease`
- Keystore imzalama: `keystore.properties` dosyası oluşturulup içine anahtar bilgileri yazılmalıdır (`keystore.properties.example` şablonuna bakınız).

## 3. GitHub Senkronizasyonu
- Yapılan değişiklikleri GitHub reponuza göndermek için AI Studio arayüzündeki sağ üst panelden **"Export to GitHub"** veya **"Push to GitHub"** butonunu kullanabilirsiniz.
