import Foundation
import ImageIO
import PDFKit
import UniformTypeIdentifiers
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
        var preservesText: Bool { self == .lossless }

        /// Kayıplı modlarda sayfa bitmap'ine uygulanan parametreler.
        var rasterization: (jpegQuality: CGFloat, renderScale: CGFloat)? {
            switch self {
            case .lossless: return nil
            case .balanced: return (0.52, 110.0 / 72.0)
            case .aggressive: return (0.22, 72.0 / 72.0)
            }
        }

        /// PDF sayfasının yeniden oluşturulacağı yaklaşık çözünürlüğü temsil eder.
        var dpi: CGFloat {
            switch self {
            case .lossless: return 0
            case .balanced: return 110
            case .aggressive: return 72
            }
        }

        var jpegQuality: CGFloat {
            switch self {
            case .lossless: return 1.0
            case .balanced: return 0.52
            case .aggressive: return 0.22
            }
        }

        var maximumPixelDimension: CGFloat {
            switch self {
            case .lossless: return 2200
            case .balanced: return 2200
            case .aggressive: return 1600
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

        let originalBytes = fileSize(at: url)
        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Sikistirilmis_\(url.deletingPathExtension().lastPathComponent)")
            .appendingPathExtension("pdf")

        try removeExistingOutput(at: outputURL)
        if quality == .lossless {
            try rewritePreservingText(document: document, to: outputURL)
        } else {
            guard let destination = CGImageDestinationCreateWithURL(
                outputURL as CFURL,
                UTType.pdf.identifier as CFString,
                document.pageCount,
                nil
            ) else {
                throw PDFToolsError.compressionFailed
            }

            for pageIndex in 0..<document.pageCount {
                guard let page = document.page(at: pageIndex) else { continue }
                let pageBounds = page.bounds(for: .mediaBox)
                let image = render(page: page, bounds: pageBounds, quality: quality)
                let pageOptions: [CFString: Any] = [
                    kCGImageDestinationLossyCompressionQuality: quality.jpegQuality,
                    kCGImagePropertyDPIWidth: quality.dpi,
                    kCGImagePropertyDPIHeight: quality.dpi
                ]
                CGImageDestinationAddImage(destination, image, pageOptions as CFDictionary)
            }

            guard CGImageDestinationFinalize(destination) else {
                try? removeExistingOutput(at: outputURL)
                throw PDFToolsError.compressionFailed
            }
        }

        var compressedBytes = fileSize(at: outputURL)
        guard compressedBytes > 0 else { throw PDFToolsError.compressionFailed }

        // Sıkıştırılmış çıktı büyürse kullanıcıya daha büyük bir dosya vermeyiz.
        if originalBytes > 0, compressedBytes >= originalBytes {
            try? removeExistingOutput(at: outputURL)
            try FileManager.default.copyItem(at: url, to: outputURL)
            compressedBytes = originalBytes
        }

        return CompressionResult(
            outputURL: outputURL,
            originalBytes: originalBytes,
            compressedBytes: compressedBytes,
            preservedText: quality.preservesText
        )
    }

    /// Kayıpsız yol: metin ve vektör içerik korunur, gömülü görseller optimize edilir.
    private static func rewritePreservingText(document: PDFDocument, to outputURL: URL) throws {
        let options: [PDFDocumentWriteOption: Any] = [
            .saveImagesAsJPEGOption: true,
            .optimizeImagesForScreenOption: true
        ]
        guard document.write(to: outputURL, withOptions: options) else {
            throw PDFToolsError.compressionFailed
        }
    }

    /// Kayıplı yol: her sayfayı bitmap'e çevirip JPEG tabanlı yeni PDF oluşturur.
    private static func render(
        page: PDFPage,
        bounds: CGRect,
        quality: CompressionQuality
    ) -> CGImage {
        let longestSide = max(bounds.width, bounds.height)
        let requestedScale = quality.dpi / 72.0
        let maxScale = quality.maximumPixelDimension / max(longestSide, 1)
        let scale = min(requestedScale, maxScale)
        let pixelSize = CGSize(
            width: max(1, ceil(bounds.width * scale)),
            height: max(1, ceil(bounds.height * scale))
        )

        var format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        format.preferredRange = .standard

        let renderer = UIGraphicsImageRenderer(size: pixelSize, format: format)
        let image = renderer.image { rendererContext in
            UIColor.white.setFill()
            rendererContext.fill(CGRect(origin: .zero, size: pixelSize))
            rendererContext.cgContext.interpolationQuality = quality == .aggressive ? .medium : .high
            rendererContext.cgContext.saveGState()
            rendererContext.cgContext.scaleBy(x: scale, y: scale)
            rendererContext.cgContext.translateBy(x: -bounds.minX, y: -bounds.minY)
            page.draw(with: .mediaBox, to: rendererContext.cgContext)
            rendererContext.cgContext.restoreGState()
        }

        return image.cgImage ?? UIImage().cgImage!
    }

    private static func fileSize(at url: URL) -> Int64 {
        (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0
    }

    private static func removeExistingOutput(at url: URL) throws {
        if FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
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
