import Foundation
import SwiftUI
import Combine

/// Bir dava veya iş dosyası. Dönüştürülen belgeler bu dosyalara etiketlenerek gruplanır.
struct CaseFile: Identifiable, Codable, Hashable {
    let id: UUID
    var title: String
    var caseNumber: String?
    let createdAt: Date
    var colorIndex: Int

    init(id: UUID = UUID(), title: String, caseNumber: String? = nil, colorIndex: Int = 0) {
        self.id = id
        self.title = title
        self.caseNumber = CaseFile.normalize(caseNumber)
        self.createdAt = Date()
        self.colorIndex = colorIndex
    }

    /// Boş veya yalnızca boşluktan oluşan esas numarasını `nil`'e indirger.
    static func normalize(_ text: String?) -> String? {
        guard let trimmed = text?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else { return nil }
        return trimmed
    }

    static let palette: [Color] = [.blue, .green, .orange, .purple, .pink, .teal, .indigo, .brown]

    var color: Color {
        CaseFile.palette[abs(colorIndex) % CaseFile.palette.count]
    }

    /// Listelerde ve rozetlerde kullanılan tek satırlık etiket: "2026/1234 · Ahmet Yılmaz".
    var displayLabel: String {
        guard let caseNumber else { return title }
        return "\(caseNumber) · \(title)"
    }
}

/// Dava dosyalarını saklar. `ConversionStorage` ile aynı yaklaşım: UserDefaults + Codable.
final class CaseFileStore: ObservableObject {
    static let shared = CaseFileStore()

    private let storageKey = "caseFiles"

    @Published var cases: [CaseFile] = []

    private init() {
        load()
    }

    @discardableResult
    func add(title: String, caseNumber: String? = nil) -> CaseFile? {
        guard let title = CaseFile.normalize(title) else { return nil }
        let file = CaseFile(title: title, caseNumber: caseNumber, colorIndex: nextColorIndex())
        cases.insert(file, at: 0)
        save()
        return file
    }

    func update(_ file: CaseFile, title: String, caseNumber: String?) {
        guard let title = CaseFile.normalize(title),
              let index = cases.firstIndex(where: { $0.id == file.id }) else { return }
        cases[index].title = title
        cases[index].caseNumber = CaseFile.normalize(caseNumber)
        save()
    }

    /// Dosyayı siler. İçindeki belgeler silinmez — yalnızca etiketleri kaldırılır ve
    /// belgeler genel geçmişte kalmaya devam eder.
    func delete(_ file: CaseFile) {
        cases.removeAll { $0.id == file.id }
        save()
        ConversionStorage.shared.unassignAll(from: file.id)
    }

    func caseFile(id: UUID?) -> CaseFile? {
        guard let id else { return nil }
        return cases.first { $0.id == id }
    }

    /// Yeni dosyaya, listedeki son dosyadan farklı bir renk verir.
    private func nextColorIndex() -> Int {
        (cases.first.map { $0.colorIndex + 1 } ?? 0) % CaseFile.palette.count
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([CaseFile].self, from: data) else { return }
        cases = decoded
    }

    private func save() {
        if let data = try? JSONEncoder().encode(cases) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }
}
