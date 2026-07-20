import Foundation

/// Yapılandırılmış UDF modelini UYAP content.xml (CDATA + elements + headers + footers) biçimine yazar.
enum UDFStructureWriter {

    struct BuildResult {
        let cdataText: String
        let elementsXML: String
        let headersXML: String?
        let footersXML: String?
    }

    static func build(from document: UDFEditDocument) -> BuildResult {
        var cdata = ""
        var offset = 0

        let headersXML = writeHeaderFooterSection(
            document.headers,
            itemTag: "header",
            cdata: &cdata,
            offset: &offset
        )

        var elements = "\n"
        for block in document.blocks {
            switch block {
            case .paragraph(let para):
                elements += writeParagraph(para, cdata: &cdata, offset: &offset)
            case .table(let table):
                elements += writeTable(table, cdata: &cdata, offset: &offset)
            }
        }

        let footersXML = writeHeaderFooterSection(
            document.footers,
            itemTag: "footer",
            cdata: &cdata,
            offset: &offset
        )

        return BuildResult(
            cdataText: cdata,
            elementsXML: elements,
            headersXML: headersXML,
            footersXML: footersXML
        )
    }

    // MARK: - Header / Footer

    private static func writeHeaderFooterSection(
        _ items: [UDFEditHeaderFooter],
        itemTag: String,
        cdata: inout String,
        offset: inout Int
    ) -> String? {
        guard !items.isEmpty else { return nil }
        var xml = "\n"
        for item in items {
            xml += "  <\(itemTag) type=\"\(escape(item.type))\">\n"
            for para in item.paragraphs {
                xml += writeParagraph(para, cdata: &cdata, offset: &offset, indent: "    ")
            }
            xml += "  </\(itemTag)>\n"
        }
        return xml
    }

    // MARK: - Paragraph

    private static func writeParagraph(_ para: UDFEditParagraph, cdata: inout String, offset: inout Int, indent: String = "") -> String {
        var tabAttr = ""
        if !para.tabStops.isEmpty, let first = para.tabStops.first {
            tabAttr = " TabSet=\"\(first):0:0\""
        }

        var xml = "\(indent)<paragraph SpaceAbove=\"\(para.spaceAbove)\" SpaceBelow=\"\(para.spaceBelow)\" "
            + "LeftIndent=\"\(para.leftIndent)\" RightIndent=\"\(para.rightIndent)\" "
            + "LineSpacing=\"\(para.lineSpacing)\" resolver=\"hvl-default\" "
            + "Alignment=\"\(para.alignment)\" Hanging=\"\(para.hangingIndent)\"\(tabAttr)>"

        let paraText = para.runs.map(\.text).joined()
        var runOffset = offset

        if paraText.isEmpty {
            xml += "<content resolver=\"hvl-default\" startOffset=\"\(offset)\" length=\"0\" />"
            cdata += "\n"
            offset += 1
        } else {
            for run in para.runs where !run.text.isEmpty {
                xml += runTag(run, startOffset: runOffset, length: run.text.count)
                runOffset += run.text.count
            }
            cdata += paraText + "\n"
            offset += paraText.count + 1
        }

        xml += "</paragraph>\n"
        return xml
    }

    // MARK: - Table

    private static func writeTable(_ table: UDFEditTable, cdata: inout String, offset: inout Int) -> String {
        let spans = table.columnSpans.map(String.init).joined(separator: ",")
        var spansAttr = spans.isEmpty ? "" : " columnSpans=\"\(spans)\""
        var xml = "<table tableName=\"Tablo\" columnCount=\"\(table.columnCount)\"\(spansAttr) border=\"\(escape(table.border))\">\n"

        for row in table.rows {
            xml += "  <row rowName=\"row\" rowType=\"\(escape(row.rowType))\" border=\"\(escape(table.border))\">\n"
            for cell in row.cells {
                var cellAttrs = "colspan=\"\(cell.colspan)\" rowspan=\"\(cell.rowspan)\" align=\"top\" border=\"\(escape(table.border))\" borderSpec=\"15\""
                if let fill = cell.fillColorARGB {
                    cellAttrs += " fillColor=\"\(fill)\""
                }
                xml += "    <cell \(cellAttrs)>\n"
                for para in cell.paragraphs {
                    xml += writeParagraph(para, cdata: &cdata, offset: &offset, indent: "      ")
                }
                xml += "    </cell>\n"
            }
            xml += "  </row>\n"
        }

        xml += "</table>\n"
        return xml
    }

    // MARK: - Run tag

    private static func runTag(_ run: UDFEditRun, startOffset: Int, length: Int) -> String {
        let tagName: String
        switch run.kind {
        case .content: tagName = "content"
        case .field: tagName = "field"
        case .space: tagName = "space"
        }

        var attrs = " resolver=\"hvl-default\""
        if run.isBold { attrs += " bold=\"true\"" }
        if run.isItalic { attrs += " italic=\"true\"" }
        if run.isUnderline { attrs += " underline=\"true\"" }
        if run.fontSize != 12 && run.fontSize > 0 {
            attrs += " size=\"\(Int(run.fontSize))\""
        }
        attrs += " family=\"\(escape(run.fontFamily))\""
        if let fg = run.foregroundARGB {
            attrs += " foreground=\"\(fg)\""
        }
        if let bg = run.backgroundARGB {
            attrs += " background=\"\(bg)\""
        }
        if case .field(let name) = run.kind {
            attrs += " fieldName=\"\(escape(name))\""
        }
        attrs += " startOffset=\"\(startOffset)\" length=\"\(length)\""
        return "<\(tagName)\(attrs) />"
    }

    private static func escape(_ string: String) -> String {
        string.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }
}
