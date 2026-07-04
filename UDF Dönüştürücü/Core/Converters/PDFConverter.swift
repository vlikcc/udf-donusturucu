import Foundation
import PDFKit
import UIKit

final class PDFConverter {

    private static let pageSize = CGSize(width: 595.28, height: 841.89) // A4

    static func convert(document: UDFDocument) throws -> URL {
        let format = document.pageFormat ?? UDFPageFormat.defaultFormat
        let attributedContent = buildAttributedString(from: document)
        let pdfData = renderPDFWithTextKit(attributedString: attributedContent, pageFormat: format)

        let outputDir = outputDirectory()
        let outputURL = outputDir
            .appendingPathComponent(document.fileName.replacingOccurrences(of: ".udf", with: ""))
            .appendingPathExtension("pdf")
        try pdfData.write(to: outputURL, options: .atomic)
        return outputURL
    }

    // MARK: - Build Attributed String

    private static func buildAttributedString(from document: UDFDocument) -> NSAttributedString {
        let content = document.content

        // UYAP format: use pre-built formatted string
        if content.contentType == .uyap, let formatted = content.formattedString, formatted.length > 0 {
            return formatted
        }

        switch content.contentType {
        case .html:
            if let attrStr = attributedStringFromHTML(content.rawContent) {
                return attrStr
            }
        case .rtf:
            if let attrStr = attributedStringFromRTF(content.rawContent) {
                return attrStr
            }
        default:
            break
        }

        return attributedStringFromPlainSections(document)
    }

    private static func attributedStringFromHTML(_ html: String) -> NSAttributedString? {
        var fullHTML = html
        if !html.lowercased().contains("<html") {
            fullHTML = "<html><head><meta charset=\"UTF-8\"><style>body{font-family:'Times New Roman',serif;font-size:12pt;line-height:1.4;}table{border-collapse:collapse;width:100%;margin:8pt 0;}td,th{border:1px solid #999;padding:4pt 6pt;}</style></head><body>\(html)</body></html>"
        }
        guard let data = fullHTML.data(using: .utf8) else { return nil }
        let opts: [NSAttributedString.DocumentReadingOptionKey: Any] = [
            .documentType: NSAttributedString.DocumentType.html,
            .characterEncoding: String.Encoding.utf8.rawValue
        ]
        var result: NSAttributedString?
        if Thread.isMainThread {
            result = try? NSAttributedString(data: data, options: opts, documentAttributes: nil)
        } else {
            DispatchQueue.main.sync {
                result = try? NSAttributedString(data: data, options: opts, documentAttributes: nil)
            }
        }
        return result
    }

    private static func attributedStringFromRTF(_ rtf: String) -> NSAttributedString? {
        for encoding in [String.Encoding.utf8, .windowsCP1254, .isoLatin1] {
            guard let data = rtf.data(using: encoding) else { continue }
            let opts: [NSAttributedString.DocumentReadingOptionKey: Any] = [
                .documentType: NSAttributedString.DocumentType.rtf,
                .characterEncoding: encoding.rawValue
            ]
            if let attrStr = try? NSAttributedString(data: data, options: opts, documentAttributes: nil) {
                return attrStr
            }
        }
        return nil
    }

    private static func attributedStringFromPlainSections(_ document: UDFDocument) -> NSAttributedString {
        let result = NSMutableAttributedString()
        let bodyFont = UIFont(name: "Times New Roman", size: 12) ?? UIFont.systemFont(ofSize: 12)
        let headingFont = UIFont(name: "Times New Roman", size: 14)?.withTraits(.traitBold) ?? UIFont.boldSystemFont(ofSize: 14)
        let metaFont = UIFont.italicSystemFont(ofSize: 10)

        let bodyStyle = NSMutableParagraphStyle()
        bodyStyle.lineSpacing = 4
        bodyStyle.paragraphSpacing = 6

        let headingStyle = NSMutableParagraphStyle()
        headingStyle.paragraphSpacingBefore = 14
        headingStyle.paragraphSpacing = 6

        if let meta = document.metadata {
            var parts: [String] = []
            if let t = meta.title { parts.append("Başlık: \(t)") }
            if let a = meta.author { parts.append("Yazar: \(a)") }
            if let d = meta.creationDate { parts.append("Tarih: \(d)") }
            if !parts.isEmpty {
                result.append(NSAttributedString(string: parts.joined(separator: " | ") + "\n\n", attributes: [
                    .font: metaFont, .foregroundColor: UIColor.darkGray, .paragraphStyle: bodyStyle
                ]))
            }
        }

        for section in document.content.sections {
            if let title = section.title {
                result.append(NSAttributedString(string: title + "\n", attributes: [
                    .font: headingFont, .foregroundColor: UIColor.black, .paragraphStyle: headingStyle
                ]))
            }
            result.append(NSAttributedString(string: section.body + "\n\n", attributes: [
                .font: bodyFont, .foregroundColor: UIColor.black, .paragraphStyle: bodyStyle
            ]))
        }
        return result
    }

    // MARK: - TextKit PDF Rendering

    private static func renderPDFWithTextKit(attributedString: NSAttributedString, pageFormat: UDFPageFormat) -> Data {
        let leftMargin = pageFormat.leftMargin
        let rightMargin = pageFormat.rightMargin
        let topMargin = pageFormat.topMargin
        let bottomMargin = pageFormat.bottomMargin

        let drawableWidth = pageSize.width - leftMargin - rightMargin
        let drawableHeight = pageSize.height - topMargin - bottomMargin

        // TextKit stack
        let textStorage = NSTextStorage(attributedString: attributedString)
        let layoutManager = NSLayoutManager()
        textStorage.addLayoutManager(layoutManager)

        let containerSize = CGSize(width: drawableWidth, height: drawableHeight)
        var textContainers: [NSTextContainer] = []
        var done = false
        let maxPages = 500

        while !done && textContainers.count < maxPages {
            let tc = NSTextContainer(size: containerSize)
            tc.lineFragmentPadding = 0
            layoutManager.addTextContainer(tc)
            textContainers.append(tc)

            let glyphRange = layoutManager.glyphRange(for: tc)
            if NSMaxRange(glyphRange) >= layoutManager.numberOfGlyphs {
                done = true
            }
        }

        // Render
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))

        let data = renderer.pdfData { pdfContext in
            for (pageIndex, tc) in textContainers.enumerated() {
                pdfContext.beginPage()

                let glyphRange = layoutManager.glyphRange(for: tc)
                guard glyphRange.length > 0 else { continue }

                let textOrigin = CGPoint(x: leftMargin, y: topMargin)
                layoutManager.drawBackground(forGlyphRange: glyphRange, at: textOrigin)
                layoutManager.drawGlyphs(forGlyphRange: glyphRange, at: textOrigin)

                // Page number
                let pageNum = "\(pageIndex + 1)" as NSString
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 9),
                    .foregroundColor: UIColor.gray
                ]
                let numSize = pageNum.size(withAttributes: attrs)
                pageNum.draw(
                    at: CGPoint(x: (pageSize.width - numSize.width) / 2,
                                y: pageSize.height - bottomMargin + 10),
                    withAttributes: attrs
                )
            }
        }

        return data
    }

    // MARK: - Output Directory

    static func outputDirectory() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let outputDir = docs.appendingPathComponent("ConvertedFiles")
        if !FileManager.default.fileExists(atPath: outputDir.path) {
            try? FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
        }
        return outputDir
    }
}

// MARK: - UIFont Helper

private extension UIFont {
    func withTraits(_ traits: UIFontDescriptor.SymbolicTraits) -> UIFont {
        guard let descriptor = fontDescriptor.withSymbolicTraits(traits) else { return self }
        return UIFont(descriptor: descriptor, size: pointSize)
    }
}

enum ConversionError: LocalizedError {
    case pdfCreationFailed
    case docxCreationFailed
    case exportFailed(String)

    var errorDescription: String? {
        switch self {
        case .pdfCreationFailed: return "PDF oluşturulamadı."
        case .docxCreationFailed: return "DOCX oluşturulamadı."
        case .exportFailed(let detail): return "Dosya kaydetme hatası: \(detail)"
        }
    }
}
