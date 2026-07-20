import Foundation
import Compression

/// PDF veya DOCX'ten çıkarılan içerikle UYAP UDF dosyası oluşturur
final class UDFCreator {

    /// Bir paragraf içindeki biçimlendirme parçası (run)
    struct InputRun {
        let text: String
        let isBold: Bool
        let isItalic: Bool
        let isUnderline: Bool
        let fontSize: CGFloat
        let fontFamily: String
    }

    /// Biçimlendirilmiş paragraf
    struct InputParagraph {
        let runs: [InputRun]
        let alignment: Int // 0=left, 1=center, 2=right, 3=justify

        var text: String { runs.map(\.text).joined() }
    }

    static func createFromPDF(url: URL) throws -> URL {
        let extracted = try PDFExtractor.extract(from: url)
        let paragraphs = extracted.paragraphs.map { para in
            InputParagraph(
                runs: para.runs.map { run in
                    InputRun(text: run.text, isBold: run.isBold, isItalic: run.isItalic,
                             isUnderline: run.isUnderline, fontSize: run.fontSize, fontFamily: run.fontFamily)
                },
                alignment: para.alignment
            )
        }
        let fileName = url.deletingPathExtension().lastPathComponent
        return try buildUDF(fileName: fileName, paragraphs: paragraphs)
    }

    static func createFromDOCX(url: URL) throws -> URL {
        let extracted = try DOCXExtractor.extract(from: url)
        let paragraphs = extracted.paragraphs.map { para in
            InputParagraph(
                runs: para.runs.map { run in
                    InputRun(text: run.text, isBold: run.isBold, isItalic: run.isItalic,
                             isUnderline: run.isUnderline, fontSize: run.fontSize, fontFamily: run.fontFamily)
                },
                alignment: para.alignment
            )
        }
        let fileName = url.deletingPathExtension().lastPathComponent
        return try buildUDF(fileName: fileName, paragraphs: paragraphs)
    }

    /// Hazır paragraf listesinden UDF üretir (şablon motoru ve OCR çıktısı için).
    static func create(fileName: String, paragraphs: [InputParagraph]) throws -> URL {
        try buildUDF(fileName: fileName, paragraphs: paragraphs)
    }

    /// Zengin düzenleme modelinden UDF üretir (tablo, alan, renk destekli).
    static func create(fileName: String, document: UDFEditDocument) throws -> URL {
        let built = UDFStructureWriter.build(from: document)
        let contentXML = buildContentXML(
            cdataText: built.cdataText,
            elementsXML: built.elementsXML,
            headersXML: built.headersXML,
            footersXML: built.footersXML
        )
        return try writeUDF(fileName: fileName, contentXML: contentXML)
    }

    // MARK: - Build UDF ZIP

    private static func buildUDF(fileName: String, paragraphs: [InputParagraph]) throws -> URL {
        let cdataText = paragraphs.map(\.text).joined(separator: "\n")
        let contentXML = buildContentXML(cdataText: cdataText, paragraphs: paragraphs)
        return try writeUDF(fileName: fileName, contentXML: contentXML)
    }

    private static func writeUDF(fileName: String, contentXML: String) throws -> URL {
        let propertiesXML = buildPropertiesXML()

        guard let contentData = contentXML.data(using: .utf8),
              let propertiesData = propertiesXML.data(using: .utf8) else {
            throw ConversionError.exportFailed("UDF içerik oluşturulamadı.")
        }

        let zipData = buildZIPArchiveDeflate(entries: [
            ("content.xml", contentData),
            ("documentproperties.xml", propertiesData)
        ])

        let outputDir = PDFConverter.outputDirectory()
        let outputURL = outputDir
            .appendingPathComponent(fileName)
            .appendingPathExtension("udf")

        try zipData.write(to: outputURL, options: .atomic)
        return outputURL
    }

    // MARK: - Content XML

    private static func buildContentXML(
        cdataText: String,
        elementsXML: String,
        headersXML: String? = nil,
        footersXML: String? = nil
    ) -> String {
        let stylesXML = "<styles>"
            + "<style name=\"default\" italic=\"false\" description=\"Geçerli\" size=\"12\" RightIndent=\"15.0\" bold=\"false\" family=\"Dialog\" foreground=\"-16777216\" FONT_ATTRIBUTE_KEY=\"javax.swing.plaf.FontUIResource[family=Dialog,name=Dialog,style=plain,size=12]\" />"
            + "<style name=\"hvl-default\" SpaceAbove=\"0.0\" description=\"Gövde\" SpaceBelow=\"0.0\" size=\"12\" LeftIndent=\"0.0\" RightIndent=\"0.0\" LineSpacing=\"0.0\" Alignment=\"0\" family=\"Times New Roman\" />"
            + "</styles>"

        var sectionsXML = "<elements >\(elementsXML)</elements>\n"
        if let headersXML {
            sectionsXML += "<headers>\(headersXML)</headers>\n"
        }
        if let footersXML {
            sectionsXML += "<footers>\(footersXML)</footers>\n"
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?> \n\n"
            + "<template format_id=\"1.8\" >\n"
            + "<content><![CDATA[\(cdataText)]]></content>"
            + "<properties>"
            + "<pageFormat mediaSizeName=\"1\" leftMargin=\"56.69291305541992\" rightMargin=\"56.69291305541992\" topMargin=\"28.34645652770996\" bottomMargin=\"28.34645652770996\" paperOrientation=\"1\" headerFOffset=\"15.0\" footerFOffset=\"60.00944846916199\" />"
            + "</properties>\n"
            + sectionsXML
            + stylesXML + "\n"
            + "</template>\n"
    }

    private static func buildContentXML(cdataText: String, paragraphs: [InputParagraph]) -> String {
        var elementsXML = "\n"
        var currentOffset = 0

        for para in paragraphs {
            let paraText = para.text
            let paraLength = paraText.count

            // Paragraph opening tag
            elementsXML += "<paragraph SpaceAbove=\"1.0\" SpaceBelow=\"1.0\" LeftIndent=\"0.0\" RightIndent=\"0.0\" LineSpacing=\"0.0\" resolver=\"hvl-default\" Alignment=\"\(para.alignment)\" Hanging=\"0.0\">"

            if paraLength == 0 {
                // Empty paragraph
                elementsXML += "<content resolver=\"hvl-default\" startOffset=\"\(currentOffset)\" length=\"0\" />"
                currentOffset += 1 // newline separator
            } else {
                // Write each run as a separate <content> element with its own formatting
                var runOffset = currentOffset
                for run in para.runs {
                    let runLength = run.text.count
                    guard runLength > 0 else { continue }

                    var attrs = " resolver=\"hvl-default\""
                    if run.isBold { attrs += " bold=\"true\"" }
                    if run.isItalic { attrs += " italic=\"true\"" }
                    if run.isUnderline { attrs += " underline=\"true\"" }
                    if run.fontSize != 12 && run.fontSize > 0 {
                        attrs += " size=\"\(Int(run.fontSize))\""
                    }
                    let family = run.fontFamily.isEmpty ? "Times New Roman" : run.fontFamily
                    attrs += " family=\"\(escapeXML(family))\""
                    attrs += " startOffset=\"\(runOffset)\" length=\"\(runLength)\""

                    elementsXML += "<content\(attrs) />"
                    runOffset += runLength
                }
                currentOffset += paraLength + 1 // +1 for newline separator
            }

            elementsXML += "</paragraph>\n"
        }

        return buildContentXML(cdataText: cdataText, elementsXML: elementsXML)
    }

    private static func buildPropertiesXML() -> String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n"
            + "<properties>\n"
            + "<entry key=\"uyapdogrulamakodu\"></entry>\n"
            + "</properties>"
    }

    private static func escapeXML(_ string: String) -> String {
        string.replacingOccurrences(of: "&", with: "&amp;")
              .replacingOccurrences(of: "<", with: "&lt;")
              .replacingOccurrences(of: ">", with: "&gt;")
              .replacingOccurrences(of: "\"", with: "&quot;")
    }

    // MARK: - ZIP Builder with Deflate (method 8) — matching UYAP format

    private static func buildZIPArchiveDeflate(entries: [(String, Data)]) -> Data {
        var zipData = Data()

        struct LocalEntry {
            let name: String
            let uncompressedData: Data
            let compressedData: Data
            let crc32: UInt32
            let offset: Int
        }

        var localEntries: [LocalEntry] = []

        for (name, fileData) in entries {
            let offset = zipData.count
            let crc = crc32(fileData)
            let nameData = Data(name.utf8)

            let (compressedData, method): (Data, UInt16)
            if let deflated = deflateRaw(fileData), deflated.count < fileData.count {
                compressedData = deflated
                method = 8
            } else {
                compressedData = fileData
                method = 0
            }

            zipData.append(contentsOf: [0x50, 0x4B, 0x03, 0x04])
            appendUInt16(&zipData, 20)
            appendUInt16(&zipData, 0)
            appendUInt16(&zipData, method)
            appendUInt16(&zipData, 0)
            appendUInt16(&zipData, 0)
            appendUInt32(&zipData, crc)
            appendUInt32(&zipData, UInt32(compressedData.count))
            appendUInt32(&zipData, UInt32(fileData.count))
            appendUInt16(&zipData, UInt16(nameData.count))
            appendUInt16(&zipData, 0)
            zipData.append(nameData)
            zipData.append(compressedData)

            localEntries.append(LocalEntry(
                name: name, uncompressedData: fileData,
                compressedData: compressedData, crc32: crc, offset: offset
            ))
        }

        let centralDirStart = zipData.count

        for entry in localEntries {
            let nameData = Data(entry.name.utf8)
            let method: UInt16 = (entry.compressedData.count < entry.uncompressedData.count) ? 8 : 0

            zipData.append(contentsOf: [0x50, 0x4B, 0x01, 0x02])
            appendUInt16(&zipData, 20)
            appendUInt16(&zipData, 20)
            appendUInt16(&zipData, 0)
            appendUInt16(&zipData, method)
            appendUInt16(&zipData, 0)
            appendUInt16(&zipData, 0)
            appendUInt32(&zipData, entry.crc32)
            appendUInt32(&zipData, UInt32(entry.compressedData.count))
            appendUInt32(&zipData, UInt32(entry.uncompressedData.count))
            appendUInt16(&zipData, UInt16(nameData.count))
            appendUInt16(&zipData, 0)
            appendUInt16(&zipData, 0)
            appendUInt16(&zipData, 0)
            appendUInt16(&zipData, 0)
            appendUInt32(&zipData, 0)
            appendUInt32(&zipData, UInt32(entry.offset))
            zipData.append(nameData)
        }

        let centralDirSize = zipData.count - centralDirStart

        zipData.append(contentsOf: [0x50, 0x4B, 0x05, 0x06])
        appendUInt16(&zipData, 0)
        appendUInt16(&zipData, 0)
        appendUInt16(&zipData, UInt16(localEntries.count))
        appendUInt16(&zipData, UInt16(localEntries.count))
        appendUInt32(&zipData, UInt32(centralDirSize))
        appendUInt32(&zipData, UInt32(centralDirStart))
        appendUInt16(&zipData, 0)

        return zipData
    }

    // MARK: - Raw Deflate Compression

    private static func deflateRaw(_ data: Data) -> Data? {
        let sourceSize = data.count
        guard sourceSize > 0 else { return Data() }

        let destinationSize = sourceSize + 512
        let destinationBuffer = UnsafeMutablePointer<UInt8>.allocate(capacity: destinationSize)
        defer { destinationBuffer.deallocate() }

        let compressedSize = data.withUnsafeBytes { sourcePtr -> Int in
            guard let baseAddress = sourcePtr.baseAddress else { return 0 }
            return compression_encode_buffer(
                destinationBuffer, destinationSize,
                baseAddress.assumingMemoryBound(to: UInt8.self), sourceSize,
                nil, COMPRESSION_ZLIB
            )
        }

        guard compressedSize > 0 else { return nil }
        return Data(bytes: destinationBuffer, count: compressedSize)
    }

    // MARK: - Helpers

    private static func appendUInt16(_ data: inout Data, _ value: UInt16) {
        data.append(UInt8(value & 0xFF))
        data.append(UInt8((value >> 8) & 0xFF))
    }

    private static func appendUInt32(_ data: inout Data, _ value: UInt32) {
        data.append(UInt8(value & 0xFF))
        data.append(UInt8((value >> 8) & 0xFF))
        data.append(UInt8((value >> 16) & 0xFF))
        data.append(UInt8((value >> 24) & 0xFF))
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                if crc & 1 == 1 {
                    crc = (crc >> 1) ^ 0xEDB88320
                } else {
                    crc >>= 1
                }
            }
        }
        return crc ^ 0xFFFFFFFF
    }
}
