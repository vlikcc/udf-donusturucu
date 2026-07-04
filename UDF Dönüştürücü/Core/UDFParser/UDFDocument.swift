import Foundation
import UIKit

struct UDFDocument {
    let fileName: String
    let content: UDFContent
    let metadata: UDFMetadata?
    let pageFormat: UDFPageFormat?
}

struct UDFPageFormat {
    let leftMargin: CGFloat
    let rightMargin: CGFloat
    let topMargin: CGFloat
    let bottomMargin: CGFloat

    static let defaultFormat = UDFPageFormat(
        leftMargin: 56.69,
        rightMargin: 56.69,
        topMargin: 28.35,
        bottomMargin: 28.35
    )
}

enum UDFContentType {
    case uyap       // UYAP custom format with CDATA + <elements>
    case html
    case rtf
    case plainText
}

struct UDFContent {
    let text: String              // Plain text from CDATA
    let rawContent: String        // Original XML/HTML/RTF
    let contentType: UDFContentType
    let sections: [UDFSection]
    let tables: [UDFTable]
    let isRTF: Bool
    let formattedString: NSAttributedString?  // Pre-built formatted string for UYAP
}

// MARK: - UYAP Paragraph Model

struct UYAPParagraph {
    let alignment: Int          // 0=left, 1=center, 3=justify
    let spaceAbove: CGFloat
    let spaceBelow: CGFloat
    let leftIndent: CGFloat
    let rightIndent: CGFloat
    let firstLineIndent: CGFloat
    let hangingIndent: CGFloat
    let lineSpacing: CGFloat
    let tabStops: [CGFloat]
    let runs: [UYAPTextRun]
}

struct UYAPTextRun {
    let startOffset: Int
    let length: Int
    let bold: Bool
    let underline: Bool
    let italic: Bool
    let fontSize: CGFloat?
    let fontFamily: String?
}

// MARK: - Section / Table / Metadata

struct UDFSection {
    let title: String?
    let body: String
    let level: Int
}

struct UDFTable {
    let rows: [[String]]
}

struct UDFMetadata {
    let author: String?
    let creationDate: String?
    let title: String?
    let subject: String?
}

enum UDFParserError: LocalizedError {
    case fileNotFound
    case invalidZipArchive
    case noContentFound
    case parsingFailed(String)
    case unsupportedFormat

    var errorDescription: String? {
        switch self {
        case .fileNotFound:
            return "UDF dosyası bulunamadı."
        case .invalidZipArchive:
            return "Geçersiz UDF arşiv formatı."
        case .noContentFound:
            return "UDF dosyasında içerik bulunamadı."
        case .parsingFailed(let detail):
            return "UDF parse hatası: \(detail)"
        case .unsupportedFormat:
            return "Desteklenmeyen UDF formatı."
        }
    }
}
