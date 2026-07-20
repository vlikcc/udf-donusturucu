import Foundation
import PDFKit

enum MergeError: LocalizedError {
    case cannotOpenFile(String)
    case noPages
    case writeFailed

    var errorDescription: String? {
        switch self {
        case .cannotOpenFile(let name):
            return "\(name) dosyası açılamadı."
        case .noPages:
            return "Birleştirilecek sayfa bulunamadı."
        case .writeFailed:
            return "Birleştirilmiş PDF kaydedilemedi."
        }
    }
}

/// Birden fazla UDF/PDF dosyasını tek bir PDF'te birleştirir.
/// UDF girdileri önce mevcut dönüştürme motoruyla PDF'e çevrilir.
final class MergeService {

    /// Verilen sıradaki dosyaları tek PDF'te birleştirir ve çıktı URL'sini döner.
    static func merge(urls: [URL], progress: ((Int, Int) -> Void)? = nil) throws -> URL {
        let merged = PDFDocument()

        for (index, url) in urls.enumerated() {
            progress?(index, urls.count)

            let pdfURL: URL
            if url.pathExtension.lowercased() == "udf" {
                let document = try UDFParser.parse(fileURL: url)
                pdfURL = try PDFConverter.convert(document: document)
            } else {
                pdfURL = url
            }

            guard let pdf = PDFDocument(url: pdfURL) else {
                throw MergeError.cannotOpenFile(url.lastPathComponent)
            }
            for pageIndex in 0..<pdf.pageCount {
                if let page = pdf.page(at: pageIndex) {
                    merged.insert(page, at: merged.pageCount)
                }
            }
        }

        guard merged.pageCount > 0 else { throw MergeError.noPages }

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmm"
        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Birlestirilmis_\(formatter.string(from: Date()))")
            .appendingPathExtension("pdf")

        guard merged.write(to: outputURL) else { throw MergeError.writeFailed }
        progress?(urls.count, urls.count)
        return outputURL
    }
}
