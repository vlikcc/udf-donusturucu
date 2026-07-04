import Foundation
import PDFKit
import UIKit

/// PDF dosyasından metin ve detaylı biçimlendirme çıkarır
final class PDFExtractor {

    struct ExtractedContent {
        let plainText: String
        let paragraphs: [ExtractedParagraph]
    }

    struct ExtractedParagraph {
        let runs: [TextRun]
        let alignment: Int // 0=left, 1=center, 2=right, 3=justify

        var text: String { runs.map(\.text).joined() }
        var isBold: Bool { runs.first?.isBold ?? false }
        var fontSize: CGFloat { runs.first?.fontSize ?? 12 }
    }

    struct TextRun {
        let text: String
        let isBold: Bool
        let isItalic: Bool
        let isUnderline: Bool
        let fontSize: CGFloat
        let fontFamily: String
    }

    static func extract(from url: URL) throws -> ExtractedContent {
        guard let document = PDFDocument(url: url) else {
            throw ExtractionError.cannotOpenFile
        }

        var allParagraphs: [ExtractedParagraph] = []
        var fullText = ""

        for pageIndex in 0..<document.pageCount {
            guard let page = document.page(at: pageIndex) else { continue }

            if let attrString = page.attributedString {
                let paragraphs = extractParagraphs(from: attrString)
                allParagraphs.append(contentsOf: paragraphs)
            } else if let pageText = page.string {
                let lines = pageText.components(separatedBy: "\n")
                for line in lines {
                    allParagraphs.append(ExtractedParagraph(
                        runs: [TextRun(text: line, isBold: false, isItalic: false,
                                       isUnderline: false, fontSize: 12, fontFamily: "Times New Roman")],
                        alignment: 0
                    ))
                }
            }

            if let text = page.string {
                if !fullText.isEmpty { fullText += "\n" }
                fullText += text
            }
        }

        return ExtractedContent(plainText: fullText, paragraphs: allParagraphs)
    }

    // MARK: - Paragraph + Run extraction

    private static func extractParagraphs(from attrString: NSAttributedString) -> [ExtractedParagraph] {
        let fullString = attrString.string
        let nsString = fullString as NSString

        // Split by newline to get paragraphs
        var paragraphs: [ExtractedParagraph] = []
        var searchRange = NSRange(location: 0, length: nsString.length)

        while searchRange.location < nsString.length {
            // Find next newline
            let newlineRange = nsString.range(of: "\n", range: searchRange)
            let paraEnd: Int
            if newlineRange.location == NSNotFound {
                paraEnd = nsString.length
            } else {
                paraEnd = newlineRange.location
            }

            let paraRange = NSRange(location: searchRange.location, length: paraEnd - searchRange.location)

            if paraRange.length == 0 {
                // Empty paragraph
                paragraphs.append(ExtractedParagraph(
                    runs: [TextRun(text: "", isBold: false, isItalic: false,
                                   isUnderline: false, fontSize: 12, fontFamily: "Times New Roman")],
                    alignment: 0
                ))
            } else {
                // Extract runs with formatting from this paragraph range
                let (runs, alignment) = extractRuns(from: attrString, in: paraRange)
                paragraphs.append(ExtractedParagraph(runs: runs, alignment: alignment))
            }

            // Move past the newline
            if newlineRange.location == NSNotFound {
                break
            }
            searchRange.location = newlineRange.location + 1
            searchRange.length = nsString.length - searchRange.location
        }

        return paragraphs
    }

    private static func extractRuns(from attrString: NSAttributedString, in paraRange: NSRange) -> ([TextRun], Int) {
        var runs: [TextRun] = []
        var alignment = 0
        var alignmentDetected = false

        attrString.enumerateAttributes(in: paraRange, options: []) { attrs, range, _ in
            let text = (attrString.string as NSString).substring(with: range)

            var isBold = false
            var isItalic = false
            var fontSize: CGFloat = 12
            var fontFamily = "Times New Roman"

            if let font = attrs[.font] as? UIFont {
                fontSize = font.pointSize
                let traits = font.fontDescriptor.symbolicTraits
                isBold = traits.contains(.traitBold)
                isItalic = traits.contains(.traitItalic)
                fontFamily = font.familyName
            }

            let isUnderline = (attrs[.underlineStyle] as? Int ?? 0) != 0

            if !alignmentDetected, let paraStyle = attrs[.paragraphStyle] as? NSParagraphStyle {
                switch paraStyle.alignment {
                case .center: alignment = 1
                case .right: alignment = 2
                case .justified: alignment = 3
                default: alignment = 0
                }
                alignmentDetected = true
            }

            runs.append(TextRun(
                text: text, isBold: isBold, isItalic: isItalic,
                isUnderline: isUnderline, fontSize: fontSize, fontFamily: fontFamily
            ))
        }

        // Merge consecutive runs with identical formatting to reduce clutter
        let merged = mergeRuns(runs)
        return (merged.isEmpty ? [TextRun(text: "", isBold: false, isItalic: false,
                                          isUnderline: false, fontSize: 12, fontFamily: "Times New Roman")] : merged,
                alignment)
    }

    /// Merge adjacent runs that share the same formatting attributes
    private static func mergeRuns(_ runs: [TextRun]) -> [TextRun] {
        guard !runs.isEmpty else { return runs }
        var result: [TextRun] = []
        var current = runs[0]

        for i in 1..<runs.count {
            let next = runs[i]
            if current.isBold == next.isBold &&
               current.isItalic == next.isItalic &&
               current.isUnderline == next.isUnderline &&
               current.fontSize == next.fontSize &&
               current.fontFamily == next.fontFamily {
                // Same formatting — merge text
                current = TextRun(
                    text: current.text + next.text,
                    isBold: current.isBold, isItalic: current.isItalic,
                    isUnderline: current.isUnderline, fontSize: current.fontSize,
                    fontFamily: current.fontFamily
                )
            } else {
                result.append(current)
                current = next
            }
        }
        result.append(current)
        return result
    }
}

enum ExtractionError: LocalizedError {
    case cannotOpenFile
    case noTextContent
    case invalidFormat

    var errorDescription: String? {
        switch self {
        case .cannotOpenFile: return "Dosya açılamadı."
        case .noTextContent: return "Dosyada metin içeriği bulunamadı."
        case .invalidFormat: return "Geçersiz dosya formatı."
        }
    }
}
