import Foundation
import UIKit

final class UDFParser {

    static func parse(fileURL: URL) throws -> UDFDocument {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            throw UDFParserError.fileNotFound
        }

        let data = try Data(contentsOf: fileURL)
        let entries = try ZIPExtractor.extractEntries(from: data)

        var contentEntry: ZIPExtractor.ZIPEntry?
        var metadataEntry: ZIPExtractor.ZIPEntry?

        for entry in entries {
            let name = entry.fileName.lowercased()
            if name.contains("content") || name.hasSuffix(".xml") || name.hasSuffix(".rtf") || name.hasSuffix(".html") || name.hasSuffix(".htm") {
                if contentEntry == nil { contentEntry = entry }
            }
            if name.contains("meta") || name.contains("info") || name.contains("propert") {
                metadataEntry = entry
            }
        }

        if contentEntry == nil {
            contentEntry = entries
                .filter { e in
                    let n = e.fileName.lowercased()
                    return !n.contains("imza") && !n.contains("sign") && !n.contains(".p7s") && !n.contains(".sig")
                }
                .max(by: { $0.data.count < $1.data.count })
        }

        guard let content = contentEntry else {
            throw UDFParserError.noContentFound
        }

        let udfContent = try parseContent(entry: content)
        let udfMetadata = metadataEntry.flatMap { try? parseMetadata(entry: $0) }
        let pageFormat = parsePageFormat(from: content)

        return UDFDocument(
            fileName: fileURL.lastPathComponent,
            content: udfContent,
            metadata: udfMetadata,
            pageFormat: pageFormat
        )
    }

    // MARK: - Content Detection

    private static func parseContent(entry: ZIPExtractor.ZIPEntry) throws -> UDFContent {
        guard let text = String(data: entry.data, encoding: .utf8)
                ?? String(data: entry.data, encoding: .windowsCP1254)
                ?? String(data: entry.data, encoding: .isoLatin1) else {
            throw UDFParserError.parsingFailed("İçerik kodlaması tanınmadı.")
        }

        // Check if it's UYAP format: has <template> with CDATA and <elements>
        if text.contains("<template") && text.contains("<![CDATA[") && text.contains("<elements") {
            return parseUYAPContent(text)
        }

        let isRTF = text.hasPrefix("{\\rtf")
        let isHTML = text.lowercased().contains("<html") || text.lowercased().contains("<body")

        if isRTF {
            return UDFContent(text: stripRTFFormatting(text), rawContent: text,
                              contentType: .rtf, sections: [], tables: [], isRTF: true, formattedString: nil)
        } else if isHTML {
            let plain = stripHTMLTags(text)
            return UDFContent(text: plain, rawContent: text,
                              contentType: .html, sections: extractSections(from: plain),
                              tables: extractHTMLTables(from: text), isRTF: false, formattedString: nil)
        } else {
            let sections = extractSections(from: text)
            return UDFContent(text: text, rawContent: text,
                              contentType: .plainText, sections: sections, tables: [], isRTF: false, formattedString: nil)
        }
    }

    // MARK: - UYAP Format Parsing

    private static func parseUYAPContent(_ xml: String) -> UDFContent {
        // 1. Extract plain text from CDATA
        let plainText = extractCDATA(from: xml) ?? ""

        // 2. Parse <elements> for paragraph formatting
        let paragraphs = parseUYAPElements(xml: xml)

        // 3. Build NSAttributedString with formatting
        let attributedString = buildAttributedString(plainText: plainText, paragraphs: paragraphs)

        return UDFContent(
            text: plainText,
            rawContent: xml,
            contentType: .uyap,
            sections: [],
            tables: [],
            isRTF: false,
            formattedString: attributedString
        )
    }

    private static func parseUYAPElements(xml: String) -> [UYAPParagraph] {
        guard let elementsStart = xml.range(of: "<elements") else { return [] }
        let elementsXML = String(xml[elementsStart.lowerBound...])

        var paragraphs: [UYAPParagraph] = []

        // Find each <paragraph ...>...</paragraph>
        var searchStart = elementsXML.startIndex
        while let pStart = elementsXML.range(of: "<paragraph ", range: searchStart..<elementsXML.endIndex) {
            guard let pEnd = elementsXML.range(of: "</paragraph>", range: pStart.upperBound..<elementsXML.endIndex) else { break }

            let paragraphXML = String(elementsXML[pStart.lowerBound..<pEnd.upperBound])

            // Parse paragraph attributes
            let alignment = extractIntAttr(paragraphXML, name: "Alignment") ?? 0
            let spaceAbove = extractFloatAttr(paragraphXML, name: "SpaceAbove") ?? 1.0
            let spaceBelow = extractFloatAttr(paragraphXML, name: "SpaceBelow") ?? 1.0
            let leftIndent = extractFloatAttr(paragraphXML, name: "LeftIndent") ?? 0
            let rightIndent = extractFloatAttr(paragraphXML, name: "RightIndent") ?? 0
            let firstLineIndent = extractFloatAttr(paragraphXML, name: "FirstLineIndent") ?? 0
            let hanging = extractFloatAttr(paragraphXML, name: "Hanging") ?? 0
            let lineSpacing = extractFloatAttr(paragraphXML, name: "LineSpacing") ?? 0

            // Parse tab stops: "130.0:0:0" or "87.0:0:0"
            let tabStops = parseTabStops(paragraphXML)

            // Parse text runs (content and field elements inside the paragraph)
            let runs = parseTextRuns(paragraphXML)

            paragraphs.append(UYAPParagraph(
                alignment: alignment,
                spaceAbove: spaceAbove,
                spaceBelow: spaceBelow,
                leftIndent: leftIndent,
                rightIndent: rightIndent,
                firstLineIndent: firstLineIndent,
                hangingIndent: hanging,
                lineSpacing: lineSpacing,
                tabStops: tabStops,
                runs: runs
            ))

            searchStart = pEnd.upperBound
        }

        return paragraphs
    }

    private static func parseTextRuns(_ paragraphXML: String) -> [UYAPTextRun] {
        var runs: [UYAPTextRun] = []

        // Match <content .../>, <field .../>, <space .../> — self-closing tags with startOffset and length
        let tagPatterns: [(String, Bool, Bool)] = [
            ("<content ", false, false),
            ("<field ", true, false),
            ("<space ", false, true)
        ]

        for (tagPattern, isField, isSpace) in tagPatterns {
            var search = paragraphXML.startIndex
            while let tagStart = paragraphXML.range(of: tagPattern, range: search..<paragraphXML.endIndex) {
                // Find the end of this tag (either /> or >)
                guard let tagEnd = paragraphXML.range(of: "/>", range: tagStart.upperBound..<paragraphXML.endIndex) else {
                    search = tagStart.upperBound
                    continue
                }

                let tagContent = String(paragraphXML[tagStart.lowerBound..<tagEnd.upperBound])

                guard let startOffset = extractIntAttr(tagContent, name: "startOffset"),
                      let length = extractIntAttr(tagContent, name: "length") else {
                    search = tagEnd.upperBound
                    continue
                }

                let bold = tagContent.contains("bold=\"true\"")
                let underline = tagContent.contains("underline=\"true\"")
                let italic = tagContent.contains("italic=\"true\"")
                let fontSize = extractFloatAttr(tagContent, name: "size")
                let fontFamily = extractStringAttr(tagContent, name: "family")
                let foreground = parseColorAttr(tagContent, name: "foreground")
                    ?? parseColorAttr(tagContent, name: "Foreground")
                let background = parseColorAttr(tagContent, name: "background")
                    ?? parseColorAttr(tagContent, name: "Background")
                let fieldName = extractStringAttr(tagContent, name: "fieldName")
                    ?? extractStringAttr(tagContent, name: "name")
                    ?? extractStringAttr(tagContent, name: "FieldName")

                runs.append(UYAPTextRun(
                    startOffset: startOffset,
                    length: length,
                    bold: bold,
                    underline: underline,
                    italic: italic,
                    fontSize: fontSize,
                    fontFamily: fontFamily,
                    foregroundARGB: foreground,
                    backgroundARGB: background,
                    isField: isField,
                    fieldName: fieldName,
                    isSpace: isSpace
                ))

                search = tagEnd.upperBound
            }
        }

        runs.sort { $0.startOffset < $1.startOffset }
        return runs
    }

    private static func parseTabStops(_ xml: String) -> [CGFloat] {
        guard let tabStr = extractStringAttr(xml, name: "TabSet") else { return [] }
        // Format: "130.0:0:0" — first number is position
        let parts = tabStr.components(separatedBy: ":")
        if let pos = parts.first.flatMap({ CGFloat(Double($0) ?? 0) }), pos > 0 {
            return [pos]
        }
        return []
    }

    // MARK: - Build NSAttributedString from UYAP

    private static func buildAttributedString(plainText: String, paragraphs: [UYAPParagraph]) -> NSAttributedString {
        let result = NSMutableAttributedString()

        // Default font
        let defaultFontSize: CGFloat = 12
        let defaultFontFamily = "Times New Roman"

        // Split plain text into characters for offset mapping
        let textChars = Array(plainText)
        let totalLength = textChars.count

        if paragraphs.isEmpty {
            // No formatting info — just use plain text
            let font = UIFont(name: defaultFontFamily, size: defaultFontSize) ?? UIFont.systemFont(ofSize: defaultFontSize)
            return NSAttributedString(string: plainText, attributes: [.font: font])
        }

        // Process each paragraph
        for (pIdx, paragraph) in paragraphs.enumerated() {
            // Determine paragraph text range from its runs
            guard let firstRun = paragraph.runs.first else { continue }
            let paraStart = firstRun.startOffset
            let lastRun = paragraph.runs.last!
            let paraEnd = min(lastRun.startOffset + lastRun.length, totalLength)

            guard paraStart < totalLength && paraStart < paraEnd else { continue }

            let safeEnd = min(paraEnd, totalLength)
            let paraText = String(textChars[paraStart..<safeEnd])

            // Build paragraph style
            let paraStyle = NSMutableParagraphStyle()

            switch paragraph.alignment {
            case 1: paraStyle.alignment = .center
            case 2: paraStyle.alignment = .right
            case 3: paraStyle.alignment = .justified
            default: paraStyle.alignment = .left
            }

            paraStyle.paragraphSpacingBefore = paragraph.spaceAbove
            paraStyle.paragraphSpacing = paragraph.spaceBelow
            paraStyle.headIndent = paragraph.leftIndent
            paraStyle.tailIndent = paragraph.rightIndent > 0 ? -paragraph.rightIndent : 0
            paraStyle.firstLineHeadIndent = paragraph.firstLineIndent

            if paragraph.hangingIndent > 0 {
                paraStyle.headIndent = paragraph.hangingIndent
                paraStyle.firstLineHeadIndent = 0
            }

            // Tab stops
            if !paragraph.tabStops.isEmpty {
                paraStyle.tabStops = paragraph.tabStops.map {
                    NSTextTab(textAlignment: .left, location: $0)
                }
            }

            if paragraph.lineSpacing > 0 {
                paraStyle.lineSpacing = paragraph.lineSpacing
            }

            // Create attributed string for paragraph text with default attributes
            let defaultFont = UIFont(name: defaultFontFamily, size: defaultFontSize) ?? UIFont.systemFont(ofSize: defaultFontSize)
            let paraAttrStr = NSMutableAttributedString(string: paraText, attributes: [
                .font: defaultFont,
                .foregroundColor: UIColor.black,
                .paragraphStyle: paraStyle
            ])

            // Apply runs formatting
            for run in paragraph.runs {
                let runLocalStart = run.startOffset - paraStart
                let runLocalEnd = min(runLocalStart + run.length, paraText.count)
                guard runLocalStart >= 0 && runLocalStart < paraText.count && runLocalEnd > runLocalStart else { continue }

                let range = NSRange(location: runLocalStart, length: runLocalEnd - runLocalStart)

                // Determine font
                let family = run.fontFamily ?? defaultFontFamily
                let size = run.fontSize ?? defaultFontSize
                var font: UIFont

                if run.bold && run.italic {
                    font = UIFont(name: family, size: size) ?? UIFont.systemFont(ofSize: size)
                    if let descriptor = font.fontDescriptor.withSymbolicTraits([.traitBold, .traitItalic]) {
                        font = UIFont(descriptor: descriptor, size: size)
                    }
                } else if run.bold {
                    font = UIFont(name: family, size: size) ?? UIFont.systemFont(ofSize: size)
                    if let descriptor = font.fontDescriptor.withSymbolicTraits(.traitBold) {
                        font = UIFont(descriptor: descriptor, size: size)
                    } else {
                        font = UIFont.boldSystemFont(ofSize: size)
                    }
                } else if run.italic {
                    font = UIFont(name: family, size: size) ?? UIFont.systemFont(ofSize: size)
                    if let descriptor = font.fontDescriptor.withSymbolicTraits(.traitItalic) {
                        font = UIFont(descriptor: descriptor, size: size)
                    } else {
                        font = UIFont.italicSystemFont(ofSize: size)
                    }
                } else {
                    font = UIFont(name: family, size: size) ?? UIFont.systemFont(ofSize: size)
                }

                paraAttrStr.addAttribute(.font, value: font, range: range)

                if run.underline {
                    paraAttrStr.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
                }

                if let fg = run.foregroundARGB {
                    paraAttrStr.addAttribute(.foregroundColor, value: UDFColorCodec.uiColor(fromJavaARGB: fg), range: range)
                }
                if let bg = run.backgroundARGB {
                    paraAttrStr.addAttribute(.backgroundColor, value: UDFColorCodec.uiColor(fromJavaARGB: bg), range: range)
                } else if run.isField {
                    paraAttrStr.addAttribute(.backgroundColor, value: UIColor.systemYellow.withAlphaComponent(0.35), range: range)
                }
                if run.isField, let name = run.fieldName {
                    paraAttrStr.addAttribute(.udfFieldName, value: name, range: range)
                }
            }

            result.append(paraAttrStr)

            // Add newline between paragraphs (except the last one)
            if pIdx < paragraphs.count - 1 {
                // Check if the paragraph text already ends with newline
                if !paraText.hasSuffix("\n") {
                    let nlFont = UIFont(name: defaultFontFamily, size: defaultFontSize) ?? UIFont.systemFont(ofSize: defaultFontSize)
                    result.append(NSAttributedString(string: "\n", attributes: [
                        .font: nlFont,
                        .paragraphStyle: paraStyle
                    ]))
                }
            }
        }

        return result
    }

    // MARK: - Page Format Parsing

    private static func parsePageFormat(from entry: ZIPExtractor.ZIPEntry) -> UDFPageFormat? {
        guard let text = String(data: entry.data, encoding: .utf8) else { return nil }
        guard text.contains("<pageFormat") else { return nil }

        let left = extractFloatAttr(text, name: "leftMargin") ?? 56.69
        let right = extractFloatAttr(text, name: "rightMargin") ?? 56.69
        let top = extractFloatAttr(text, name: "topMargin") ?? 28.35
        let bottom = extractFloatAttr(text, name: "bottomMargin") ?? 28.35

        return UDFPageFormat(leftMargin: left, rightMargin: right, topMargin: top, bottomMargin: bottom)
    }

    // MARK: - XML Attribute Helpers

    private static func extractIntAttr(_ xml: String, name: String) -> Int? {
        guard let range = xml.range(of: "\(name)=\"") else { return nil }
        let start = range.upperBound
        guard let end = xml.range(of: "\"", range: start..<xml.endIndex) else { return nil }
        return Int(xml[start..<end.lowerBound])
    }

    private static func extractFloatAttr(_ xml: String, name: String) -> CGFloat? {
        guard let range = xml.range(of: "\(name)=\"") else { return nil }
        let start = range.upperBound
        guard let end = xml.range(of: "\"", range: start..<xml.endIndex) else { return nil }
        guard let val = Double(xml[start..<end.lowerBound]) else { return nil }
        return CGFloat(val)
    }

    private static func extractStringAttr(_ xml: String, name: String) -> String? {
        guard let range = xml.range(of: "\(name)=\"") else { return nil }
        let start = range.upperBound
        guard let end = xml.range(of: "\"", range: start..<xml.endIndex) else { return nil }
        let val = String(xml[start..<end.lowerBound])
        return val.isEmpty ? nil : val
    }

    private static func parseColorAttr(_ xml: String, name: String) -> Int? {
        UDFColorCodec.parse(extractStringAttr(xml, name: name))
    }

    // MARK: - CDATA Extraction

    private static func extractCDATA(from xml: String) -> String? {
        var result = ""
        var searchStart = xml.startIndex
        while let cdataStart = xml.range(of: "<![CDATA[", range: searchStart..<xml.endIndex) {
            let contentStart = cdataStart.upperBound
            guard let cdataEnd = xml.range(of: "]]>", range: contentStart..<xml.endIndex) else { break }
            if !result.isEmpty { result += "\n" }
            result += String(xml[contentStart..<cdataEnd.lowerBound])
            searchStart = cdataEnd.upperBound
        }
        return result.isEmpty ? nil : result
    }

    // MARK: - RTF Stripping

    private static func stripRTFFormatting(_ rtf: String) -> String {
        var result = rtf
        if let range = result.range(of: "{\\rtf") {
            result = String(result[range.lowerBound...])
        }

        let replacements: [(String, String)] = [
            ("\\par", "\n"), ("\\line", "\n"), ("\\tab", "\t"),
            ("\\pard", ""), ("\\plain", ""),
            ("\\b0", ""), ("\\b", ""), ("\\i0", ""), ("\\i", ""),
            ("\\ul0", ""), ("\\ul", ""),
        ]
        for (p, r) in replacements {
            result = result.replacingOccurrences(of: p, with: r)
        }

        var cleaned = ""
        var i = result.startIndex
        while i < result.endIndex {
            let ch = result[i]
            if ch == "\\" {
                let next = result.index(after: i)
                if next < result.endIndex {
                    let nextCh = result[next]
                    if nextCh == "'" {
                        let hexStart = result.index(next, offsetBy: 1, limitedBy: result.endIndex) ?? result.endIndex
                        let hexEnd = result.index(hexStart, offsetBy: 2, limitedBy: result.endIndex) ?? result.endIndex
                        if hexEnd <= result.endIndex {
                            let hex = String(result[hexStart..<hexEnd])
                            if let code = UInt8(hex, radix: 16) {
                                if let str = String(data: Data([code]), encoding: .windowsCP1254) {
                                    cleaned.append(str)
                                }
                            }
                            i = hexEnd; continue
                        }
                    } else if nextCh.isLetter {
                        var j = next
                        while j < result.endIndex && result[j].isLetter { j = result.index(after: j) }
                        while j < result.endIndex && (result[j].isNumber || result[j] == "-") { j = result.index(after: j) }
                        if j < result.endIndex && result[j] == " " { j = result.index(after: j) }
                        i = j; continue
                    } else {
                        cleaned.append(nextCh)
                        i = result.index(next, offsetBy: 1, limitedBy: result.endIndex) ?? result.endIndex; continue
                    }
                }
            } else if ch == "{" || ch == "}" {
                i = result.index(after: i); continue
            } else {
                cleaned.append(ch)
            }
            i = result.index(after: i)
        }

        let lines = cleaned.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }
        var finalLines: [String] = []
        var lastWasEmpty = false
        for line in lines {
            if line.isEmpty { if !lastWasEmpty { finalLines.append("") }; lastWasEmpty = true }
            else { finalLines.append(line); lastWasEmpty = false }
        }
        return finalLines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - HTML Stripping

    private static func stripHTMLTags(_ html: String) -> String {
        var result = html
        let blockTags = ["</p>", "</div>", "</br>", "<br>", "<br/>", "<br />", "</tr>", "</h1>", "</h2>", "</h3>", "</h4>", "</li>"]
        for tag in blockTags {
            result = result.replacingOccurrences(of: tag, with: "\n", options: .caseInsensitive)
        }
        result = result.replacingOccurrences(of: "</td>", with: "\t", options: .caseInsensitive)

        while let open = result.range(of: "<"),
              let close = result.range(of: ">", range: open.upperBound..<result.endIndex) {
            result.removeSubrange(open.lowerBound...close.lowerBound)
        }

        let entities: [(String, String)] = [
            ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"),
            ("&quot;", "\""), ("&apos;", "'"), ("&#39;", "'"),
            ("&nbsp;", " "), ("&ouml;", "ö"), ("&uuml;", "ü"),
            ("&ccedil;", "ç"), ("&Ouml;", "Ö"), ("&Uuml;", "Ü"),
            ("&Ccedil;", "Ç"), ("&#304;", "İ"), ("&#305;", "ı"),
            ("&#351;", "ş"), ("&#350;", "Ş"), ("&#287;", "ğ"), ("&#286;", "Ğ"),
        ]
        for (e, c) in entities { result = result.replacingOccurrences(of: e, with: c) }

        let lines = result.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }
        var finalLines: [String] = []
        var lastWasEmpty = false
        for line in lines {
            if line.isEmpty { if !lastWasEmpty { finalLines.append("") }; lastWasEmpty = true }
            else { finalLines.append(line); lastWasEmpty = false }
        }
        return finalLines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - HTML Table Extraction

    private static func extractHTMLTables(from html: String) -> [UDFTable] {
        var tables: [UDFTable] = []
        var searchRange = html.startIndex..<html.endIndex

        while let tableStart = html.range(of: "<table", options: .caseInsensitive, range: searchRange),
              let tableEnd = html.range(of: "</table>", options: .caseInsensitive, range: tableStart.upperBound..<html.endIndex) {
            let tableContent = String(html[tableStart.lowerBound..<tableEnd.upperBound])
            var rows: [[String]] = []
            var rowSearch = tableContent.startIndex..<tableContent.endIndex

            while let trStart = tableContent.range(of: "<tr", options: .caseInsensitive, range: rowSearch),
                  let trEnd = tableContent.range(of: "</tr>", options: .caseInsensitive, range: trStart.upperBound..<tableContent.endIndex) {
                let rowContent = String(tableContent[trStart.lowerBound..<trEnd.upperBound])
                var cells: [String] = []
                var cellSearch = rowContent.startIndex..<rowContent.endIndex

                while let tdStart = rowContent.range(of: "<td", options: .caseInsensitive, range: cellSearch) ?? rowContent.range(of: "<th", options: .caseInsensitive, range: cellSearch),
                      let tdEnd = rowContent.range(of: "</td>", options: .caseInsensitive, range: tdStart.upperBound..<rowContent.endIndex) ?? rowContent.range(of: "</th>", options: .caseInsensitive, range: tdStart.upperBound..<rowContent.endIndex) {
                    let cellContent = String(rowContent[tdStart.upperBound..<tdEnd.lowerBound])
                    cells.append(stripHTMLTags(cellContent).trimmingCharacters(in: .whitespacesAndNewlines))
                    cellSearch = tdEnd.upperBound..<rowContent.endIndex
                }
                if !cells.isEmpty { rows.append(cells) }
                rowSearch = trEnd.upperBound..<tableContent.endIndex
            }
            if !rows.isEmpty { tables.append(UDFTable(rows: rows)) }
            searchRange = tableEnd.upperBound..<html.endIndex
        }
        return tables
    }

    // MARK: - Section Extraction (fallback)

    private static func extractSections(from text: String) -> [UDFSection] {
        let lines = text.components(separatedBy: "\n")
        var sections: [UDFSection] = []
        var currentBody = ""
        var currentTitle: String?

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            let isTitle = trimmed.count > 2 && trimmed.count < 100 &&
                (trimmed == trimmed.uppercased() && trimmed.rangeOfCharacter(from: .letters) != nil ||
                 trimmed.hasSuffix(":"))
            if isTitle && !currentBody.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                sections.append(UDFSection(title: currentTitle, body: currentBody.trimmingCharacters(in: .whitespacesAndNewlines), level: 0))
                currentBody = ""
                currentTitle = trimmed
            } else if isTitle && currentBody.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                currentTitle = trimmed
            } else {
                currentBody += line + "\n"
            }
        }
        if !currentBody.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            sections.append(UDFSection(title: currentTitle, body: currentBody.trimmingCharacters(in: .whitespacesAndNewlines), level: 0))
        }
        if sections.isEmpty { sections.append(UDFSection(title: nil, body: text, level: 0)) }
        return sections
    }

    // MARK: - Metadata

    private static func parseMetadata(entry: ZIPExtractor.ZIPEntry) throws -> UDFMetadata {
        guard let text = String(data: entry.data, encoding: .utf8)
                ?? String(data: entry.data, encoding: .windowsCP1254) else {
            return UDFMetadata(author: nil, creationDate: nil, title: nil, subject: nil)
        }

        func extractValue(tag: String) -> String? {
            guard let s = text.range(of: "<\(tag)>"),
                  let e = text.range(of: "</\(tag)>", range: s.upperBound..<text.endIndex) else { return nil }
            return String(text[s.upperBound..<e.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        }

        func extractEntry(key: String) -> String? {
            let p = "key=\"\(key)\">"
            guard let kr = text.range(of: p),
                  let er = text.range(of: "</entry>", range: kr.upperBound..<text.endIndex) else { return nil }
            let v = String(text[kr.upperBound..<er.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
            return v.isEmpty ? nil : v
        }

        return UDFMetadata(
            author: extractValue(tag: "author") ?? extractValue(tag: "Author") ?? extractEntry(key: "uyapsicil"),
            creationDate: extractValue(tag: "date") ?? extractValue(tag: "Date") ?? extractEntry(key: "tarih"),
            title: extractValue(tag: "title") ?? extractValue(tag: "Title"),
            subject: extractValue(tag: "subject") ?? extractEntry(key: "uyapdogrulamakodu")
        )
    }
}
