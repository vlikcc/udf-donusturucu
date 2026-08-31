import SwiftUI
import UniformTypeIdentifiers

struct HistoryView: View {
    @ObservedObject var storage = ConversionStorage.shared
    @ObservedObject var limitService = LimitService.shared
    @ObservedObject private var caseStore = CaseFileStore.shared

    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var exporter = RecordExporter()
    @State private var assigningRecord: ConversionRecord?
    @State private var showPaywall = false
    @State private var paywallSource = "history"

    var body: some View {
        List {
            if !limitService.isPremium {
                Section {
                    Button {
                        paywallSource = "history"
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

            caseFilesSection

            if storage.recentRecords.isEmpty {
                ContentUnavailableView(
                    "Henüz dönüşüm yok",
                    systemImage: "clock.arrow.circlepath",
                    description: Text("Dönüştürdüğünüz dosyalar burada görünecek.")
                )
            } else {
                let available = storage.availableRecords
                if !available.isEmpty {
                    Section {
                        ForEach(available) { record in
                            ConversionRecordRow(
                                record: record,
                                onPreview: { previewURL = $0 },
                                onShare: { shareURL = $0 },
                                onSave: { exporter.prepare(for: $0) }
                            )
                            .swipeActions(edge: .leading) {
                                Button {
                                    if limitService.isPremium {
                                        assigningRecord = record
                                    } else {
                                        paywallSource = "history_case"
                                        showPaywall = true
                                    }
                                } label: {
                                    Label("Dosya", systemImage: "folder.badge.plus")
                                }
                                .tint(AppTheme.navy)
                            }
                        }
                        .onDelete { indexSet in
                            for index in indexSet {
                                storage.deleteRecord(available[index])
                            }
                        }
                    } header: {
                        Text("Dosyalar (\(available.count))")
                    } footer: {
                        if limitService.isPremium {
                            Text("Bir belgeyi sağa kaydırıp dava dosyasına ekleyebilirsiniz.")
                        }
                    }
                }

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
            PaywallView(source: paywallSource)
        }
        .sheet(item: $assigningRecord) { record in
            CasePickerView(record: record)
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

    // MARK: - Dava dosyaları girişi

    @ViewBuilder
    private var caseFilesSection: some View {
        Section {
            if limitService.isPremium {
                NavigationLink {
                    CaseFilesView()
                } label: {
                    caseFilesLabel(detail: "\(caseStore.cases.count) dosya")
                }
            } else {
                Button {
                    paywallSource = "history_case"
                    showPaywall = true
                } label: {
                    HStack {
                        caseFilesLabel(detail: "Pro özelliği")
                        Spacer()
                        Image(systemName: "lock.fill")
                            .font(.subheadline)
                            .foregroundStyle(.orange)
                    }
                }
            }
        } footer: {
            Text("Belgelerinizi dava veya iş bazında gruplayın. Dosyaya eklenen belgeler geçmiş süresinden etkilenmez.")
        }
    }

    private func caseFilesLabel(detail: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "folder.fill")
                .font(.title3)
                .foregroundStyle(AppTheme.navy)
                .frame(width: 38, height: 38)
                .background(AppTheme.navy.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text("Dava Dosyaları")
                    .font(.subheadline).bold()
                    .foregroundStyle(.primary)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Kullanılamayan belge satırı

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
}
