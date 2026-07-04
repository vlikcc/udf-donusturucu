import Foundation
import Combine

struct ConversionRecord: Identifiable, Codable {
    let id: UUID
    let originalFileName: String
    let outputFormat: String
    let date: Date
    let success: Bool
    let outputPath: String?      // Legacy: full path (may break across launches)
    let outputFileName: String?  // New: just the file name, resolved at runtime

    init(originalFileName: String, outputFormat: String, success: Bool, outputPath: String? = nil) {
        self.id = UUID()
        self.originalFileName = originalFileName
        self.outputFormat = outputFormat
        self.date = Date()
        self.success = success
        self.outputPath = outputPath
        // Extract just the filename for reliable resolution
        if let path = outputPath {
            self.outputFileName = URL(fileURLWithPath: path).lastPathComponent
        } else {
            self.outputFileName = nil
        }
    }

    /// Resolves the actual file URL at runtime (handles sandbox UUID changes)
    var resolvedURL: URL? {
        // Try the stored filename first (reliable across launches)
        if let fileName = outputFileName {
            let url = ConversionStorage.outputDirectory.appendingPathComponent(fileName)
            if FileManager.default.fileExists(atPath: url.path) {
                return url
            }
        }

        // Fallback: try the full stored path (works within same launch)
        if let path = outputPath, FileManager.default.fileExists(atPath: path) {
            return URL(fileURLWithPath: path)
        }

        return nil
    }

    var fileExists: Bool {
        resolvedURL != nil
    }
}

final class ConversionStorage: ObservableObject {
    static let shared = ConversionStorage()

    static var outputDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("ConvertedFiles")
    }

    private let storageKey = "conversionHistory"
    @Published var records: [ConversionRecord] = []

    private init() {
        loadRecords()
    }

    func addRecord(_ record: ConversionRecord) {
        records.insert(record, at: 0)
        saveRecords()
    }

    func clearHistory() {
        records.removeAll()
        saveRecords()
    }

    func deleteRecord(_ record: ConversionRecord) {
        // Delete the file too
        if let url = record.resolvedURL {
            try? FileManager.default.removeItem(at: url)
        }
        records.removeAll { $0.id == record.id }
        saveRecords()
    }

    var recentRecords: [ConversionRecord] {
        let cutoff: TimeInterval
        if LimitService.shared.isPremium {
            cutoff = 30 * 24 * 3600
        } else {
            cutoff = 7 * 24 * 3600
        }
        let earliest = Date().addingTimeInterval(-cutoff)
        return records.filter { $0.date >= earliest }
    }

    /// Only successful records with existing files
    var availableRecords: [ConversionRecord] {
        recentRecords.filter { $0.success && $0.fileExists }
    }

    private func loadRecords() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([ConversionRecord].self, from: data) else {
            return
        }
        records = decoded
    }

    private func saveRecords() {
        if records.count > 200 {
            records = Array(records.prefix(200))
        }
        if let data = try? JSONEncoder().encode(records) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }
}
