import Foundation

/// Ana uygulama ile Share Extension'ın paylaştığı konteyner.
///
/// Extension ayrı bir process'te çalışır ve ana uygulamanın sandbox'ına erişemez.
/// Dönüştürdüğü belgeyi buradaki "gelen kutusu"na bırakır; ana uygulama açıldığında
/// `ConversionStorage.importPendingSharedRecords()` bu belgeleri kendi klasörüne taşır
/// ve geçmişe ekler.
enum AppGroup {
    /// Her iki target'ın Signing & Capabilities > App Groups bölümünde tanımlı olmalı.
    static let identifier = "group.com.velikececi.UDFDonusturucu"

    static let pendingRecordsKey = "pendingShareExtensionRecords"

    static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }

    static var defaults: UserDefaults? {
        UserDefaults(suiteName: identifier)
    }

    /// Extension'ın çıktıları bıraktığı klasör. Yoksa oluşturulur.
    static func inboxDirectory() -> URL? {
        guard let containerURL else { return nil }
        let inbox = containerURL.appendingPathComponent("ShareInbox", isDirectory: true)
        if !FileManager.default.fileExists(atPath: inbox.path) {
            try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        }
        return inbox
    }

    /// Extension'ın ürettiği ve ana uygulamanın henüz devralmadığı belge.
    struct PendingRecord: Codable {
        let originalFileName: String
        let outputFormat: String
        let inboxFileName: String
        let date: Date
    }

    static func loadPendingRecords() -> [PendingRecord] {
        guard let data = defaults?.data(forKey: pendingRecordsKey),
              let decoded = try? JSONDecoder().decode([PendingRecord].self, from: data) else { return [] }
        return decoded
    }

    static func savePendingRecords(_ records: [PendingRecord]) {
        guard let defaults else { return }
        if records.isEmpty {
            defaults.removeObject(forKey: pendingRecordsKey)
        } else if let data = try? JSONEncoder().encode(records) {
            defaults.set(data, forKey: pendingRecordsKey)
        }
    }

    static func appendPendingRecord(_ record: PendingRecord) {
        var records = loadPendingRecords()
        records.append(record)
        savePendingRecords(records)
    }
}
