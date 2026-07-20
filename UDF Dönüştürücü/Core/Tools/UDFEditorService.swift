import Foundation
import UIKit

struct UDFEditableDocument {
    let sourceURL: URL
    let baseName: String
    let model: UDFEditDocument
    let hasSignature: Bool
}

enum UDFEditorError: LocalizedError {
    case emptyContent
    case saveFailed(String)

    var errorDescription: String? {
        switch self {
        case .emptyContent:
            return "Belge boş olamaz."
        case .saveFailed(let detail):
            return "UDF kaydedilemedi: \(detail)"
        }
    }
}

/// UDF dosyasını yapılandırılmış model olarak yükler; tablo, alan ve renk bilgisini korur.
final class UDFEditorService {

    static func load(from url: URL) throws -> UDFEditableDocument {
        let readableURL = try prepareReadableCopy(from: url)
        let document = try UDFParser.parse(fileURL: readableURL)
        let model: UDFEditDocument

        if document.content.contentType == .uyap {
            model = UDFStructureParser.parse(
                rawXML: document.content.rawContent,
                plainText: document.content.text
            )
        } else {
            model = UDFStructureParser.parse(rawXML: "", plainText: document.content.text)
        }

        guard !model.plainText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw UDFEditorError.emptyContent
        }

        let hasSignature = (try? SignatureInspector.inspect(udfURL: readableURL))?.hasSignature ?? false
        let baseName = readableURL.deletingPathExtension().lastPathComponent

        return UDFEditableDocument(
            sourceURL: readableURL,
            baseName: baseName,
            model: model,
            hasSignature: hasSignature
        )
    }

    static func save(model: UDFEditDocument, baseName: String) throws -> URL {
        guard !model.plainText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw UDFEditorError.emptyContent
        }

        let fileName = "Duzenlenmis_\(baseName)"
        do {
            return try UDFCreator.create(fileName: fileName, document: model)
        } catch {
            throw UDFEditorError.saveFailed(error.localizedDescription)
        }
    }

    static func fingerprint(_ model: UDFEditDocument) -> String {
        let built = UDFStructureWriter.build(from: model)
        return built.cdataText
            + (built.headersXML ?? "")
            + built.elementsXML
            + (built.footersXML ?? "")
    }

    /// Dosya seçiciden gelen URL'yi güvenli ve okunabilir bir kopyaya alır.
    static func prepareReadableCopy(from url: URL) throws -> URL {
        let needsAccess = url.startAccessingSecurityScopedResource()
        defer { if needsAccess { url.stopAccessingSecurityScopedResource() } }

        guard FileManager.default.isReadableFile(atPath: url.path) else {
            throw UDFParserError.fileNotFound
        }

        var fileName = url.lastPathComponent
        if fileName.isEmpty { fileName = "belge.udf" }
        if !fileName.lowercased().hasSuffix(".udf") {
            fileName += ".udf"
        }

        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent("udf-editor", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent(fileName)

        try FileManager.default.createDirectory(
            at: destination.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )

        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }

        if url.path == destination.path {
            return url
        }

        try FileManager.default.copyItem(at: url, to: destination)
        return destination
    }
}

extension UDFEditDocument {
    var plainText: String {
        UDFStructureWriter.build(from: self).cdataText
    }
}
