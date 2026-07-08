# Görüntüleme Özelliği Planı — Dönüştürülen Evrakların Uygulama İçinde Görüntülenmesi

**Hedef:** Kullanıcı, dönüştürme biter bitmez (Sonuç ekranı) ve daha sonra Geçmiş ekranından, dönüştürülen dosyayı (PDF / DOCX / UDF) uygulamadan çıkmadan görüntüleyebilsin — hem iOS hem Android'de.

---

## 1. Mevcut Durum Analizi

| | iOS | Android |
|---|---|---|
| Önizleme ekranı | `Features/Preview/DocumentPreviewView.swift` var (PDFKit + QuickLook) ama **hiçbir ekrandan çağrılmıyor — ölü kod** | `ui/preview/DocumentPreviewScreen.kt` var ve çalışıyor |
| Sonuç ekranından görüntüleme | ❌ Yok (yalnızca Paylaş / Kaydet) | ❌ Yok (yalnızca Paylaş / Kaydet) |
| Geçmiş ekranından görüntüleme | ❌ Yok (yalnızca Paylaş / Kaydet) | ✅ Var (`HistoryScreen` → `Routes.preview(recordId)`) |
| PDF önizleme | PDFKit, tüm sayfalar, zoom dahil | `PdfRenderer` ile **yalnızca ilk sayfa**, zoom yok |
| DOCX önizleme | QuickLook (yerel, biçimli render) | **Düz metin** (`DocxExtractor.plainText`) |
| UDF önizleme | QuickLook'a düşer → **.udf'yi render edemez** | ✅ Biçimli metin (kalın/italik/altı çizili) |

**Özet boşluklar:**
1. iOS'ta önizleme hiçbir yere bağlı değil; UDF dosyaları için render yolu yok.
2. Android'de Sonuç ekranından önizleme açılamıyor (`ConversionOutcome` yalnızca `outputFile: File?` taşıyor, `recordId` yok — mevcut rota ise `recordId` bekliyor).
3. Android önizleme kalitesi düşük: PDF tek sayfa, DOCX düz metin.

---

## 2. iOS Planı (Faz A)

### A1 — `DocumentPreviewView`'a UDF desteği
**Dosya:** `UDF Dönüştürücü/Features/Preview/DocumentPreviewView.swift`

- `url.pathExtension == "udf"` dalı ekle: mevcut `UDFParser` ile belgeyi ayrıştır, `AttributedString` üzerinden kalın/italik/altı çizili biçimli metin göster (Android'deki `buildAnnotatedFormattedText` mantığının Swift karşılığı; `UyapParagraph`/`TextRun` offsetleri zaten parser'da mevcut).
- Ayrıştırma `Task`/arka planda yapılsın; yüklenme sırasında `ProgressView`, hata durumunda kullanıcı dostu mesaj ("Dosya bulunamadı." / "Önizleme oluşturulamadı.").
- PDF → mevcut `PDFKitView`, DOCX → mevcut `QuickLookPreview` aynen kalır.

### A2 — Sonuç ekranına "Görüntüle" aksiyonu
**Dosya:** `UDF Dönüştürücü/Features/Result/ResultView.swift`

- `@State private var previewURL: URL?` ekle.
- Her başarılı satırdaki Paylaş/Kaydet butonlarının yanına `Görüntüle` (`eye` SF Symbol) ekle → `previewURL = result.outputURL`.
- `navigationDestination(item: $previewURL) { DocumentPreviewView(url: $0) }` ile push et. (`URL: Identifiable` uzantısı ResultView.swift'te zaten var; `ConversionView` de `NavigationStack` içinde olduğundan push çalışır.)

### A3 — Geçmiş ekranına görüntüleme
**Dosya:** `UDF Dönüştürücü/Features/History/HistoryView.swift`

- Kullanılabilir dosya satırına dokunma (veya üçüncü buton olarak `Görüntüle`) → `record.resolvedURL` ile `DocumentPreviewView` push edilir (A2'deki `navigationDestination(item:)` deseniyle aynı).
- `resolvedURL == nil` ise satır zaten "Diğer" bölümünde; ek durum gerekmez.

### A4 — (Opsiyonel) Önizleme ekranına "Kaydet"
- Toolbar'da Paylaş zaten var; parite için `fileExporter` tabanlı Kaydet eklenebilir. Öncelik düşük.

---

## 3. Android Planı (Faz B)

### B1 — Önizleme ekranını dosya-tabanlı hale getir + yeni rota
**Dosyalar:** `ui/preview/DocumentPreviewScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/AppNavHost.kt`

- `DocumentPreviewScreen`'in çekirdeğini `(title: String, file: File?)` alan bir composable'a ayır; `recordId` çözümlemesi ince bir sarmalayıcıda kalsın.
- `Routes`'a dosya yolu tabanlı ikinci rota ekle:
  ```kotlin
  const val PREVIEW_FILE_ARG = "filePath"
  const val PREVIEW_FILE = "previewFile/{$PREVIEW_FILE_ARG}"
  fun previewFile(path: String) = "previewFile/${Uri.encode(path)}"
  ```
  (Dosyalar `filesDir/ConvertedFiles/` altında olduğundan yol güvenle taşınabilir; `Uri.encode` ile Türkçe karakter/boşluk sorunu önlenir.)
- `AppNavHost`'a `PREVIEW_FILE` composable'ı ekle; mevcut `PREVIEW` (recordId) rotası Geçmiş için aynen kalır.

### B2 — Sonuç ekranına "Görüntüle" aksiyonu
**Dosyalar:** `ui/result/ResultScreen.kt`, `ui/navigation/AppNavHost.kt`

- `ResultScreen`'e `onOpenPreview: (File) -> Unit` parametresi ekle; `AppNavHost` bunu `navController.navigate(Routes.previewFile(file.absolutePath))` ile bağlar.
- `OutcomeRow`'a başarılı sonuçlar için `Görüntüle` IconButton'ı ekle (`Icons.Filled.Visibility`), Paylaş/Kaydet'in yanına.
- Geri tuşu önizlemeden Sonuç ekranına döner (normal back stack — ek iş yok).

### B3 — PDF önizlemesini çok sayfalı yap
**Dosya:** `ui/preview/DocumentPreviewScreen.kt`

- `renderFirstPdfPage` yerine: `LazyColumn` + sayfa başına tembel render.
  - `PdfRenderer` **thread-safe değildir** — tüm `openPage`/`render` çağrıları tek bir `Mutex` ile `Dispatchers.IO`'da serileştirilir.
  - Render ölçeği: net görüntü için ~2x, ancak OOM'a karşı bitmap kenarı ~2048 px ile sınırlandırılır (büyük sayfada ölçek düşürülür).
  - Ekrandan çıkan sayfaların bitmap'leri bırakılabilir (basit LRU veya `LazyColumn`'un doğal davranışı + `remember` anahtarlaması).
- Sayfa sayısı göstergesi (ör. üst barda "3 sayfa").

### B4 — DOCX önizlemesini biçimli hale getir
**Dosya:** `ui/preview/DocumentPreviewScreen.kt`

- `DocxExtractor.extract(file)` zaten `ExtractedContent.paragraphs` (`ExtractedTextRun`: bold/italic/underline + hizalama) döndürüyor — düz metin yerine bundan `AnnotatedString` üret.
- UDF için var olan `buildAnnotatedFormattedText` ile ortaklaştırılabilir: her iki model de "paragraf + run" yapısında; tek bir `AnnotatedString` kurucuya indirgenebilir (hizalama için `ParagraphStyle(textAlign = ...)`).

### B5 — "Harici uygulamada aç" aksiyonu
**Dosyalar:** `data/ShareUtils.kt`, `ui/preview/DocumentPreviewScreen.kt`

- `ShareUtils`'e `openFile(context, file)` ekle: `ACTION_VIEW` + `FileProvider` URI + `FLAG_GRANT_READ_URI_PERMISSION`; işleyici uygulama yoksa (`ActivityNotFoundException`) kullanıcıya bilgi ver.
- Önizleme üst barına buton olarak ekle — özellikle DOCX'te gömülü render sınırlı olduğundan kullanıcıya tam kaliteli alternatif sunar. (Manifest'te FileProvider zaten tanımlı, paylaşım bununla çalışıyor.)

### B6 — (Opsiyonel) PDF'te pinch-to-zoom
- `Modifier.graphicsLayer` + `rememberTransformableState` ile basit zoom. Öncelik düşük; B3'ten bağımsız eklenebilir.

---

## 4. Uygulama Sırası

| # | İş | Platform | Bağımlılık |
|---|---|---|---|
| 1 | A1 — UDF önizleme (AttributedString) | iOS | — |
| 2 | A2 — Sonuç → Görüntüle | iOS | A1 |
| 3 | A3 — Geçmiş → Görüntüle | iOS | A1 |
| 4 | B1 — Dosya-tabanlı önizleme rotası | Android | — |
| 5 | B2 — Sonuç → Görüntüle | Android | B1 |
| 6 | B3 — Çok sayfalı PDF | Android | — |
| 7 | B4 — Biçimli DOCX | Android | — |
| 8 | B5 — Harici uygulamada aç | Android | — |
| 9 | A4 / B6 — Opsiyoneller | ikisi | diğerleri bitince |

1–3 ve 4–5 iki platformda "görüntülenebilme" özelliğinin kendisidir (asıl istek); 6–8 önizleme kalitesini iOS ile eşitler.

---

## 5. Kabul Kriterleri

- [ ] Başarılı bir dönüşümden hemen sonra Sonuç ekranından dosya, uygulama içinde açılabiliyor (iOS + Android).
- [ ] Geçmiş'teki mevcut bir kayıt her iki platformda da önizlenebiliyor (Android'de zaten var; iOS'ta yeni).
- [ ] Üç format da görüntülenebiliyor: PDF (çok sayfalı), DOCX (iOS: QuickLook; Android: biçimli metin + harici aç), UDF (her ikisinde biçimli metin).
- [ ] Silinmiş/eksik dosyada çökme yok; anlaşılır Türkçe hata mesajı gösteriliyor.
- [ ] Önizlemeden geri dönüş, geldiği ekrana (Sonuç veya Geçmiş) sorunsuz dönüyor.

## 6. Test Planı

**Android (birim, saf JVM):**
- `AnnotatedString` kurucu fonksiyonu ayrı bir dosyaya (`ui/preview/PreviewText.kt` gibi) çıkarılırsa UDF/DOCX run-offset eşlemesi mevcut fikstürlerle (`sample_rtf.udf` vb.) JUnit ile test edilebilir.

**Elle (cihaz/simülatör) — her iki platformda:**
1. UDF → PDF dönüştür, Sonuç'tan görüntüle: tüm sayfalar geliyor mu, Türkçe karakterler doğru mu?
2. UDF → DOCX dönüştür, görüntüle: kalın/italik/altı çizili korunuyor mu?
3. PDF → UDF dönüştür, görüntüle: biçimli metin önizlemesi doğru mu?
4. Geçmiş'ten eski bir kaydı aç; sonra dosyayı silip (kaydı silmeden — dosya `ConvertedFiles/` altından elle silinebilir) tekrar aç → hata mesajı.
5. Android: 20+ sayfalık büyük bir PDF ile kaydırma akıcılığı ve bellek (OOM yok) kontrolü.
6. Android: "Harici uygulamada aç" — Word/PDF görüntüleyici yüklü ve yüklü değilken.

## 7. Riskler / Notlar

- **`PdfRenderer` thread-safety ve OOM (B3):** planın en riskli parçası; Mutex + bitmap boyut sınırı zorunlu. Bu ortamda Android derlenemediği için (bkz. ANDROID_PLAN.md uyarısı) ilk doğrulama Android Studio'da yapılmalı.
- **QuickLook DOCX render'ı (iOS):** yaklaşık bir görünüm verir (Word birebir değil) ama yerlidir ve sıfır maliyetlidir — yeterli kabul edilir.
- **Tablolar:** UDF parser tablo çıkarımı yapıyor ancak metin önizlemesi tabloları düz metin olarak gösterecek — bu sürümde kapsam dışı, ileride iyileştirilebilir.
- **Kapsam dışı:** UDF'yi düzenleme, DOCX'i Android'de birebir (sayfa düzeniyle) render etme, dosya içinde arama.
