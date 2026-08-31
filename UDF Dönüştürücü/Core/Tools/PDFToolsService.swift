import Foundation
import PDFKit
import UIKit

enum PDFToolsError: LocalizedError {
    case cannotOpen
    case locked
    case compressionFailed
    case encryptionFailed
    case alreadyEncrypted

    var errorDescription: String? {
        switch self {
        case .cannotOpen:
            return "PDF dosyası açılamadı."
        case .locked:
            return "Bu PDF parola korumalı. Önce parolasını kaldırmanız gerekiyor."
        case .compressionFailed:
            return "PDF sıkıştırılamadı."
        case .encryptionFailed:
            return "PDF şifrelenemedi."
        case .alreadyEncrypted:
            return "Bu PDF zaten parola korumalı."
        }
    }
}

/// PDF sıkıştırma ve parola ile şifreleme araçları.
final class PDFToolsService {

    enum CompressionQuality: String, CaseIterable, Identifiable {
        case lossless
        case balanced
        case aggressive

        var id: String { rawValue }

        var title: String {
            switch self {
            case .lossless: return "Kayıpsız"
            case .balanced: return "Dengeli"
            case .aggressive: return "Maksimum Sıkıştırma"
            }
        }

        var subtitle: String {
            switch self {
            case .lossless: return "Metin araması korunur, yalnızca gömülü görseller optimize edilir"
            case .balanced: return "İyi görüntü kalitesi, orta boyut — metin araması kaybolur"
            case .aggressive: return "Düşük boyut, azalan görüntü kalitesi — metin araması kaybolur"
            }
        }

        /// Metin katmanının korunup korunmadığı. Kayıplı modlarda sayfalar bitmap'e çevrilir.
        var preservesText: Bool { rasterization == nil }

        /// Kayıplı modlarda sayfa bitmap'ine uygulanan parametreler.
        /// `renderScale` sayfanın nokta boyutuna uygulanır (150 / 110 dpi'a karşılık gelir).
        /// Kayıpsız modda `nil` — sayfalar hiç yeniden çizilmez.
        var rasterization: (jpegQuality: CGFloat, renderScale: CGFloat)? {
            switch self {
            case .lossless: return nil
            case .balanced: return (0.6, 150.0 / 72.0)
            case .aggressive: return (0.35, 110.0 / 72.0)
            }
        }
    }

    struct CompressionResult {
        let outputURL: URL
        let originalBytes: Int64
        let compressedBytes: Int64
        let preservedText: Bool
    }

    static func compress(url: URL, quality: CompressionQuality) throws -> CompressionResult {
        guard let document = PDFDocument(url: url) else { throw PDFToolsError.cannotOpen }
        guard !document.isLocked else { throw PDFToolsError.locked }
        guard document.pageCount > 0 else { throw PDFToolsError.compressionFailed }

        let originalBytes = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0

        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Sikistirilmis_\(url.deletingPathExtension().lastPathComponent)")
            .appendingPathExtension("pdf")

        if let raster = quality.rasterization {
            try rasterize(document: document, to: outputURL, raster: raster)
        } else {
            try rewritePreservingText(document: document, to: outputURL)
        }

        let compressedBytes = (try? FileManager.default.attributesOfItem(atPath: outputURL.path)[.size] as? Int64) ?? 0
        guard compressedBytes > 0 else { throw PDFToolsError.compressionFailed }

        return CompressionResult(
            outputURL: outputURL,
            originalBytes: originalBytes,
            compressedBytes: compressedBytes,
            preservedText: quality.preservesText
        )
    }

    /// Kayıpsız yol: belgeyi yeniden yazar. Metin ve vektör içerik olduğu gibi taşınır;
    /// yalnızca gömülü görseller JPEG olarak yeniden kodlanıp ekran çözünürlüğüne indirgenir.
    /// Kullanılmayan nesneler ve şişmiş çapraz referans tabloları da bu sırada temizlenir.
    private static func rewritePreservingText(document: PDFDocument, to outputURL: URL) throws {
        let options: [PDFDocumentWriteOption: Any] = [
            .saveImagesAsJPEGOption: true,
            .optimizeImagesForScreenOption: true
        ]
        guard document.write(to: outputURL, withOptions: options) else {
            throw PDFToolsError.compressionFailed
        }
    }

    /// Kayıplı yol: her sayfayı bitmap'e çevirip JPEG olarak yeni bir PDF'e yazar.
    /// Metin katmanı kaybolur — taranmış/büyük PDF'ler için uygundur.
    private static func rasterize(
        document: PDFDocument,
        to outputURL: URL,
        raster: (jpegQuality: CGFloat, renderScale: CGFloat)
    ) throws {
        let firstPageBounds = document.page(at: 0)?.bounds(for: .mediaBox) ?? CGRect(x: 0, y: 0, width: 595, height: 842)
        let format = UIGraphicsPDFRendererFormat()
        let renderer = UIGraphicsPDFRenderer(bounds: firstPageBounds, format: format)

        try renderer.writePDF(to: outputURL) { context in
            for pageIndex in 0..<document.pageCount {
                guard let page = document.page(at: pageIndex) else { continue }
                let bounds = page.bounds(for: .mediaBox)

                let renderSize = CGSize(
                    width: bounds.width * raster.renderScale,
                    height: bounds.height * raster.renderScale
                )
                let bitmap = page.thumbnail(of: renderSize, for: .mediaBox)

                // JPEG'e çevirip geri yükleyerek sayfayı sıkıştırılmış görüntü olarak göm.
                guard let jpegData = bitmap.jpegData(compressionQuality: raster.jpegQuality),
                      let compressedImage = UIImage(data: jpegData) else { continue }

                context.beginPage(withBounds: CGRect(origin: .zero, size: bounds.size), pageInfo: [:])
                compressedImage.draw(in: CGRect(origin: .zero, size: bounds.size))
            }
        }
    }

    /// PDF'i kullanıcı parolasıyla şifreler (açarken parola sorulur).
    static func encrypt(url: URL, password: String) throws -> URL {
        guard let document = PDFDocument(url: url) else { throw PDFToolsError.cannotOpen }
        guard !document.isEncrypted else { throw PDFToolsError.alreadyEncrypted }

        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Sifreli_\(url.deletingPathExtension().lastPathComponent)")
            .appendingPathExtension("pdf")

        let options: [PDFDocumentWriteOption: Any] = [
            .userPasswordOption: password,
            .ownerPasswordOption: password
        ]
        guard document.write(to: outputURL, withOptions: options) else {
            throw PDFToolsError.encryptionFailed
        }
        return outputURL
    }
}
