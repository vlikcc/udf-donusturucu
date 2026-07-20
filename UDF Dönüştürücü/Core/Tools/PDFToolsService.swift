import Foundation
import PDFKit
import UIKit

enum PDFToolsError: LocalizedError {
    case cannotOpen
    case compressionFailed
    case encryptionFailed
    case alreadyEncrypted

    var errorDescription: String? {
        switch self {
        case .cannotOpen:
            return "PDF dosyası açılamadı."
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
        case balanced
        case aggressive

        var id: String { rawValue }

        var title: String {
            switch self {
            case .balanced: return "Dengeli"
            case .aggressive: return "Maksimum Sıkıştırma"
            }
        }

        var subtitle: String {
            switch self {
            case .balanced: return "İyi görüntü kalitesi, orta boyut"
            case .aggressive: return "Düşük boyut, azalan görüntü kalitesi"
            }
        }

        var jpegQuality: CGFloat {
            switch self {
            case .balanced: return 0.6
            case .aggressive: return 0.35
            }
        }

        /// Sayfa bitmap'inin nokta boyutuna uygulanan ölçek (150 / 110 dpi'a karşılık gelir).
        var renderScale: CGFloat {
            switch self {
            case .balanced: return 150.0 / 72.0
            case .aggressive: return 110.0 / 72.0
            }
        }
    }

    struct CompressionResult {
        let outputURL: URL
        let originalBytes: Int64
        let compressedBytes: Int64
    }

    /// Her sayfayı bitmap'e çevirip JPEG olarak yeni bir PDF'e yazar.
    /// Metin katmanı kaybolur — taranmış/büyük PDF'ler için uygundur.
    static func compress(url: URL, quality: CompressionQuality) throws -> CompressionResult {
        guard let document = PDFDocument(url: url) else { throw PDFToolsError.cannotOpen }
        guard document.pageCount > 0 else { throw PDFToolsError.compressionFailed }

        let originalBytes = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0

        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Sikistirilmis_\(url.deletingPathExtension().lastPathComponent)")
            .appendingPathExtension("pdf")

        let firstPageBounds = document.page(at: 0)?.bounds(for: .mediaBox) ?? CGRect(x: 0, y: 0, width: 595, height: 842)
        let format = UIGraphicsPDFRendererFormat()
        let renderer = UIGraphicsPDFRenderer(bounds: firstPageBounds, format: format)

        try renderer.writePDF(to: outputURL) { context in
            for pageIndex in 0..<document.pageCount {
                guard let page = document.page(at: pageIndex) else { continue }
                let bounds = page.bounds(for: .mediaBox)

                let renderSize = CGSize(
                    width: bounds.width * quality.renderScale,
                    height: bounds.height * quality.renderScale
                )
                let bitmap = page.thumbnail(of: renderSize, for: .mediaBox)

                // JPEG'e çevirip geri yükleyerek sayfayı sıkıştırılmış görüntü olarak göm.
                guard let jpegData = bitmap.jpegData(compressionQuality: quality.jpegQuality),
                      let compressedImage = UIImage(data: jpegData) else { continue }

                context.beginPage(withBounds: CGRect(origin: .zero, size: bounds.size), pageInfo: [:])
                compressedImage.draw(in: CGRect(origin: .zero, size: bounds.size))
            }
        }

        let compressedBytes = (try? FileManager.default.attributesOfItem(atPath: outputURL.path)[.size] as? Int64) ?? 0
        guard compressedBytes > 0 else { throw PDFToolsError.compressionFailed }

        return CompressionResult(
            outputURL: outputURL,
            originalBytes: originalBytes,
            compressedBytes: compressedBytes
        )
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
