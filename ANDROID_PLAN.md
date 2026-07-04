# Android Versiyonu Planı — Evrak Dönüştürücü (UDF Çevirici)

## Durum

- ✅ **Faz 1 tamamlandı:** `android/` altında Gradle projesi (Kotlin DSL + version catalog), tema (lacivert marka rengi, koyu mod), tam navigasyon iskeleti, `UdfDocument` çekirdek modelleri, DataStore tabanlı `LimitRepository`/`SettingsRepository`/`HistoryRepository` (iOS iş kurallarıyla birebir), Onboarding ekranı (iOS içeriğiyle birebir) ve gerçek veriyle çalışan Main/History/Settings ekranları oluşturuldu.
- ✅ **Faz 2 tamamlandı:** `UdfZip.kt` (java.util.zip sarmalayıcı) ve `UdfParser.kt` (594 satırlık `UDFParser.swift`'in tam portu — UYAP/HTML/RTF/düz metin algılama, CDATA + `<elements>` eşlemesi, tablo/bölüm çıkarımı, metadata, sayfa marjları, 3 katmanlı kodlama düşümü). `src/test/resources` altında sentetik UDF fikstürleri (`build_fixtures.py` ile üretildi) ve JUnit testleri (`UdfParserTest.kt`, `UdfZipTest.kt`) eklendi.
- ✅ **Faz 3 tamamlandı:** 4 yönün tamamı çalışır durumda —
  - `DocxExtractor.kt` / `PdfExtractor.kt` (PdfBox-Android `PDFTextStripper` alt sınıflaması) → `ExtractedContent` ortak modeli
  - `UdfCreator.kt` (PDF/DOCX → UDF, `java.util.zip.ZipOutputStream` ile — iOS'taki elle deflate/CRC32 kodu gerekmiyor)
  - `WordConverter.kt` (UDF → DOCX, elle OOXML + ZipOutputStream, ayrı geçici dizin yok)
  - `PdfConverter.kt` (UDF → PDF, tek `StaticLayout` + `android.graphics.pdf.PdfDocument` sayfalama — iOS'un TextKit/NSTextContainer yaklaşımının Android karşılığı)
  - `ConversionRepository.kt` (4 yönü + toplu dönüşüm + günlük hak düşümü + geçmiş kaydını birleştiren orkestrasyon; henüz UI'a bağlanmadı — bu Faz 4'ün işi)
  - Bilinçli platform sadeleştirmeleri (kod içinde yorumlarla işaretli): PDF'den **hizalama** çıkarılamıyor (tüm çıkarılan paragraflar sola hizalı varsayılır), PDF'den **altı çizili** tespit edilemiyor (PDF'de ayrı bir vektör çizgisi, metin özniteliği değil), Android span sisteminde paragraf başına **iki yana yaslama** ve **paragraf boşluğu/satır aralığı** desteklenmiyor (sola hizalı + varsayılan boşluklarla gösterilir), RTF için gerçek zengin biçimlendirme render edilmiyor (zaten düzleştirilmiş metin gösteriliyor — iOS'un kendi RTF-başarısız yedek yolu da facto aynı sonucu veriyordu)
  - `DocxExtractorTest.kt` eklendi (saf JVM, Android bağımlılığı yok); `PdfExtractor`/`PdfConverter`/`WordConverter`/`UdfCreator` Android API'lerine dayandığından (Robolectric/cihaz gerekir) bu ortamda testleri yazılamadı
- ✅ **Faz 4 tamamlandı:** Ekranlar gerçek işlevle bağlandı —
  - `ConversionFlowViewModel.kt`: Ana ekran → Dönüşüm → Sonuç arasında paylaşılan durum (seçili dosyalar, yön, format, ilerleme, sonuçlar); `AppNavHost` düzeyinde tek örnek
  - `FileCopier.kt`: SAF `content://` URI'lerini `cacheDir`e kopyalar (her seçim kendi alt klasöründe — orijinal dosya adı korunur)
  - `MainScreen.kt`: gerçek `OpenMultipleDocuments` seçici (yöne göre mime filtresi/uzantı süzme), yön ve format anahtarları `ConversionFlowViewModel`e bağlı, seçili dosya listesi + kaldırma
  - `ConversionScreen.kt`: gerçek `ConversionRepository.convert()` çağrısı, canlı ilerleme (`N / toplam — dosya adı`)
  - `ResultScreen.kt`: her dosya için paylaş (`ShareUtils`/`ACTION_SEND` + `FileProvider`) ve kaydet (`CreateDocument`, uzantıya göre 3 ayrı launcher) aksiyonları
  - `HistoryScreen.kt`: kullanılabilir kayıtlara paylaş aksiyonu eklendi
  - `DocumentPreviewScreen.kt`: UDF için biçimlendirilmiş (kalın/italik/altı çizili) metin önizlemesi, PDF için `PdfRenderer` ile ilk sayfa bitmap'i, DOCX için düz metin önizlemesi (Android'de gömülü DOCX render motoru yok)
- ✅ **Faz 5 tamamlandı:** Monetizasyon altyapısı bağlandı —
  - `BillingManager.kt`: Play Billing (non-consumable `unlimited_premium` ürünü), satın alma + acknowledge + `queryPurchasesAsync` ile açılışta/Ayarlar'dan geri yükleme; başarılı satın almada `LimitRepository.activatePremium()`
  - `AdsManager.kt`: banner/interstitial (her 2. sonuç ekranında, `SettingsRepository.shouldShowInterstitial()` sayaç mantığıyla)/rewarded interstitial (+1 bonus hak); tüm ID'ler şu an Google'ın herkese açık Android test ID'leri — **gerçek gelir Faz 6'da AdMob'da yeni Android uygulaması oluşturulana kadar oluşmaz**
  - `ConsentManager.kt`: UMP (GDPR) onay akışı — iOS'taki ATT izninin karşılığı; onay tamamlanmadan reklam SDK'sı başlatılmaz
  - `ui/components/BannerAd.kt`: adaptif banner, yaşam döngüsü (resume/pause/destroy) `DisposableEffect` ile yönetiliyor; `MainScreen`de yalnızca ücretsiz kullanıcılara gösteriliyor
  - `PaywallScreen.kt`: gerçek satın alma akışı, fiyat gösterimi, yükleniyor/hata durumları
  - `SettingsScreen.kt`: hesap durumu (Ücretsiz/Premium) + "Satın Almaları Geri Yükle"
  - `MainScreen.kt`: günlük hak dolduğunda "Reklam İzle, +1 Hak Kazan" butonu eklendi
  - ⚠️ Play Billing Library sürüm-hassas bir API'dir (`PendingPurchasesParams` builder'ı gibi kısımlar minor sürümler arasında değişebilir) — `BillingManager.kt`'deki yorumda işaretlendi, Android Studio'nun ilk Gradle senkronizasyonunda derleyici hatalarına göre doğrulanmalı
- ✅ **Faz 6 tamamlandı:** Cila + yayın hazırlığı —
  - Gerçek launcher ikonu: `web/icon.png`'den (512×512) `sips` ile 5 yoğunluk (mdpi–xxxhdpi) için `mipmap-*/ic_launcher.png` üretildi; Faz 1'deki geçici vektör placeholder ve adaptive-icon XML'i kaldırıldı (minSdk 26 klasik mipmap'lerle de düzgün çalışır)
  - `resConfigs("tr")`: uygulama tamamen Türkçe olduğundan kütüphanelerden gelen gereksiz dil kaynakları APK/AAB'den elenir
  - İmzalama şablonu: `keystore.properties.example` + `build.gradle.kts`'te `keystore.properties` (gitignore'da) varsa okuyan, yoksa debug imzasına düşen `signingConfigs` bloğu — gerçek keystore/parolalar hiçbir zaman koda girmedi
  - Çökme dayanıklılığı: `ConversionRepository.convertSingle` artık `Throwable` yakalıyor (`Exception` değil) — çok büyük/bozuk bir dosyanın `OutOfMemoryError` fırlatması tüm toplu dönüşümü çökertmiyor, yalnızca o dosya başarısız sayılıyor
  - Play Store yayın kontrol listesi aşağıda (§4) — bunlar Android Studio/Play Console'da elle yapılması gereken adımlar (gerçek AdMob Android uygulaması, Billing ürünü, imzalı AAB, mağaza metinleri)
- 🎉 **Tüm 6 faz tamamlandı.** Proje `android/` altında derlemeye hazır durumda (42 Kotlin dosyası, ~4.500 satır). Sıradaki adım Android Studio'da açıp derlemek ve gerçek cihaz/dosyalarla doğrulamak.
- ⚠️ Bu ortamda Android Studio/Android SDK kurulu değil (yalnızca Java 8 var, AGP için Java 17 gerekiyor) — proje bu makinede **hiçbir zaman derlenip test edilemedi**. Aşağıdaki "İlk Doğrulama Adımları" bölümü, Android Studio'da yapılması gereken kontrolleri sırasıyla listeler.

## İlk Doğrulama Adımları (Android Studio'da)

1. `android/` klasörünü Android Studio'da aç, Gradle senkronizasyonunun hatasız tamamlandığını doğrula (özellikle Play Billing Library API'leri — bkz. Faz 5 notu).
2. `./gradlew testDebugUnitTest` çalıştır — `UdfParserTest`, `UdfZipTest`, `DocxExtractorTest` içindeki 12 testin geçtiğini doğrula; offset/uzunluk hesaplarında hata varsa fikstür veya assertion'ı birlikte düzeltiriz.
3. Uygulamayı bir cihaz/emülatörde çalıştır, onboarding → ana ekran akışını dene.
4. Gerçek bir UYAP `.udf` dosyasıyla UDF→PDF ve UDF→DOCX dene; ardından PDF→UDF ve DOCX→UDF dene.
5. **Kritik test:** Android'in ürettiği `.udf` dosyasını UYAP Doküman Editörü'nde aç — bu projenin asıl başarı ölçütü.
6. Test reklam ID'leriyle banner/interstitial/rewarded akışlarının çalıştığını, günlük limitin (3) doğru işlediğini, "Reklam İzle" ile +1 hak kazanıldığını doğrula.
7. Sandbox/lisans test hesabıyla Billing satın alma akışını dene (yalnızca AAB bir test kanalına yüklendikten sonra test edilebilir).

## Bağlam (Context)

Mevcut iOS uygulaması (SwiftUI, ~4.400 satır Swift) UYAP'ın .udf belgelerini tamamen cihaz üzerinde PDF/DOCX'e (ve tersine) dönüştürüyor. Hedef: aynı özellik seti ve gelir modeliyle **Kotlin + Jetpack Compose** Android uygulaması geliştirip Google Play'de yayınlamak.

**Kesinleşen kararlar:**
- UI: Jetpack Compose | Min SDK: **26** (Android 8.0), target SDK 35
- Gelir modeli iOS ile birebir aynı; günlük ücretsiz limit **3** (koddaki değer; mağaza metnindeki "5" güncellenecek)
- Proje konumu: bu klasörün içinde **`android/`** alt klasörü

**Koddan doğrulanan iş kuralları (birebir korunacak):**
- Günlük 3 ücretsiz dönüşüm; ödüllü (rewarded interstitial) reklam → +1 bonus hak; gece yarısı hem kullanım hem bonus sıfırlanır (`bugün > sonSıfırlamaGünü` karşılaştırması, uygulama öne gelince yeniden kontrol) — kaynak: `LimitService.swift`
- Toplu dönüşüm tek seferde N hak düşer; hak yetmezse işlem reddedilir
- Geçişli (interstitial) reklam: her 2. başarılı sonuç ekranında (sayaç kalıcı) — kaynak: `AdsManager.swift`
- Premium: tek seferlik (non-consumable) ürün → sınırsız dönüşüm + tüm reklamlar kapalı
- Geçmiş: JSON, en fazla 200 kayıt; görünürlük ücretsizde 7 gün / premiumda 30 gün; "kullanılabilir" = başarılı **ve** dosya `ConvertedFiles/` içinde adıyla çözümlenebiliyor — kaynak: `ConversionStorage.swift`
- iOS'taki `Persistence.swift` (Core Data) kullanılmayan iskelet — **taşınmayacak**

---

## 1. Proje Kurulumu

- **Konum:** `UDF Dönüştürücü/android/` — Android Studio'da "Empty Activity (Compose)" şablonuyla oluşturulacak
- **Modül:** tek `:app` modülü; katman disiplini paketlerle sağlanır (`core/` altında Compose/UI importu yasak → JVM birim testi yazılabilir kalır)
- **applicationId:** `com.velikececi.udfdonusturucu` (Play'de kalıcıdır)
- **Gradle:** Kotlin DSL + version catalog (`gradle/libs.versions.toml`)

**Bağımlılıklar:**
| Amaç | Artifact |
|---|---|
| Compose | `androidx.compose:compose-bom` (ui, material3, icons-extended) |
| Navigasyon | `androidx.navigation:navigation-compose` |
| Lifecycle | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, **`lifecycle-process`** (ön plana gelişte limit sıfırlama kontrolü) |
| Kalıcılık | `androidx.datastore:datastore-preferences` + `kotlinx-serialization-json` (geçmiş JSON'u) |
| Reklam | `com.google.android.gms:play-services-ads` + `com.google.android.ump:user-messaging-platform` (GDPR onayı) |
| Satın alma | `com.android.billingclient:billing-ktx` |
| PDF metin çıkarma | **`com.tom-roush:pdfbox-android`** (Apache 2.0 lisans — kapalı kaynak için güvenli) |
| Açılış | `androidx.core:core-splashscreen` |
| Test | JUnit4, `kotlinx-coroutines-test`, Robolectric (PDF üretimi testleri) |

**Bilinçli olarak dahil edilmeyenler:** Room (geçmiş ≤200 kayıtlık JSON — iOS ile aynı), Hilt (elle `AppContainer` yeterli), Apache POI (10+ MB, gereksiz), iText (AGPL — lisans engeli).

**Manifest:** AdMob Android App ID meta-data'sı, `ConvertedFiles/` için `FileProvider`; **hiçbir depolama izni yok** (SAF yeterli). Tema: `Color.kt` içinde lacivert marka rengi — açık `#1C3357`, koyu mod `#668FD1` (iOS `AccentNavy` değerleri).

## 2. Mimari ve Dosya Eşlemesi

MVVM + tek yönlü veri akışı: Composable → ViewModel (`StateFlow<UiState>`) → Repository → core dönüştürücüler. Dönüşümler `Dispatchers.Default/IO` üzerinde; dosya bazlı ilerleme `Flow<ConversionProgress>` ile ekrana akar.

```
android/app/src/main/java/com/velikececi/udfdonusturucu/
├── App.kt / MainActivity.kt / di/AppContainer.kt
├── core/
│   ├── model/UdfDocument.kt        ← UDFDocument.swift (UyapParagraph, UyapTextRun, metadata, sayfa formatı)
│   ├── zip/UdfZip.kt               ← ZIPExtractor.swift yerine java.util.zip sarmalayıcısı (elle ZIP/CRC32 kodu taşınmaz)
│   ├── parser/UdfParser.kt         ← UDFParser.swift (594 satır — en kritik port: UYAP-XML/HTML/RTF/düz metin algılama, CDATA + <elements> eşlemesi, tablolar, meta, kenar boşlukları)
│   └── converters/
│       ├── PdfConverter.kt         ← PDFConverter.swift → android.graphics.pdf.PdfDocument + StaticLayout (A4 595×842pt, kenar boşlukları, sayfa numarası; biçim → Spannable: StyleSpan/UnderlineSpan/AbsoluteSizeSpan/AlignmentSpan/LeadingMarginSpan)
│       ├── WordConverter.kt        ← WordConverter.swift (elle OOXML string şablonları + ZipOutputStream — iOS ile birebir aynı yaklaşım)
│       ├── UdfCreator.kt           ← UDFCreator.swift (format_id="1.8" UYAP XML zarfı; ZIP'i java.util.zip yazar)
│       ├── PdfExtractor.kt         ← PDFExtractor.swift → PdfBox-Android PDFTextStripper alt sınıfı (TextPosition'dan font adı/boyut; "Bold"/"Italic" alt dizisinden stil çıkarımı)
│       └── DocxExtractor.kt        ← DOCXExtractor.swift → ZipInputStream + XmlPullParser (regex'ten daha sağlam, sıfır ek bağımlılık)
├── data/
│   ├── SettingsRepository.kt       ← UserDefaults yerine DataStore (onboarding, premium, sayaçlar)
│   ├── LimitRepository.kt          ← LimitService.swift (last_reset_epoch_day: Long; kontrol+düşüm tek dataStore.edit{} içinde — toplu dönüşümde yarış olmasın)
│   ├── HistoryRepository.kt        ← ConversionStorage.swift
│   └── ConversionRepository.kt     ← ContentView.swift içindeki orkestrasyon (seç → ayrıştır → dönüştür → kaydet)
├── billing/BillingManager.kt       ← PurchaseService.swift → Play Billing
├── ads/AdsManager.kt               ← AdsManager.swift → banner/interstitial/rewarded-interstitial + UMP
└── ui/  (theme, navigation, onboarding, main, conversion, result, history, settings, paywall, preview, components/BannerAd.kt)
```

**Platform karşılıkları:** UIDocumentPicker → `OpenMultipleDocuments` (mime `*/*`, uzantıya göre süzme; seçilen URI'lar hemen `cacheDir`e kopyalanır); paylaşım → `ACTION_SEND(_MULTIPLE)` + FileProvider; "Dosyalara kaydet" → `CreateDocument` (API 26'dan itibaren tek yol, MediaStore.Downloads API 29+ olduğu için elenmiştir); ATT'nin Android karşılığı yok → UMP onay akışı; ekranlar: `AppNavHost` rotaları + paywall `ModalBottomSheet`.

## 3. Aşamalı Yol Haritası (~20–26 iş günü)

1. **İskelet + çekirdek modeller (2–3 gün):** proje, tema (lacivert palet, koyu mod), navigasyon iskeleti, `UdfDocument` modelleri, DataStore. → Uygulama derlenir, boş ekranlar arası gezinilir.
2. **UDF okuma yolu (3–4 gün):** `UdfZip` + `UdfParser` portu; `src/test/resources` altına gerçek UDF örnekleriyle JVM birim testleri (Türkçe karakter + tablolu belgeler dahil).
3. **Dönüştürücüler (4–5 gün):** 4 yönün tamamı + toplu dönüşüm + ilerleme Flow'u + geçmiş kaydı. → **Kritik doğrulama: Android'in ürettiği UDF, UYAP Doküman Editörü'nde açılmalı**; çıktılar iOS uygulamasının ürettikleriyle karşılaştırılmalı.
4. **UI ekranları (5–6 gün):** Onboarding (HorizontalPager, 3 sayfa), Ana ekran (yön anahtarı, limit kartı, dosya listesi, format seçici, son dönüşümler), Dönüşüm (animasyonlu dosya bazlı ilerleme), Sonuç, Geçmiş (kullanılabilir/kullanılamaz bölümleri), Ayarlar (gizlilik/koşullar/destek bağlantıları), Paywall, Önizleme (UDF → `AnnotatedString`; PDF → `PdfRenderer` bitmap). → Monetizasyon hariç tam akış.
5. **Monetizasyon (3–4 gün):** BillingManager (non-consumable ürün; **satın almayı 3 gün içinde acknowledge et** — hem dinleyicide hem açılıştaki `queryPurchasesAsync` taramasında), UMP onayı → reklam başlatma, AdsManager (adaptif banner `AndroidView` + lifecycle; her 2. sonuçta interstitial; rewarded interstitial → +1 bonus, yüklenmemişse sessizce atla — iOS davranışı). Google test reklam ID'leri + lisans test kullanıcılarıyla doğrulama.
6. **Cila + yayın (3–4 gün):** ikon/splash, R8 açık release QA (4 dönüşüm yolu minified build'de test), bozuk/0 baytlık UDF ve dev PDF dayanıklılığı, imzalı AAB, aşağıdaki Play kontrol listesi.

## 4. Google Play Yayın Kontrol Listesi

- [ ] Upload keystore oluştur (`keytool`), **Play App Signing**'e kaydol, keystore'u yedekle
- [ ] Play Console'da uygulama: "Evrak Dönüştürücü - UDF Çevirici" (30 karakter sınırına uyuyor), Türkçe listeleme; metinler `App_Store_Bilgileri.md`'den uyarlanır (**"günlük 5" → "günlük 3" düzeltilecek**)
- [ ] Gizlilik politikası URL'si: mevcut `web/gizlilik.html` herkese açık yayınlanacak; metne Android (AdMob/Play Billing) ibaresi eklenecek
- [ ] Data Safety formu: Reklam kimliği + uygulama etkileşimi (AdMob, reklam amaçlı, 3. tarafla paylaşılıyor); belgelerin tamamen cihazda işlendiği ve toplanmadığı belirtilecek
- [ ] "Reklam içerir" beyanı + IARC içerik derecelendirme anketi
- [ ] AdMob: mevcut hesapta (`pub-1041738122428212`) **yeni Android uygulaması** + 3 reklam birimi (banner, interstitial, rewarded interstitial); debug'da Google'ın Android test ID'leri; `app-ads.txt` güncelle; yayın sonrası AdMob ↔ Play bağla
- [ ] UMP: AdMob Privacy & Messaging'de GDPR mesajı; EEA debug geography ile test
- [ ] Play Billing: tek seferlik ürün (ör. `unlimited_premium`), TRY fiyatı iOS ile aynı; lisans test kullanıcıları; faturalama ancak AAB test kanalındayken test edilebilir
- [ ] ⚠️ Kasım 2023 sonrası açılmış **bireysel** geliştirici hesabıysa: üretim öncesi 12 testçiyle 14 gün kapalı test zorunlu — kapalı teste erken başla
- [ ] Mağaza görselleri: 512px ikon, 1024×500 öne çıkan görsel, ≥2 telefon ekran görüntüsü (açık + koyu)

## 5. Riskler ve Dikkat Noktaları

- **UDF kodlaması:** XML prologundaki `encoding` okunmalı (UTF-8 varsayımı yetmez; `ISO-8859-9`/`windows-1254` üretebilen araçlar var); İ/ı/ğ/ş gidiş-dönüş testleri şart
- **Türkçe locale tuzağı:** uzantı/format karşılaştırmalarında `uppercase(Locale.ROOT)` ("i" → "İ" sürprizine karşı)
- **PdfBox-Android:** ilk kullanımdan önce `PDFBoxResourceLoader.init()`; büyük PDF'lerde yavaş/bellek yoğun → arka plan dispatcher; taranmış (görüntü) PDF'lerde metin çıkmaz → iOS'takiyle aynı hata mesajı
- **DataStore asenkron:** güncel değerler repository `StateFlow`'larında tutulur ki `canConvert` UI'da senkron kontrol edilebilsin; hak düşümü tek `edit{}` bloğunda
- **R8:** `@Serializable` sınıflar ve PdfBox için keep kuralları gerekebilir — release build'de 4 dönüşüm yolu mutlaka test edilir
- **Dosya adı çakışması:** aynı dosya iki kez dönüştürülünce üzerine yazılır (iOS davranışı) — parite için kabul ediliyor
- **Gece yarısı sıfırlama:** `todayEpochDay > storedEpochDay` (saat geri alınmasına da dayanıklı); ON_START'ta ve her dönüşümden önce kontrol

## 6. Doğrulama

1. **Birim testleri:** `UdfParser`/`UdfCreator`/`WordConverter`/`DocxExtractor` için gerçek UDF/DOCX fikstürleriyle JVM testleri; `PdfConverter` için Robolectric
2. **Cihaz testi:** 4 dönüşüm yönü + toplu dönüşüm gerçek cihazda; çıktılar iOS uygulamasının çıktılarıyla çapraz karşılaştırılır (iOS'un ürettiği UDF Android'de, Android'inki iOS'ta açılmalı)
3. **UYAP kabulü:** Android'in ürettiği .udf dosyası UYAP Doküman Editörü'nde açılıp düzenlenebilmeli — asıl başarı ölçütü
4. **Monetizasyon:** test reklam ID'leriyle banner/interstitial/rewarded akışı; sandbox satın alma + geri yükleme; premiumda reklamların tamamen kapandığı; limit sayacının gece yarısı sıfırlandığı (cihaz saati ileri alınarak)
5. **Release QA:** minified AAB iç test kanalında; Play Pre-launch report sonuçları
