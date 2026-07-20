# Pro Değerini Artıran 7 Yeni Özellik Planı (iOS)

## Bağlam

Bölüm 1 (fiyatlandırma + paywall + tetikleyiciler + analitik) tamamlandı: abonelik ürünleri, yeni PaywallView, onboarding/son-hak tetikleyicileri, günde 1 limit, Firebase Analytics sarmalayıcısı eklendi. Tam derleme doğrulaması bekliyor (Firebase paketi çözümlendi, exit 0).

Bu plan, "Pro'da limit dışında ne var?" sorusuna cevap veren 7 özelliği ekler. Kullanıcı kararları: **yeni ARAÇLAR sekmesi** · **araçlar herkese görünür + kilitli** (dokununca paywall) · **şablonlarda genel başlangıç seti** (yayın öncesi kullanıcı metinleri gözden geçirir).

Keşif bulguları (yeniden kullanılacak altyapı):
- `Core/UDFParser/ZIPExtractor.swift` — elle yazılmış ZIP okuyucu (imza dosyalarını çıkarmak için de kullanılacak)
- `Core/Converters/PDFConverter.swift` — `convert(document:)`, `outputDirectory()` (= `Documents/ConvertedFiles`, tüm çıktıların tek adresi), TextKit tabanlı `renderPDFWithTextKit`
- `Core/Converters/UDFCreator.swift` — `InputParagraph`/`InputRun` + private `buildUDF` (şablonlar için public API eklenecek)
- `Core/Converters/WordConverter.swift` — `convert(document: UDFDocument)` (OCR çıktısı için)
- `Services/ConversionStorage.swift` — `addRecord` (tüm araç çıktıları geçmişe yazılır)
- `Features/Preview/DocumentPreviewView.swift`, `ResultView`'daki `ActivityViewController`/`ExportFileDocument`
- Pro kontrolü: `LimitService.shared.isPremium`; paywall: `PaywallView(source:)`
- **Info.plist'te belge türü kaydı ve kodda `onOpenURL` YOK** — dışarıdan UDF açma sıfırdan eklenecek

## 0. Ortak altyapı — ARAÇLAR sekmesi

- `UDF_Donusturucu_App.swift` → `MainTabView`'a 4. sekme: "ARAÇLAR" (`wrench.and.screwdriver.fill`).
- Yeni `Features/Tools/ToolsView.swift`: 5 araç kartı (Birleştir, Sıkıştır, Şifrele, E-İmza Bilgisi, OCR, Şablonlar). Ücretsiz kullanıcıda her kartta kilit rozeti; dokununca `PaywallView(source: "tools_<araç>")`. Premium'da doğrudan araç ekranı açılır.
- `AnalyticsService`'e `logToolOpened(tool)`, `logToolLockedTap(tool)` eklenir (kilitli karta dokunma = güçlü satın alma niyeti sinyali).
- `PaywallView` fayda listesi güncellenir: "Belge birleştirme, sıkıştırma ve şifreleme", "Taranmış belgeler için OCR", "Dilekçe şablonları" satırları eklenir (liste 6 satırı geçmesin diye "30 günlük geçmiş" ve "toplu dönüştürme" tek satırda birleştirilebilir).

## 1. Toplu dönüştürme Pro kilidi (ContentView)

- `UDFDocumentPicker` / `PDFDOCXDocumentPicker` (`ContentView.swift:31-67`): `allowsMultipleSelection` parametreli olur → `LimitService.shared.isPremium`.
- `addFiles`: ücretsiz kullanıcı 2. dosyayı eklemeye çalışırsa ilk dosya tutulur, "Toplu dönüştürme bir Pro özelliğidir" uyarısı + "Pro'ya Yükselt" → `PaywallView(source: "batch")`.
- Böylece paywall'daki "Toplu dönüştürme desteği" vaadi gerçek bir kısıta dönüşür.

## 2. Belge birleştirme — Pro

- `Core/Tools/MergeService.swift`: girişler UDF ise `UDFParser.parse` → `PDFConverter.convert` ile PDF'e çevrilir; PDF'ler doğrudan alınır. PDFKit `PDFDocument` ile sayfalar sırayla tek belgede toplanır → `outputDirectory()/Birlestirilmis_<tarih>.pdf`; `ConversionStorage.addRecord` ile geçmişe yazılır.
- `Features/Tools/MergeView.swift`: çoklu dosya seçimi (mevcut picker'lar), sürükle-bırak sıralama (`List.onMove`), "Birleştir" butonu, sonuçta önizleme/paylaş.

## 3. PDF sıkıştırma + şifreleme — Pro

- `Core/Tools/PDFToolsService.swift`:
  - `compress(url:quality:)` — her sayfa `PDFPage.thumbnail(of:for:)` ile bitmap'e alınır, JPEG (0.5/0.7 kalite) olarak `UIGraphicsPDFRenderer` ile yeni PDF'e yazılır; önce/sonra boyutları döner. Not: metin PDF'i görüntüye çevirir — UI'da "taranmış/büyük PDF'ler için" diye konumlandırılır.
  - `encrypt(url:password:)` — `PDFDocument.write(to:withOptions: [.userPasswordOption:…, .ownerPasswordOption:…])`.
- `Features/Tools/CompressView.swift` (kalite seçimi + boyut karşılaştırması), `Features/Tools/EncryptView.swift` (SecureField ile parola + tekrar).

## 4. E-imza bilgisi görüntüleme — Pro (best-effort)

- `Core/Tools/SignatureInspector.swift`: `.udf` → `ZIPExtractor.extractEntries`; adı "imza"/"sign"/".p7s"/".sig" içeren girdiler bulunur. Küçük bir DER/ASN.1 yürüyücüsü ile CMS SignedData içindeki X.509 sertifikaları çıkarılır → `SecCertificateCreateWithData` + `SecCertificateCopySubjectSummary` (imzacı adı); signingTime (OID 1.2.840.113549.1.9.5) best-effort okunur.
- `Features/Tools/SignatureInfoView.swift`: UDF seç → imzacı(lar), tarih; bulunamazsa "İmza bulunamadı". Ekranda kalıcı not: **"Bilgi amaçlıdır; hukuki geçerlilik doğrulaması yapılmaz."** (zincir doğrulaması kapsam dışı).

## 5. OCR: Taranmış PDF → metin/DOCX/UDF — Pro

- `Core/Tools/OCRService.swift`: PDFKit ile sayfa → ~300dpi bitmap; `VNRecognizeTextRequest` (`.accurate`, diller `["tr-TR","en-US"]`, dil düzeltmesi açık); sayfa başına ilerleme callback'i.
- Çıktı: düz metin → basit `UDFDocument` kurularak mevcut `WordConverter.convert` (DOCX), `UDFCreator` (UDF) ve metni kopyalama seçenekleri. Aranabilir-PDF (görünmez metin katmanı) v1 kapsam dışı.
- `Features/Tools/OCRView.swift`: PDF/görsel seç, sayfa ilerlemesi, sonuç önizleme + dışa aktarım.

## 6. Dilekçe şablonları — Pro

- `Resources/templates.json` (bundle): `{id, title, category, fields:[{key,label,placeholder}], body}` — body içinde `{{alan}}` yer tutucuları. Başlangıç seti (6): genel dilekçe, itiraz dilekçesi, icra takibine itiraz, tanık listesi, mazeret dilekçesi, ek süre talebi. **Metinler genel iskelettir; yayın öncesi kullanıcı gözden geçirecek (manuel adım).**
- `Core/Tools/TemplateEngine.swift`: yer tutucu doldurma → `[UDFCreator.InputParagraph]`; `UDFCreator`'a public `create(paragraphs:fileName:)` overload'u eklenir (mevcut private `buildUDF`'i çağırır).
- `Features/Tools/TemplatesView.swift` (kategori/liste) + `TemplateFormView.swift` (alan formu, önizleme, "UDF Oluştur" / "PDF Oluştur" — PDF için düz metinli `UDFDocument` + `PDFConverter`).

## 7. Mail/WhatsApp'tan UDF açma — ÜCRETSİZ (edinim/kullanım artırıcı)

- `Info.plist` (kökteki): `UTImportedTypeDeclarations` (kimlik `com.velikececi.udf`, uzantı `udf`, `public.data`'ya conform) + `CFBundleDocumentTypes` (LSHandlerRank Alternate). `LSSupportsOpeningDocumentsInPlace` eklenmez (Inbox kopyası yeterli).
- `UDF_Donusturucu_App.swift`: `WindowGroup` içine `.onOpenURL` — gelen dosya `Documents/Inbox`'tan güvenli biçimde kopyalanır, basit bir `IncomingFileRouter: ObservableObject` (environmentObject) üzerinden `ContentView`'a iletilir → yön UDF→PDF seçilir, dosya listeye eklenir. `AnalyticsService.logFileOpenedExternal()` eklenir.
- Ücretsiz kalır: kullanım sıklığını, dolayısıyla limite takılma ve paywall görme oranını artırır.

## Uygulama sırası

1. Ortak altyapı (ARAÇLAR sekmesi + kilit deseni + paywall fayda listesi)
2. (1) Toplu kilit → (3) Sıkıştır/Şifrele → (7) Dışarıdan açma  *(kolay, hızlı değer)*
3. (2) Birleştirme → (6) Şablonlar
4. (5) OCR → (4) E-imza  *(en karmaşık ikisi)*

## Doğrulama

1. `xcodebuild -project "UDF Dönüştürücü.xcodeproj" -scheme "UDF Dönüştürücü" -destination 'generic/platform=iOS Simulator' build` — Bölüm 1 değişiklikleri + Firebase paketi + yeni kod birlikte derlenir.
2. Simülatörde: ARAÇLAR sekmesi; ücretsiz kullanıcıda kilit rozetleri ve paywall açılışı; premium'da (StoreKit test satın alması sonrası) araçların çalışması.
3. Fonksiyonel: iki dosya birleştirme → tek PDF; sıkıştırmada boyut düşüşü; şifreli PDF'in parola sorması; örnek taranmış PDF'te OCR metni; şablondan üretilen UDF'in önizlemede açılması; imzalı örnek UDF'te imzacı bilgisi.
4. Toplu kilit: ücretsizken picker'da tek seçim + 2. dosyada paywall.
5. Dışarıdan açma: Files/AirDrop'tan .udf "Şuraya Aç → Evrak Dönüştürücü" ile uygulamaya düşmesi.

## Manuel adımlar (kullanıcı)

1. Şablon metinlerini (templates.json) hukuki açıdan gözden geçirip düzeltme — yayın öncesi şart.
2. App Store ekran görüntülerine yeni araçları ekleme; abonelik ürünleri + Firebase `GoogleService-Info.plist` (Bölüm 1'den bekleyen adımlar).
