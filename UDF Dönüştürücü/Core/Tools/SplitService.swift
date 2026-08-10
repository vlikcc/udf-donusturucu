import Foundation
import PDFKit
import UniformTypeIdentifiers

enum SplitError: LocalizedError {
    case cannotOpenFile(String)
    case emptyDocument
    case emptyRange
    case invalidToken(String)
    case pageOutOfBounds(page: Int, pageCount: Int)
    case writeFailed

    var errorDescription: String? {
        switch self {
        case .cannotOpenFile(let name):
            return "\(name) dosyası açılamadı."
        case .emptyDocument:
            return "Belgede sayfa bulunamadı."
        case .emptyRange:
            return "Sayfa aralığı girin. Örnek: 1-3, 7, 10-12"
        case .invalidToken(let token):
            return "\"\(token)\" geçerli bir sayfa aralığı değil. Örnek: 1-3, 7, 10-12"
        case .pageOutOfBounds(let page, let pageCount):
            return "\(page). sayfa bulunamadı — belge \(pageCount) sayfa."
        case .writeFailed:
            return "Yeni PDF kaydedilemedi."
        }
    }
}

/// UDF/PDF belgelerinden sayfa aralığı çıkarır veya belgeyi tek tek sayfalara böler.
/// UDF girdileri, birleştirmede olduğu gibi önce mevcut dönüştürme motoruyla PDF'e çevrilir.
final class SplitService {

    /// PDF'e çözülmüş kaynak belge. Bir kez hazırlanır, hem sayfa sayısını göstermek
    /// hem de bölme işlemlerini yapmak için tekrar kullanılır (UDF her seferinde yeniden çevrilmesin).
    struct Source {
        let originalURL: URL
        let pdfURL: URL
        let pageCount: Int

        var baseName: String { originalURL.deletingPathExtension().lastPathComponent }
    }

    /// Kaynağı PDF'e çözer ve sayfa sayısını okur.
    static func loadSource(url: URL) throws -> Source {
        let pdfURL: URL
        if UTType.isUDFFile(url) {
            let document = try UDFParser.parse(fileURL: url)
            pdfURL = try PDFConverter.convert(document: document)
        } else {
            pdfURL = url
        }

        guard let pdf = PDFDocument(url: pdfURL) else {
            throw SplitError.cannotOpenFile(url.lastPathComponent)
        }
        guard pdf.pageCount > 0 else { throw SplitError.emptyDocument }

        return Source(originalURL: url, pdfURL: pdfURL, pageCount: pdf.pageCount)
    }

    /// "1-3, 7, 10-12" biçimindeki metni 0 tabanlı sayfa indekslerine çevirir.
    /// Girilen sıra korunur (3, 1 yazılırsa sayfalar o sırayla çıkar), tekrarlar atılır.
    /// Ters yazılan aralıklar (5-2) düzeltilir.
    static func parsePageIndices(_ text: String, pageCount: Int) throws -> [Int] {
        let tokens = text
            .split(whereSeparator: { $0 == "," || $0 == ";" || $0 == "\n" })
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }

        guard !tokens.isEmpty else { throw SplitError.emptyRange }

        var indices: [Int] = []
        var seen = Set<Int>()

        for token in tokens {
            // Kullanıcı "1 - 3", "1–3" (en dash) veya "1-3" yazabilir.
            let normalized = token.replacingOccurrences(of: "–", with: "-")
            let bounds = normalized.split(separator: "-", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }

            let range: ClosedRange<Int>
            switch bounds.count {
            case 1:
                guard let page = Int(bounds[0]), page > 0 else { throw SplitError.invalidToken(token) }
                range = page...page
            case 2:
                guard let first = Int(bounds[0]), let second = Int(bounds[1]),
                      first > 0, second > 0 else { throw SplitError.invalidToken(token) }
                range = min(first, second)...max(first, second)
            default:
                throw SplitError.invalidToken(token)
            }

            for page in range {
                guard page <= pageCount else {
                    throw SplitError.pageOutOfBounds(page: page, pageCount: pageCount)
                }
                if seen.insert(page).inserted {
                    indices.append(page - 1)
                }
            }
        }

        return indices
    }

    /// Seçilen sayfaları tek bir PDF'e çıkarır.
    static func extract(source: Source, ranges: String) throws -> URL {
        let indices = try parsePageIndices(ranges, pageCount: source.pageCount)

        guard let document = PDFDocument(url: source.pdfURL) else {
            throw SplitError.cannotOpenFile(source.originalURL.lastPathComponent)
        }

        let output = PDFDocument()
        for index in indices {
            guard let page = document.page(at: index)?.copy() as? PDFPage else { continue }
            output.insert(page, at: output.pageCount)
        }
        guard output.pageCount > 0 else { throw SplitError.emptyDocument }

        let outputURL = PDFConverter.outputDirectory()
            .appendingPathComponent("Sayfalar_\(source.baseName)")
            .appendingPathExtension("pdf")

        guard output.write(to: outputURL) else { throw SplitError.writeFailed }
        return outputURL
    }

    /// Belgeyi her sayfası ayrı bir PDF olacak şekilde böler.
    static func splitAll(source: Source, progress: ((Int, Int) -> Void)? = nil) throws -> [URL] {
        guard let document = PDFDocument(url: source.pdfURL) else {
            throw SplitError.cannotOpenFile(source.originalURL.lastPathComponent)
        }

        let width = String(source.pageCount).count
        var outputs: [URL] = []

        for index in 0..<document.pageCount {
            progress?(index, document.pageCount)

            guard let page = document.page(at: index)?.copy() as? PDFPage else { continue }
            let single = PDFDocument()
            single.insert(page, at: 0)

            let number = String(format: "%0\(width)d", index + 1)
            let outputURL = PDFConverter.outputDirectory()
                .appendingPathComponent("\(source.baseName)_Sayfa_\(number)")
                .appendingPathExtension("pdf")

            guard single.write(to: outputURL) else { throw SplitError.writeFailed }
            outputs.append(outputURL)
        }

        guard !outputs.isEmpty else { throw SplitError.emptyDocument }
        progress?(document.pageCount, document.pageCount)
        return outputs
    }
}
