import SwiftUI
import UniformTypeIdentifiers

// MARK: - Dosya listesi

struct CaseFilesView: View {
    @ObservedObject private var caseStore = CaseFileStore.shared
    @ObservedObject private var storage = ConversionStorage.shared

    @State private var editingFile: CaseFile?
    @State private var showNewSheet = false
    @State private var confirmDelete: CaseFile?

    var body: some View {
        List {
            if caseStore.cases.isEmpty {
                ContentUnavailableView {
                    Label("Henüz dava dosyası yok", systemImage: "folder.badge.plus")
                } description: {
                    Text("Belgelerinizi dava veya iş bazında gruplayın. Dosyaya eklenen belgeler geçmiş süresinden etkilenmez, kalıcı olarak saklanır.")
                } actions: {
                    Button("Dosya Oluştur") { showNewSheet = true }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                Section {
                    ForEach(caseStore.cases) { file in
                        NavigationLink {
                            CaseFileDetailView(file: file)
                        } label: {
                            row(for: file)
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                confirmDelete = file
                            } label: {
                                Label("Sil", systemImage: "trash")
                            }

                            Button {
                                editingFile = file
                            } label: {
                                Label("Düzenle", systemImage: "pencil")
                            }
                            .tint(.orange)
                        }
                    }
                } footer: {
                    Text("Dosyaya eklenen belgeler 7/30 günlük geçmiş süresinden etkilenmez.")
                }
            }
        }
        .navigationTitle("Dava Dosyaları")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showNewSheet = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Yeni dosya")
            }
        }
        .sheet(isPresented: $showNewSheet) {
            CaseFileEditor()
        }
        .sheet(item: $editingFile) { file in
            CaseFileEditor(existing: file)
        }
        .confirmationDialog(
            "\"\(confirmDelete?.title ?? "")\" dosyası silinsin mi?",
            isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Dosyayı Sil", role: .destructive) {
                if let file = confirmDelete { caseStore.delete(file) }
                confirmDelete = nil
            }
            Button("Vazgeç", role: .cancel) { confirmDelete = nil }
        } message: {
            Text("Belgeleriniz silinmez, yalnızca bu dosyadan çıkarılır ve genel geçmişte kalmaya devam eder.")
        }
    }

    private func row(for file: CaseFile) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "folder.fill")
                .font(.title3)
                .foregroundStyle(file.color)
                .frame(width: 38, height: 38)
                .background(file.color.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text(file.title)
                    .font(.subheadline).bold()
                    .lineLimit(1)

                HStack(spacing: 6) {
                    if let caseNumber = file.caseNumber {
                        Text(caseNumber)
                            .font(.caption2).bold()
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(file.color.opacity(0.15), in: Capsule())
                            .foregroundStyle(file.color)
                    }

                    Text("\(storage.documentCount(inCase: file.id)) belge")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Dosya içeriği

struct CaseFileDetailView: View {
    let file: CaseFile

    @ObservedObject private var storage = ConversionStorage.shared

    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var exporter = RecordExporter()

    private var records: [ConversionRecord] {
        storage.records(inCase: file.id).filter { $0.success && $0.fileExists }
    }

    var body: some View {
        List {
            if records.isEmpty {
                ContentUnavailableView(
                    "Bu dosyada belge yok",
                    systemImage: "doc.badge.plus",
                    description: Text("Geçmiş ekranındaki bir belgeyi sola kaydırıp \"Dosya\" ile buraya ekleyebilirsiniz.")
                )
            } else {
                Section("Belgeler (\(records.count))") {
                    ForEach(records) { record in
                        ConversionRecordRow(
                            record: record,
                            onPreview: { previewURL = $0 },
                            onShare: { shareURL = $0 },
                            onSave: { exporter.prepare(for: $0) },
                            showsCaseBadge: false
                        )
                        .swipeActions(edge: .trailing) {
                            Button {
                                storage.assign(record, to: nil)
                            } label: {
                                Label("Çıkar", systemImage: "folder.badge.minus")
                            }
                            .tint(.orange)
                        }
                    }
                }
            }
        }
        .navigationTitle(file.title)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
        .navigationDestination(item: $previewURL) { url in
            DocumentPreviewView(url: url)
        }
        .fileExporter(
            isPresented: $exporter.isPresented,
            document: ExportFileDocument(data: exporter.data),
            contentType: exporter.contentType,
            defaultFilename: exporter.fileName
        ) { _ in }
    }
}

// MARK: - Dosya oluştur / düzenle

struct CaseFileEditor: View {
    var existing: CaseFile?

    @ObservedObject private var caseStore = CaseFileStore.shared
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var caseNumber = ""

    private var canSave: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Dosya adı (ör. Ahmet Yılmaz — Boşanma)", text: $title)
                        .autocorrectionDisabled()
                    TextField("Esas no (ör. 2026/1234) — isteğe bağlı", text: $caseNumber)
                        .autocorrectionDisabled()
                } footer: {
                    Text("Esas numarası girerseniz belge listelerinde dosya adının önünde gösterilir.")
                }
            }
            .navigationTitle(existing == nil ? "Yeni Dava Dosyası" : "Dosyayı Düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { save() }
                        .disabled(!canSave)
                }
            }
            .onAppear {
                if let existing {
                    title = existing.title
                    caseNumber = existing.caseNumber ?? ""
                }
            }
        }
    }

    private func save() {
        if let existing {
            caseStore.update(existing, title: title, caseNumber: caseNumber)
        } else {
            caseStore.add(title: title, caseNumber: caseNumber)
        }
        dismiss()
    }
}

// MARK: - Belgeyi dosyaya atama

struct CasePickerView: View {
    let record: ConversionRecord

    @ObservedObject private var caseStore = CaseFileStore.shared
    @ObservedObject private var storage = ConversionStorage.shared
    @Environment(\.dismiss) private var dismiss

    @State private var showNewSheet = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        showNewSheet = true
                    } label: {
                        Label("Yeni Dava Dosyası", systemImage: "folder.badge.plus")
                    }
                }

                if !caseStore.cases.isEmpty {
                    Section("Dosyalar") {
                        ForEach(caseStore.cases) { file in
                            Button {
                                storage.assign(record, to: file.id)
                                dismiss()
                            } label: {
                                HStack {
                                    Image(systemName: "folder.fill")
                                        .foregroundStyle(file.color)
                                    Text(file.displayLabel)
                                        .foregroundStyle(.primary)
                                        .lineLimit(1)
                                    Spacer()
                                    if record.caseFileID == file.id {
                                        Image(systemName: "checkmark")
                                            .foregroundStyle(AppTheme.navy)
                                    }
                                }
                            }
                        }
                    }
                }

                if record.caseFileID != nil {
                    Section {
                        Button(role: .destructive) {
                            storage.assign(record, to: nil)
                            dismiss()
                        } label: {
                            Label("Dosyadan Çıkar", systemImage: "folder.badge.minus")
                        }
                    }
                }
            }
            .navigationTitle("Dosyaya Ekle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
            }
            .sheet(isPresented: $showNewSheet) {
                CaseFileEditor()
            }
        }
    }
}

// MARK: - "Dosyalar'a Kaydet" için paylaşılan durum

/// `fileExporter` için gereken üç durumu tek yerde toplar; hem geçmiş hem dava
/// dosyası ekranı aynı davranışı kullanır.
@Observable
final class RecordExporter {
    var isPresented = false
    var data = Data()
    var fileName = ""
    var contentType: UTType = .pdf

    func prepare(for record: ConversionRecord) {
        guard let url = record.resolvedURL,
              let fileData = try? Data(contentsOf: url) else { return }
        data = fileData
        fileName = url.lastPathComponent
        contentType = Self.contentType(for: record.outputFormat)
        isPresented = true
    }

    static func contentType(for format: String) -> UTType {
        switch format.uppercased() {
        case "PDF": return .pdf
        case "DOCX": return UTType(filenameExtension: "docx") ?? .data
        case "UDF": return UTType(filenameExtension: "udf") ?? .data
        default: return .data
        }
    }
}
