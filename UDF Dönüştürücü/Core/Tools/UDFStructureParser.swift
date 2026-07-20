import Foundation

/// UYAP content.xml içindeki <elements> bölümünü yapılandırılmış düzenleme modeline çevirir.
enum UDFStructureParser {

    static func parse(rawXML: String, plainText: String) -> UDFEditDocument {
        let headers = parseHeaderFooterContainer(rawXML, container: "headers", item: "header", plainText: plainText)
        let footers = parseHeaderFooterContainer(rawXML, container: "footers", item: "footer", plainText: plainText)

        guard rawXML.contains("<elements") else {
            var doc = fallbackDocument(from: plainText)
            doc.headers = headers
            doc.footers = footers
            return doc
        }

        guard let elementsBody = extractElementsBody(from: rawXML) else {
            var doc = fallbackDocument(from: plainText)
            doc.headers = headers
            doc.footers = footers
            return doc
        }

        var blocks: [UDFEditBlock] = []
        var search = elementsBody.startIndex

        while search < elementsBody.endIndex {
            if let range = elementsBody.range(of: "<paragraph ", range: search..<elementsBody.endIndex),
               let end = elementsBody.range(of: "</paragraph>", range: range.upperBound..<elementsBody.endIndex) {
                let xml = String(elementsBody[range.lowerBound..<end.upperBound])
                blocks.append(.paragraph(parseParagraph(xml, plainText: plainText)))
                search = end.upperBound
                continue
            }

            if let range = elementsBody.range(of: "<table", range: search..<elementsBody.endIndex),
               let end = elementsBody.range(of: "</table>", range: range.upperBound..<elementsBody.endIndex) {
                let xml = String(elementsBody[range.lowerBound..<end.upperBound])
                blocks.append(.table(parseTable(xml, plainText: plainText)))
                search = end.upperBound
                continue
            }

            if let nextPara = elementsBody.range(of: "<paragraph ", range: search..<elementsBody.endIndex),
               let nextTable = elementsBody.range(of: "<table", range: search..<elementsBody.endIndex) {
                search = min(nextPara.lowerBound, nextTable.lowerBound)
            } else if let next = elementsBody.range(of: "<paragraph ", range: search..<elementsBody.endIndex)
                ?? elementsBody.range(of: "<table", range: search..<elementsBody.endIndex) {
                search = next.lowerBound
            } else {
                break
            }
        }

        if blocks.isEmpty {
            var doc = fallbackDocument(from: plainText)
            doc.headers = headers
            doc.footers = footers
            return doc
        }
        return UDFEditDocument(headers: headers, blocks: blocks, footers: footers)
    }

    // MARK: - Header / Footer

    private static func parseHeaderFooterContainer(
        _ xml: String,
        container: String,
        item: String,
        plainText: String
    ) -> [UDFEditHeaderFooter] {
        guard let body = extractTaggedBody(xml, openTag: "<\(container)", closeTag: "</\(container)>") else {
            return []
        }

        var items: [UDFEditHeaderFooter] = []
        var search = body.startIndex
        let openItem = "<\(item)"

        while let start = body.range(of: openItem, range: search..<body.endIndex),
              let end = body.range(of: "</\(item)>", range: start.upperBound..<body.endIndex) {
            let itemXML = String(body[start.lowerBound..<end.upperBound])
            let type = extractStringAttr(itemXML, name: "type") ?? "default"
            var paragraphs: [UDFEditParagraph] = []
            var pSearch = itemXML.startIndex

            while let pStart = itemXML.range(of: "<paragraph ", range: pSearch..<itemXML.endIndex),
                  let pEnd = itemXML.range(of: "</paragraph>", range: pStart.upperBound..<itemXML.endIndex) {
                let pXML = String(itemXML[pStart.lowerBound..<pEnd.upperBound])
                paragraphs.append(parseParagraph(pXML, plainText: plainText))
                pSearch = pEnd.upperBound
            }

            if paragraphs.isEmpty,
               let pStart = itemXML.range(of: "<paragraph", range: itemXML.startIndex..<itemXML.endIndex),
               let pEnd = itemXML.range(of: "</paragraph>", range: pStart.upperBound..<itemXML.endIndex) {
                let pXML = String(itemXML[pStart.lowerBound..<pEnd.upperBound])
                paragraphs.append(parseParagraph(pXML, plainText: plainText))
            }

            if paragraphs.isEmpty {
                paragraphs = [UDFEditParagraph(runs: [UDFEditRun(text: "")])]
            }

            items.append(UDFEditHeaderFooter(type: type, paragraphs: paragraphs))
            search = end.upperBound
        }

        return items
    }

    // MARK: - Paragraph

    private static func parseParagraph(_ xml: String, plainText: String) -> UDFEditParagraph {
        UDFEditParagraph(
            alignment: extractIntAttr(xml, name: "Alignment") ?? 3,
            spaceAbove: extractFloatAttr(xml, name: "SpaceAbove") ?? 1,
            spaceBelow: extractFloatAttr(xml, name: "SpaceBelow") ?? 1,
            leftIndent: extractFloatAttr(xml, name: "LeftIndent") ?? 0,
            rightIndent: extractFloatAttr(xml, name: "RightIndent") ?? 0,
            firstLineIndent: extractFloatAttr(xml, name: "FirstLineIndent") ?? 0,
            hangingIndent: extractFloatAttr(xml, name: "Hanging") ?? 0,
            lineSpacing: extractFloatAttr(xml, name: "LineSpacing") ?? 0,
            tabStops: parseTabStops(xml),
            runs: parseRuns(in: xml, plainText: plainText)
        )
    }

    private static func parseRuns(in xml: String, plainText: String) -> [UDFEditRun] {
        let chars = Array(plainText)
        struct TagRun {
            let kind: UDFEditRun.Kind
            let startOffset: Int
            let length: Int
            let bold: Bool
            let italic: Bool
            let underline: Bool
            let fontSize: CGFloat?
            let fontFamily: String?
            let foreground: Int?
            let background: Int?
        }

        var tagged: [TagRun] = []
        let patterns: [(String, UDFEditRun.Kind)] = [
            ("<content ", .content),
            ("<field ", .field(name: "")),
            ("<space ", .space)
        ]

        for (pattern, kind) in patterns {
            var search = xml.startIndex
            while let tagStart = xml.range(of: pattern, range: search..<xml.endIndex),
                  let tagEnd = xml.range(of: "/>", range: tagStart.upperBound..<xml.endIndex) {
                let tag = String(xml[tagStart.lowerBound..<tagEnd.upperBound])
                guard let start = extractIntAttr(tag, name: "startOffset"),
                      let length = extractIntAttr(tag, name: "length") else {
                    search = tagEnd.upperBound
                    continue
                }

                let resolvedKind: UDFEditRun.Kind
                if case .field = kind {
                    let name = extractStringAttr(tag, name: "fieldName")
                        ?? extractStringAttr(tag, name: "name")
                        ?? extractStringAttr(tag, name: "FieldName")
                        ?? "alan"
                    resolvedKind = .field(name: name)
                } else {
                    resolvedKind = kind
                }

                tagged.append(TagRun(
                    kind: resolvedKind,
                    startOffset: start,
                    length: length,
                    bold: tag.localizedCaseInsensitiveContains("bold=\"true\""),
                    italic: tag.localizedCaseInsensitiveContains("italic=\"true\""),
                    underline: tag.localizedCaseInsensitiveContains("underline=\"true\""),
                    fontSize: extractFloatAttr(tag, name: "size") ?? extractFloatAttr(tag, name: "Size"),
                    fontFamily: extractStringAttr(tag, name: "family") ?? extractStringAttr(tag, name: "Family"),
                    foreground: UDFColorCodec.parse(extractStringAttr(tag, name: "foreground"))
                        ?? UDFColorCodec.parse(extractStringAttr(tag, name: "Foreground")),
                    background: UDFColorCodec.parse(extractStringAttr(tag, name: "background"))
                        ?? UDFColorCodec.parse(extractStringAttr(tag, name: "Background"))
                ))
                search = tagEnd.upperBound
            }
        }

        tagged.sort { $0.startOffset < $1.startOffset }

        return tagged.map { tag in
            let text = sliceText(from: chars, start: tag.startOffset, length: tag.length)
            return UDFEditRun(
                kind: tag.kind,
                text: text,
                isBold: tag.bold,
                isItalic: tag.italic,
                isUnderline: tag.underline,
                fontSize: tag.fontSize ?? 12,
                fontFamily: tag.fontFamily ?? "Times New Roman",
                foregroundARGB: tag.foreground,
                backgroundARGB: tag.background
            )
        }
    }

    // MARK: - Table

    private static func parseTable(_ xml: String, plainText: String) -> UDFEditTable {
        let columnCount = extractIntAttr(xml, name: "columnCount") ?? 2
        let spansString = extractStringAttr(xml, name: "columnSpans") ?? ""
        let columnSpans = spansString.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        let border = extractStringAttr(xml, name: "border") ?? "borderCell"

        var rows: [UDFEditTableRow] = []
        var search = xml.startIndex
        while let rowStart = xml.range(of: "<row", range: search..<xml.endIndex),
              let rowEnd = xml.range(of: "</row>", range: rowStart.upperBound..<xml.endIndex) {
            let rowXML = String(xml[rowStart.lowerBound..<rowEnd.upperBound])
            rows.append(parseTableRow(rowXML, plainText: plainText))
            search = rowEnd.upperBound
        }

        return UDFEditTable(
            columnCount: max(columnCount, 1),
            columnSpans: columnSpans,
            border: border,
            rows: rows
        )
    }

    private static func parseTableRow(_ xml: String, plainText: String) -> UDFEditTableRow {
        let rowType = extractStringAttr(xml, name: "rowType") ?? "dataRow"
        var cells: [UDFEditTableCell] = []
        var search = xml.startIndex

        while let cellStart = xml.range(of: "<cell", range: search..<xml.endIndex),
              let cellEnd = xml.range(of: "</cell>", range: cellStart.upperBound..<xml.endIndex) {
            let cellXML = String(xml[cellStart.lowerBound..<cellEnd.upperBound])
            cells.append(parseTableCell(cellXML, plainText: plainText))
            search = cellEnd.upperBound
        }

        return UDFEditTableRow(rowType: rowType, cells: cells)
    }

    private static func parseTableCell(_ xml: String, plainText: String) -> UDFEditTableCell {
        let colspan = extractIntAttr(xml, name: "colspan") ?? 1
        let rowspan = extractIntAttr(xml, name: "rowspan") ?? 1
        let fill = UDFColorCodec.parse(extractStringAttr(xml, name: "fillColor"))
            ?? UDFColorCodec.parse(extractStringAttr(xml, name: "background"))

        var paragraphs: [UDFEditParagraph] = []
        var search = xml.startIndex
        while let pStart = xml.range(of: "<paragraph ", range: search..<xml.endIndex),
              let pEnd = xml.range(of: "</paragraph>", range: pStart.upperBound..<xml.endIndex) {
            let pXML = String(xml[pStart.lowerBound..<pEnd.upperBound])
            paragraphs.append(parseParagraph(pXML, plainText: plainText))
            search = pEnd.upperBound
        }

        if paragraphs.isEmpty,
           let pStart = xml.range(of: "<paragraph", range: xml.startIndex..<xml.endIndex),
           let pEnd = xml.range(of: "</paragraph>", range: pStart.upperBound..<xml.endIndex) {
            let pXML = String(xml[pStart.lowerBound..<pEnd.upperBound])
            paragraphs.append(parseParagraph(pXML, plainText: plainText))
        }

        if paragraphs.isEmpty {
            paragraphs = [UDFEditParagraph(runs: [UDFEditRun(text: "")])]
        }

        return UDFEditTableCell(
            colspan: max(colspan, 1),
            rowspan: max(rowspan, 1),
            fillColorARGB: fill,
            paragraphs: paragraphs
        )
    }

    // MARK: - Helpers

    private static func fallbackDocument(from text: String) -> UDFEditDocument {
        let paragraphs = text.components(separatedBy: "\n").map { line -> UDFEditParagraph in
            UDFEditParagraph(alignment: 3, runs: [UDFEditRun(text: line)])
        }
        return UDFEditDocument(blocks: paragraphs.map { UDFEditBlock.paragraph($0) })
    }

    private static func extractTaggedBody(_ xml: String, openTag: String, closeTag: String) -> String? {
        guard let start = xml.range(of: openTag) else { return nil }
        guard let openEnd = xml.range(of: ">", range: start.upperBound..<xml.endIndex) else { return nil }
        guard let close = xml.range(of: closeTag, range: openEnd.upperBound..<xml.endIndex) else { return nil }
        return String(xml[openEnd.upperBound..<close.lowerBound])
    }

    private static func extractElementsBody(from xml: String) -> String? {
        guard let start = xml.range(of: "<elements") else { return nil }
        guard let openEnd = xml.range(of: ">", range: start.upperBound..<xml.endIndex) else { return nil }
        guard let close = xml.range(of: "</elements>", range: openEnd.upperBound..<xml.endIndex) else { return nil }
        return String(xml[openEnd.upperBound..<close.lowerBound])
    }

    private static func sliceText(from chars: [Character], start: Int, length: Int) -> String {
        guard start >= 0, length >= 0, start < chars.count else { return "" }
        let end = min(start + length, chars.count)
        guard start < end else { return "" }
        return String(chars[start..<end])
    }

    private static func parseTabStops(_ xml: String) -> [CGFloat] {
        guard let tabStr = extractStringAttr(xml, name: "TabSet") else { return [] }
        let parts = tabStr.split(separator: ":")
        if let pos = parts.first.flatMap({ Double($0) }).map({ CGFloat($0) }), pos > 0 {
            return [pos]
        }
        return []
    }

    private static func extractIntAttr(_ xml: String, name: String) -> Int? {
        for key in [name, name.lowercased(), name.capitalized] {
            guard let range = xml.range(of: "\(key)=\"") else { continue }
            let start = range.upperBound
            guard let end = xml.range(of: "\"", range: start..<xml.endIndex) else { continue }
            if let val = Int(xml[start..<end.lowerBound]) { return val }
        }
        return nil
    }

    private static func extractFloatAttr(_ xml: String, name: String) -> CGFloat? {
        for key in [name, name.lowercased(), name.capitalized] {
            guard let range = xml.range(of: "\(key)=\"") else { continue }
            let start = range.upperBound
            guard let end = xml.range(of: "\"", range: start..<xml.endIndex) else { continue }
            if let val = Double(xml[start..<end.lowerBound]) { return CGFloat(val) }
        }
        return nil
    }

    private static func extractStringAttr(_ xml: String, name: String) -> String? {
        for key in [name, name.lowercased(), name.capitalized] {
            guard let range = xml.range(of: "\(key)=\"") else { continue }
            let start = range.upperBound
            guard let end = xml.range(of: "\"", range: start..<xml.endIndex) else { continue }
            let val = String(xml[start..<end.lowerBound])
            if !val.isEmpty { return val }
        }
        return nil
    }
}
