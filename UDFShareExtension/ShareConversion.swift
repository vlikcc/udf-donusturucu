import Foundation
import UniformTypeIdentifiers

enum ShareConversionError: LocalizedError {
    case noContainer
    case tooLarge(String)

    var errorDescription: String? {
        switch self {
        case .noContainer:
            return "Paylaşılan klasöre erişilemedi. Her iki hedefte de App Groups yetkisinin tanımlı olduğundan emin olun."
        case .tooLarge(let name):
            return "\(name) bu ekrandan dönüştürülemeyecek kadar büyük. Lütfen Evrak Dönüştürücü uygulamasını açıp oradan dönüştürün."
        }
    }
}

/// Extension tarafındaki dönüştürme akışı. Ana uygulamanın motorlarını (UDFParser,
/// PDFConverter, WordConverter) kullanır — bu dosyaların extension target'ının da
/// Target Membership'inde olması gerekir.
enum ShareConversion {

    /// Extension'lar dar bir bellek bütçesinde çalışır; bu sınırın üstündeki belgeler
    /// ana uygulamaya yönlendirilir.
    static let maxInputBytes: Int64 = 20 * 1024 * 1024

    /// Paylaşılan öğelerden .udf dosyalarını çözer.
    static func resolveUDFFiles(from attachments: [NSItemProvider]) async -> [URL] {
        var urls: [URL] = []
        for provider in attachments {
            guard provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) else { continue }
            if let url = await loadFileURL(from: provider), url.pathExtension.lowercased() == "udf" {
                urls.append(url)
            }
        }
        return urls
    }

    private static func loadFileURL(from provider: NSItemProvider) async -> URL? {
        await withCheckedContinuation { continuation in
            provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier) { item, _ in
                if let url = item as? URL {
                    continuation.resume(returning: url)
                } else if let data = item as? Data,
                          let url = URL(dataRepresentation: data, relativeTo: nil) {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    /// Belgeleri dönüştürür, çıktıyı App Group gelen kutusuna taşır ve ana uygulamanın
    /// devralması için bekleyen kayıt olarak işaretler. Dönüştürülen belge sayısını döner.
    @discardableResult
    static func convert(files: [URL], to format: ShareOutputFormat) throws -> Int {
        guard let inbox = AppGroup.inboxDirectory() else { throw ShareConversionError.noContainer }

        var converted = 0
        for url in files {
            let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0
            guard size <= maxInputBytes else {
                throw ShareConversionError.tooLarge(url.lastPathComponent)
            }

            let needsStop = url.startAccessingSecurityScopedResource()
            defer { if needsStop { url.stopAccessingSecurityScopedResource() } }

            let document = try UDFParser.parse(fileURL: url)
            let output: URL
            switch format {
            case .pdf: output = try PDFConverter.convert(document: document)
            case .docx: output = try WordConverter.convert(document: document)
            }

            // Çıktı extension'ın kendi konteynerinde; ana uygulamanın görebilmesi için
            // App Group klasörüne taşınır.
            let destination = uniqueURL(in: inbox, preferredName: output.lastPathComponent)
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.moveItem(at: output, to: destination)

            AppGroup.appendPendingRecord(
                AppGroup.PendingRecord(
                    originalFileName: destination.lastPathComponent,
                    outputFormat: format.recordFormat,
                    inboxFileName: destination.lastPathComponent,
                    date: Date()
                )
            )
            converted += 1
        }
        return converted
    }

    /// Aynı adlı dosya varsa sonuna sayı ekler.
    private static func uniqueURL(in directory: URL, preferredName: String) -> URL {
        let base = (preferredName as NSString).deletingPathExtension
        let ext = (preferredName as NSString).pathExtension
        var candidate = directory.appendingPathComponent(preferredName)
        var index = 2
        while FileManager.default.fileExists(atPath: candidate.path) {
            candidate = directory.appendingPathComponent("\(base)_\(index).\(ext)")
            index += 1
        }
        return candidate
    }
}
