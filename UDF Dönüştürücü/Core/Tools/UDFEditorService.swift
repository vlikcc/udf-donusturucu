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
        let document = try UDFParser.parse(fileURL: url)
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

        let hasSignature = (try? SignatureInspector.inspect(udfURL: url))?.hasSignature ?? false
        let baseName = url.deletingPathExtension().lastPathComponent

        return UDFEditableDocument(
            sourceURL: url,
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
}

extension UDFEditDocument {
    var plainText: String {
        UDFStructureWriter.build(from: self).cdataText
    }
}
