import Foundation
import Combine

/// Mail/WhatsApp/Dosyalar'dan "Birlikte Aç" ile gelen UDF dosyalarını ana ekrana yönlendirir.
final class IncomingFileRouter: ObservableObject {
    static let shared = IncomingFileRouter()

    @Published var incomingFile: URL?

    private init() {}

    func handle(url: URL) {
        guard url.pathExtension.lowercased() == "udf" else { return }

        let needsAccess = url.startAccessingSecurityScopedResource()
        defer { if needsAccess { url.stopAccessingSecurityScopedResource() } }

        // Inbox'taki dosya kalıcı olmayabilir; temp'e kopyala.
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathComponent(url.lastPathComponent)

        do {
            try FileManager.default.createDirectory(
                at: destination.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try FileManager.default.copyItem(at: url, to: destination)
            AnalyticsService.logFileOpenedExternal()
            DispatchQueue.main.async {
                self.incomingFile = destination
            }
        } catch {
            // Kopyalanamadıysa sessizce yut — kullanıcı dosyayı uygulama içinden seçebilir.
        }
    }
}
