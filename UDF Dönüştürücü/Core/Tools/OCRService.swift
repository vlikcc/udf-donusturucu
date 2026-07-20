import Foundation
import PDFKit
import UIKit
import Vision

enum OCRError: LocalizedError {
    case cannotOpen
    case noText
    case recognitionFailed(String)

    var errorDescription: String? {
        switch self {
        case .cannotOpen:
            return "Dosya açılamadı."
        case .noText:
            return "Belgede okunabilir metin bulunamadı."
        case .recognitionFailed(let detail):
            return "Metin tanıma hatası: \(detail)"
        }
    }
}

/// Taranmış PDF veya görüntülerden Vision framework ile metin tanır (cihaz üzerinde, sunucusuz).
final class OCRService {

    /// PDF'in tüm sayfalarında OCR çalıştırır; sayfa metinlerini boş satırla ayırarak birleştirir.
    static func recognizeText(pdfURL: URL, progress: @escaping @MainActor (Int, Int) -> Void) async throws -> String {
        guard let document = PDFDocument(url: pdfURL), document.pageCount > 0 else {
            throw OCRError.cannotOpen
        }

        var pageTexts: [String] = []
        for pageIndex in 0..<document.pageCount {
            guard let page = document.page(at: pageIndex) else { continue }
            let bounds = page.bounds(for: .mediaBox)

            // ~300dpi hedefle, çok büyük sayfalarda 4000px ile sınırla.
            let targetScale = min(300.0 / 72.0, 4000.0 / max(bounds.width, bounds.height))
            let renderSize = CGSize(width: bounds.width * targetScale, height: bounds.height * targetScale)
            let bitmap = page.thumbnail(of: renderSize, for: .mediaBox)

            guard let cgImage = bitmap.cgImage else { continue }
            let text = try await recognize(cgImage: cgImage)
            if !text.isEmpty {
                pageTexts.append(text)
            }
            await progress(pageIndex + 1, document.pageCount)
        }

        let fullText = pageTexts.joined(separator: "\n\n")
        guard !fullText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw OCRError.noText
        }
        return fullText
    }

    /// Tek bir görüntü dosyasında OCR çalıştırır.
    static func recognizeText(imageURL: URL) async throws -> String {
        guard let image = UIImage(contentsOfFile: imageURL.path),
              let cgImage = image.cgImage else {
            throw OCRError.cannotOpen
        }
        let text = try await recognize(cgImage: cgImage)
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw OCRError.noText
        }
        return text
    }

    // MARK: - Vision

    private static func recognize(cgImage: CGImage) async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error {
                    continuation.resume(throwing: OCRError.recognitionFailed(error.localizedDescription))
                    return
                }
                let observations = (request.results as? [VNRecognizedTextObservation]) ?? []
                let lines = observations.compactMap { $0.topCandidates(1).first?.string }
                continuation.resume(returning: lines.joined(separator: "\n"))
            }
            request.recognitionLevel = .accurate
            request.recognitionLanguages = ["tr-TR", "en-US"]
            request.usesLanguageCorrection = true

            DispatchQueue.global(qos: .userInitiated).async {
                let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
                do {
                    try handler.perform([request])
                } catch {
                    continuation.resume(throwing: OCRError.recognitionFailed(error.localizedDescription))
                }
            }
        }
    }

    // MARK: - Dışa aktarım

    /// OCR metnini mevcut dönüştürme motoruyla DOCX'e yazar.
    static func exportDOCX(text: String, baseName: String) throws -> URL {
        try WordConverter.convert(document: plainDocument(text: text, fileName: baseName))
    }

    /// OCR metnini mevcut dönüştürme motoruyla PDF'e yazar.
    static func exportPDF(text: String, baseName: String) throws -> URL {
        try PDFConverter.convert(document: plainDocument(text: text, fileName: baseName))
    }

    /// OCR metnini UDF olarak yazar.
    static func exportUDF(text: String, baseName: String) throws -> URL {
        let paragraphs = text.components(separatedBy: "\n").map { line in
            UDFCreator.InputParagraph(
                runs: [UDFCreator.InputRun(
                    text: line, isBold: false, isItalic: false, isUnderline: false,
                    fontSize: 12, fontFamily: "Times New Roman"
                )],
                alignment: 0
            )
        }
        return try UDFCreator.create(fileName: "OCR_\(baseName)", paragraphs: paragraphs)
    }

    private static func plainDocument(text: String, fileName: String) -> UDFDocument {
        UDFDocument(
            fileName: "OCR_\(fileName)",
            content: UDFContent(
                text: text,
                rawContent: text,
                contentType: .plainText,
                sections: [UDFSection(title: nil, body: text, level: 0)],
                tables: [],
                isRTF: false,
                formattedString: nil
            ),
            metadata: nil,
            pageFormat: nil
        )
    }
}
