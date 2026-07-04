# 2.3 App Privacy — App Store Connect Gizlilik Uygulamaları Rehberi

App Store Connect > Uygulama Gizliliği bölümünde aşağıdaki bilgileri doldurmanız gerekmektedir.

---

## Genel Bilgi

- **Uygulama üçüncü taraf analitik SDK'sı kullanmıyor** (Firebase, Amplitude vb. yok).
- **Google AdMob SDK** reklam amaçlı veri topluyor.
- **StoreKit** uygulama içi satın alma için kullanılıyor.
- Tüm dosya dönüştürme işlemleri **tamamen cihaz üzerinde** yapılıyor, sunucuya veri gönderilmiyor.

---

## Veri Toplama Beyanları

App Store Connect'te "Veri Toplama" bölümünde **"Evet, bu uygulamadan veri topluyoruz"** seçeneğini işaretleyin ve aşağıdaki veri türlerini ekleyin:

---

### 1. Cihaz Tanımlayıcıları (Device ID / Identifiers)

| Alan | Değer |
|------|-------|
| **Veri türü** | Cihaz Kimliği (Device ID) |
| **Kullanım amacı** | Üçüncü Taraf Reklamcılık |
| **Kullanıcıya bağlı mı?** | Hayır |
| **İzleme için kullanılıyor mu?** | Evet |

**Açıklama:** Google AdMob SDK, reklam kişiselleştirme ve ölçümleme amacıyla Apple IDFA (Advertising Identifier) kullanmaktadır. ATT (App Tracking Transparency) izni istenmektedir.

---

### 2. Kullanım Verileri (Usage Data)

| Alan | Değer |
|------|-------|
| **Veri türü** | Ürün Etkileşimi (Product Interaction) |
| **Kullanım amacı** | Analitik (Reklam SDK dahili analitik) |
| **Kullanıcıya bağlı mı?** | Hayır |
| **İzleme için kullanılıyor mu?** | Hayır |

**Açıklama:** Google AdMob SDK, reklam performansını ölçmek amacıyla uygulama içi etkileşim verilerini (reklam görüntülenme, tıklanma vb.) toplayabilir. Bu veri birinci taraf analitik değil, reklam SDK'sının dahili çalışma mekanizmasıdır.

---

### 3. Satın Alma Geçmişi (Purchase History)

| Alan | Değer |
|------|-------|
| **Veri türü** | Satın Alma Geçmişi (Purchase History) |
| **Kullanım amacı** | Uygulama İşlevselliği |
| **Kullanıcıya bağlı mı?** | Hayır |
| **İzleme için kullanılıyor mu?** | Hayır |

**Açıklama:** StoreKit üzerinden tek seferlik "Premium" satın alma (`com.evrakdonus.unlimited`) gerçekleştiriliyor. Satın alma durumu yalnızca premium özellikleri açmak (reklam kaldırma, sınırsız dönüşüm) amacıyla kontrol ediliyor. Apple'ın standart satın alma altyapısı kullanılıyor.

---

## App Store Connect'te Adım Adım Nasıl Doldurulur

1. [App Store Connect](https://appstoreconnect.apple.com) > Uygulamam > **Uygulama Gizliliği** sekmesine gidin.
2. **"Başlayın"** veya **"Düzenle"** butonuna tıklayın.
3. **"Sizin veya üçüncü taraf ortaklarınız bu uygulamadan veri topluyor mu?"** sorusuna **"Evet"** yanıtını verin.

### Veri Türlerini Ekleme

4. **Tanımlayıcılar** kategorisini seçin:
   - "Cihaz Kimliği" kutusunu işaretleyin
   - Amacı: **"Üçüncü Taraf Reklamcılık"** olarak belirleyin
   - Kullanıcıya bağlı: **Hayır**
   - İzleme: **Evet**

5. **Kullanım Verileri** kategorisini seçin:
   - "Ürün Etkileşimi" kutusunu işaretleyin
   - Amacı: **"Analitik"** olarak belirleyin
   - Kullanıcıya bağlı: **Hayır**
   - İzleme: **Hayır**

6. **Satın Almalar** kategorisini seçin:
   - "Satın Alma Geçmişi" kutusunu işaretleyin
   - Amacı: **"Uygulama İşlevselliği"** olarak belirleyin
   - Kullanıcıya bağlı: **Hayır**
   - İzleme: **Hayır**

7. **"Yayınla"** butonuna tıklayarak kaydedin.

---

## Toplanmayan Veri Türleri

Aşağıdaki veri türleri bu uygulama tarafından **toplanmamaktadır**:

- İletişim Bilgileri (ad, e-posta, telefon)
- Sağlık ve Fitness verileri
- Finansal bilgiler (kredi kartı vb. — Apple Pay üzerinden Apple yönetiyor)
- Konum verileri
- Kişiler (rehber)
- Kullanıcı İçeriği (fotoğraf, video vb.)
- Arama Geçmişi
- Tarama Geçmişi
- Hassas Bilgiler
- Vücut verileri
- Teşhis verileri (çökme raporları vb. — ayrı bir SDK yok)

---

## PrivacyInfo.xcprivacy ile Uyum

`PrivacyInfo.xcprivacy` dosyasında zaten aşağıdaki beyanlar yapılmıştır:

| Beyan | Durum |
|-------|-------|
| `NSPrivacyTracking` = true | İzleme yapıldığı bildirildi |
| `NSPrivacyTrackingDomains` | googleads.g.doubleclick.net, pagead2.googlesyndication.com |
| `NSPrivacyCollectedDataTypeDeviceID` | Üçüncü Taraf Reklamcılık amacıyla |
| `NSPrivacyCollectedDataTypeProductInteraction` | Analitik amacıyla |
| `NSPrivacyCollectedDataTypePurchaseHistory` | Uygulama İşlevselliği amacıyla |
| `NSPrivacyAccessedAPICategoryUserDefaults` | Sebep: CA92.1 |
| `NSPrivacyAccessedAPICategoryFileTimestamp` | Sebep: C617.1 |

App Store Connect'teki beyanlar bu dosya ile tutarlıdır.
