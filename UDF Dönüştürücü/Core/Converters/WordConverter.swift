import Foundation

final class WordConverter {

    static func convert(document: UDFDocument) throws -> URL {
        let docxDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        let wordDir = docxDir.appendingPathComponent("word")
        let relsDir = docxDir.appendingPathComponent("_rels")
        let wordRelsDir = wordDir.appendingPathComponent("_rels")

        try FileManager.default.createDirectory(at: wordRelsDir, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: relsDir, withIntermediateDirectories: true)

        // [Content_Types].xml
        try contentTypesXML().write(to: docxDir.appendingPathComponent("[Content_Types].xml"),
                                     atomically: true, encoding: .utf8)

        // _rels/.rels
        try relsXML().write(to: relsDir.appendingPathComponent(".rels"),
                            atomically: true, encoding: .utf8)

        // word/_rels/document.xml.rels
        try wordRelsXML().write(to: wordRelsDir.appendingPathComponent("document.xml.rels"),
                                atomically: true, encoding: .utf8)

        // word/styles.xml
        try stylesXML().write(to: wordDir.appendingPathComponent("styles.xml"),
                              atomically: true, encoding: .utf8)

        // word/document.xml
        let documentXML = buildDocumentXML(from: document)
        try documentXML.write(to: wordDir.appendingPathComponent("document.xml"),
                              atomically: true, encoding: .utf8)

        // Create ZIP (DOCX)
        let outputDir = PDFConverter.outputDirectory()
        let outputURL = outputDir
            .appendingPathComponent(document.fileName.replacingOccurrences(of: ".udf", with: ""))
            .appendingPathExtension("docx")

        try createZIP(from: docxDir, to: outputURL)

        // Cleanup
        try? FileManager.default.removeItem(at: docxDir)

        return outputURL
    }

    // MARK: - OOXML Templates

    private static func contentTypesXML() -> String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
        </Types>
        """
    }

    private static func relsXML() -> String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
        """
    }

    private static func wordRelsXML() -> String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
        """
    }

    private static func stylesXML() -> String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:style w:type="paragraph" w:styleId="Normal" w:default="1">
                <w:name w:val="Normal"/>
                <w:rPr>
                    <w:sz w:val="22"/>
                    <w:szCs w:val="22"/>
                    <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                </w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Heading1">
                <w:name w:val="heading 1"/>
                <w:pPr><w:outlineLvl w:val="0"/></w:pPr>
                <w:rPr>
                    <w:b/>
                    <w:sz w:val="32"/>
                    <w:szCs w:val="32"/>
                    <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                </w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Heading2">
                <w:name w:val="heading 2"/>
                <w:pPr><w:outlineLvl w:val="1"/></w:pPr>
                <w:rPr>
                    <w:b/>
                    <w:sz w:val="26"/>
                    <w:szCs w:val="26"/>
                    <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                </w:rPr>
            </w:style>
        </w:styles>
        """
    }

    // MARK: - Document Building

    private static func buildDocumentXML(from document: UDFDocument) -> String {
        var body = ""

        // Metadata as header
        if let meta = document.metadata {
            if let title = meta.title {
                body += paragraph(text: title, style: "Heading1")
            }
            if let author = meta.author {
                body += paragraph(text: "Yazar: \(author)", italic: true, fontSize: 18)
            }
            if let date = meta.creationDate {
                body += paragraph(text: "Tarih: \(date)", italic: true, fontSize: 18)
            }
            body += paragraph(text: "") // Empty line
        }

        // Content: Use plain text for UYAP or sections for others
        if document.content.contentType == .uyap || document.content.sections.isEmpty {
            let lines = document.content.text.components(separatedBy: "\n")
            for line in lines {
                body += paragraph(text: line)
            }
        } else {
            for section in document.content.sections {
                if let title = section.title {
                    body += paragraph(text: title, style: "Heading2")
                }
                let lines = section.body.components(separatedBy: "\n")
                for line in lines {
                    body += paragraph(text: line)
                }
            }
        }

        // Tables
        for table in document.content.tables {
            body += buildTableXML(table)
        }

        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <w:body>
                \(body)
            </w:body>
        </w:document>
        """
    }

    private static func paragraph(text: String, style: String? = nil, bold: Bool = false, italic: Bool = false, fontSize: Int? = nil) -> String {
        let escapedText = escapeXML(text)
        var pPr = ""
        if let style = style {
            pPr += "<w:pStyle w:val=\"\(style)\"/>"
        }

        var rPr = ""
        if bold { rPr += "<w:b/>" }
        if italic { rPr += "<w:i/>" }
        if let size = fontSize {
            rPr += "<w:sz w:val=\"\(size)\"/><w:szCs w:val=\"\(size)\"/>"
        }

        let pPrBlock = pPr.isEmpty ? "" : "<w:pPr>\(pPr)</w:pPr>"
        let rPrBlock = rPr.isEmpty ? "" : "<w:rPr>\(rPr)</w:rPr>"

        return """
        <w:p>\(pPrBlock)<w:r>\(rPrBlock)<w:t xml:space="preserve">\(escapedText)</w:t></w:r></w:p>
        """
    }

    private static func buildTableXML(_ table: UDFTable) -> String {
        var xml = """
        <w:tbl>
            <w:tblPr>
                <w:tblBorders>
                    <w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                    <w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                    <w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                    <w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                    <w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                    <w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                </w:tblBorders>
            </w:tblPr>
        """

        for row in table.rows {
            xml += "<w:tr>"
            for cell in row {
                xml += "<w:tc><w:p><w:r><w:t xml:space=\"preserve\">\(escapeXML(cell))</w:t></w:r></w:p></w:tc>"
            }
            xml += "</w:tr>"
        }

        xml += "</w:tbl>"
        return xml
    }

    private static func escapeXML(_ text: String) -> String {
        text.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }

    // MARK: - ZIP Creation (for DOCX packaging)

    private static func createZIP(from sourceDir: URL, to destination: URL) throws {
        // Remove existing file
        try? FileManager.default.removeItem(at: destination)

        let coordinator = NSFileCoordinator()
        var error: NSError?

        coordinator.coordinate(readingItemAt: sourceDir, options: [.forUploading], error: &error) { zipURL in
            try? FileManager.default.copyItem(at: zipURL, to: destination)
        }

        if let error = error {
            throw ConversionError.docxCreationFailed
        }

        guard FileManager.default.fileExists(atPath: destination.path) else {
            throw ConversionError.docxCreationFailed
        }
    }
}
