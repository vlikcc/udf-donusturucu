import Foundation
import UIKit
import Compression

/// DOCX dosyasından metin ve detaylı biçimlendirme çıkarır
final class DOCXExtractor {

    struct ExtractedContent {
        let plainText: String
        let paragraphs: [ExtractedParagraph]
    }

    struct ExtractedParagraph {
        let runs: [TextRun]
        let alignment: Int

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
        let data = try Data(contentsOf: url)
        let entries = try ZIPExtractor.extractEntries(from: data)

        guard let docEntry = entries.first(where: {
            $0.fileName.lowercased().contains("word/document.xml")
        }) else {
            throw ExtractionError.invalidFormat
        }

        guard let xmlString = String(data: docEntry.data, encoding: .utf8) else {
            throw ExtractionError.noTextContent
        }

        // Parse default styles from styles.xml if available
        var defaultFontFamily = "Times New Roman"
        var defaultFontSize: CGFloat = 12
        if let stylesEntry = entries.first(where: { $0.fileName.lowercased().contains("word/styles.xml") }),
           let stylesXML = String(data: stylesEntry.data, encoding: .utf8) {
            let (family, size) = parseDefaultStyles(xml: stylesXML)
            if let f = family { defaultFontFamily = f }
            if let s = size { defaultFontSize = s }
        }

        let paragraphs = parseDOCXParagraphs(xml: xmlString, defaultFamily: defaultFontFamily, defaultSize: defaultFontSize)
        let plainText = paragraphs.map(\.text).joined(separator: "\n")

        guard !plainText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw ExtractionError.noTextContent
        }

        return ExtractedContent(plainText: plainText, paragraphs: paragraphs)
    }

    // MARK: - Default Styles

    private static func parseDefaultStyles(xml: String) -> (String?, CGFloat?) {
        // Look for <w:rPrDefault><w:rPr>...<w:rFonts w:ascii="..."/>...<w:sz w:val="..."/>
        var family: String?
        var size: CGFloat?

        if let rPrDefaultRange = xml.range(of: "<w:rPrDefault>") {
            let searchEnd = xml.range(of: "</w:rPrDefault>", range: rPrDefaultRange.upperBound..<xml.endIndex)?.upperBound ?? xml.endIndex
            let section = String(xml[rPrDefaultRange.lowerBound..<searchEnd])

            if let fontStart = section.range(of: "w:ascii=\""),
               let fontEnd = section.range(of: "\"", range: fontStart.upperBound..<section.endIndex) {
                family = String(section[fontStart.upperBound..<fontEnd.lowerBound])
            }
            if let szStart = section.range(of: "<w:sz w:val=\""),
               let szEnd = section.range(of: "\"", range: szStart.upperBound..<section.endIndex) {
                if let halfPts = Double(section[szStart.upperBound..<szEnd.lowerBound]) {
                    size = CGFloat(halfPts / 2.0)
                }
            }
        }
        return (family, size)
    }

    // MARK: - Paragraph Parsing

    private static func parseDOCXParagraphs(xml: String, defaultFamily: String, defaultSize: CGFloat) -> [ExtractedParagraph] {
        var paragraphs: [ExtractedParagraph] = []
        var searchStart = xml.startIndex

        while let pStart = xml.range(of: "<w:p", range: searchStart..<xml.endIndex) {
            guard let pEnd = xml.range(of: "</w:p>", range: pStart.upperBound..<xml.endIndex) else { break }

            let paraXML = String(xml[pStart.lowerBound..<pEnd.upperBound])

            let alignment = extractAlignment(from: paraXML)
            let runs = extractRuns(from: paraXML, defaultFamily: defaultFamily, defaultSize: defaultSize)

            paragraphs.append(ExtractedParagraph(runs: runs, alignment: alignment))
            searchStart = pEnd.upperBound
        }

        return paragraphs
    }

    private static func extractRuns(from paraXML: String, defaultFamily: String, defaultSize: CGFloat) -> [TextRun] {
        var runs: [TextRun] = []
        var runSearch = paraXML.startIndex

        while let rStart = paraXML.range(of: "<w:r>", range: runSearch..<paraXML.endIndex) ??
                           paraXML.range(of: "<w:r ", range: runSearch..<paraXML.endIndex) {
            guard let rEnd = paraXML.range(of: "</w:r>", range: rStart.upperBound..<paraXML.endIndex) else { break }

            let runXML = String(paraXML[rStart.lowerBound..<rEnd.upperBound])

            // Formatting
            let isBold = runXML.contains("<w:b/>") || runXML.contains("<w:b ") ||
                         (runXML.contains("<w:b>") && !runXML.contains("<w:b w:val=\"false\"") && !runXML.contains("<w:b w:val=\"0\""))
            let isItalic = runXML.contains("<w:i/>") || runXML.contains("<w:i ") ||
                          (runXML.contains("<w:i>") && !runXML.contains("<w:i w:val=\"false\"") && !runXML.contains("<w:i w:val=\"0\""))
            let isUnderline = runXML.contains("<w:u ") && !runXML.contains("w:val=\"none\"")

            var fontSize = defaultSize
            if let szRange = runXML.range(of: "<w:sz w:val=\""),
               let szEnd = runXML.range(of: "\"", range: szRange.upperBound..<runXML.endIndex) {
                if let halfPts = Double(runXML[szRange.upperBound..<szEnd.lowerBound]) {
                    fontSize = CGFloat(halfPts / 2.0)
                }
            }

            var fontFamily = defaultFamily
            if let fontStart = runXML.range(of: "w:ascii=\""),
               let fontEnd = runXML.range(of: "\"", range: fontStart.upperBound..<runXML.endIndex) {
                fontFamily = String(runXML[fontStart.upperBound..<fontEnd.lowerBound])
            }

            // Text content
            var runText = ""
            var textSearch = runXML.startIndex
            while let tStart = runXML.range(of: "<w:t", range: textSearch..<runXML.endIndex) {
                guard let tagClose = runXML.range(of: ">", range: tStart.upperBound..<runXML.endIndex) else { break }
                guard let tEnd = runXML.range(of: "</w:t>", range: tagClose.upperBound..<runXML.endIndex) else { break }
                runText += String(runXML[tagClose.upperBound..<tEnd.lowerBound])
                textSearch = tEnd.upperBound
            }

            if runXML.contains("<w:tab/>") {
                runText += "\t"
            }

            if !runText.isEmpty {
                runs.append(TextRun(
                    text: runText, isBold: isBold, isItalic: isItalic,
                    isUnderline: isUnderline, fontSize: fontSize, fontFamily: fontFamily
                ))
            }

            runSearch = rEnd.upperBound
        }

        // If no runs found, return empty run
        if runs.isEmpty {
            runs.append(TextRun(text: "", isBold: false, isItalic: false,
                                isUnderline: false, fontSize: defaultSize, fontFamily: defaultFamily))
        }

        return runs
    }

    private static func extractAlignment(from paraXML: String) -> Int {
        guard let jcStart = paraXML.range(of: "<w:jc w:val=\"") else { return 0 }
        guard let jcEnd = paraXML.range(of: "\"", range: jcStart.upperBound..<paraXML.endIndex) else { return 0 }
        let val = String(paraXML[jcStart.upperBound..<jcEnd.lowerBound])
        switch val {
        case "center": return 1
        case "right": return 2
        case "both": return 3
        default: return 0
        }
    }
}
