import SwiftUI
import UniformTypeIdentifiers

struct HistoryView: View {
    @ObservedObject var storage = ConversionStorage.shared
    @ObservedObject var limitService = LimitService.shared

    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var showExporter = false
    @State private var exportData: Data?
    @State private var exportFileName = ""
    @State private var exportUTType: UTType = .pdf
    @State private var confirmDeleteRecord: ConversionRecord?
    @State private var showPaywall = false

    var body: some View {
        List {
            if !limitService.isPremium {
                Section {
                    Button {
                        showPaywall = true
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "crown.fill")
                                .foregroundStyle(.orange)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Pro ile geçmişiniz 30 gün saklanır")
                                    .font(.subheadline).bold()
                                    .foregroundStyle(.primary)
                                Text("Ücretsiz sürümde geçmiş 7 gün sonra silinir.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
            }

            if storage.recentRecords.isEmpty {
                ContentUnavailableView(
                    "Henüz dönüşüm yok",
                    systemImage: "clock.arrow.circlepath",
                    description: Text("Dönüştürdüğünüz dosyalar burada görünecek.")
                )
            } else {
                // Available files section
                let available = storage.availableRecords
                if !available.isEmpty {
                    Section {
                        ForEach(available) { record in
                            historyRow(record)
                        }
                        .onDelete { indexSet in
                            for index in indexSet {
                                storage.deleteRecord(available[index])
                            }
                        }
                    } header: {
                        Text("Dosyalar (\(available.count))")
                    }
                }

                // Failed / missing files section
                let unavailable = storage.recentRecords.filter { !$0.success || !$0.fileExists }
                if !unavailable.isEmpty {
                    Section {
                        ForEach(unavailable) { record in
                            historyRowUnavailable(record)
                        }
                        .onDelete { indexSet in
                            for index in indexSet {
                                storage.deleteRecord(unavailable[index])
                            }
                        }
                    } header: {
                        Text("Diğer")
                    }
                }
            }
        }
        .navigationTitle("Geçmiş")
        .toolbar {
            if !storage.recentRecords.isEmpty {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Temizle", role: .destructive) {
                        storage.clearHistory()
                    }
                }
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
        .sheet(isPresented: $showPaywall) {
            PaywallView(source: "history")
        }
        .navigationDestination(item: $previewURL) { url in
            DocumentPreviewView(url: url)
        }
        .fileExporter(
            isPresented: $showExporter,
            document: ExportFileDocument(data: exportData ?? Data()),
            contentType: exportUTType,
            defaultFilename: exportFileName
        ) { _ in }
    }

    // MARK: - Available File Row

    private func historyRow(_ record: ConversionRecord) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                formatIcon(record.outputFormat)
                    .frame(width: 40, height: 40)
                    .background(formatColor(record.outputFormat).opacity(0.1), in: RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 2) {
                    Text(record.originalFileName)
                        .font(.subheadline).bold()
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        Text(record.outputFormat)
                            .font(.caption2).bold()
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(formatColor(record.outputFormat).opacity(0.15), in: Capsule())
                            .foregroundStyle(formatColor(record.outputFormat))

                        Text(record.date, format: .dateTime.month(.abbreviated).day().hour().minute())
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()
            }

            // Action buttons
            HStack(spacing: 12) {
                DocumentActionButton(title: "Görüntüle", systemImage: "eye") {
                    if let url = record.resolvedURL {
                        previewURL = url
                    }
                }

                DocumentActionButton(title: "Paylaş", systemImage: "square.and.arrow.up") {
                    if let url = record.resolvedURL {
                        shareURL = url
                    }
                }

                DocumentActionButton(title: "Kaydet", systemImage: "folder.badge.plus", tint: .green) {
                    saveToFiles(record: record)
                }
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Unavailable File Row

    private func historyRowUnavailable(_ record: ConversionRecord) -> some View {
        HStack(spacing: 12) {
            Image(systemName: record.success ? "exclamationmark.triangle.fill" : "xmark.circle.fill")
                .foregroundStyle(record.success ? .orange : .red)

            VStack(alignment: .leading, spacing: 2) {
                Text(record.originalFileName)
                    .font(.subheadline)
                    .lineLimit(1)
                HStack(spacing: 8) {
                    Text(record.outputFormat)
                        .font(.caption)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.gray.opacity(0.1), in: Capsule())
                    Text(record.date, style: .relative)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            if record.success {
                Text("Dosya silinmiş")
                    .font(.caption2)
                    .foregroundStyle(.orange)
            } else {
                Text("Başarısız")
                    .font(.caption2)
                    .foregroundStyle(.red)
            }
        }
    }

    // MARK: - Helpers

    private func saveToFiles(record: ConversionRecord) {
        guard let url = record.resolvedURL,
              let data = try? Data(contentsOf: url) else { return }
        exportData = data
        exportFileName = url.lastPathComponent
        switch record.outputFormat.uppercased() {
        case "PDF":
            exportUTType = .pdf
        case "DOCX":
            exportUTType = UTType(filenameExtension: "docx") ?? .data
        case "UDF":
            exportUTType = UTType(filenameExtension: "udf") ?? .data
        default:
            exportUTType = .data
        }
        showExporter = true
    }

    private func formatIcon(_ format: String) -> some View {
        let icon: String
        let color: Color
        switch format.uppercased() {
        case "PDF": icon = "doc.richtext.fill"; color = .red
        case "DOCX": icon = "doc.text.fill"; color = .blue
        case "UDF": icon = "doc.fill"; color = AppTheme.navy
        default: icon = "doc.fill"; color = .gray
        }
        return Image(systemName: icon)
            .font(.title3)
            .foregroundStyle(color)
    }

    private func formatColor(_ format: String) -> Color {
        switch format.uppercased() {
        case "PDF": return .red
        case "DOCX": return .blue
        case "UDF": return AppTheme.navy
        default: return .gray
        }
    }
}
