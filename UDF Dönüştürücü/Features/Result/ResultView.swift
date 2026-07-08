import SwiftUI
import UniformTypeIdentifiers

struct ResultView: View {
    let results: [ConversionResult]

    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var showExporter = false
    @State private var exportData: Data?
    @State private var exportFileName = ""
    @State private var exportUTType: UTType = .pdf

    var successCount: Int { results.filter(\.success).count }
    var failCount: Int { results.filter { !$0.success }.count }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                summaryHeader

                ForEach(results) { result in
                    resultRow(result)
                }

                if successCount > 0 {
                    shareAllButton
                }
            }
            .padding()
        }
        .navigationTitle("Sonuçlar")
        .navigationBarBackButtonHidden(false)
        .onAppear {
            // Başarılı bir dönüştürme sonrası interstitial reklam göster (her N'de bir).
            if successCount > 0, let vc = UIApplication.topViewController() {
                AdsManager.shared.showInterstitialIfReady(from: vc)
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
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

    private var summaryHeader: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill((failCount == 0 ? Color.green : Color.orange).opacity(0.15))
                    .frame(width: 90, height: 90)
                Image(systemName: failCount == 0 ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .font(.system(size: 42))
                    .foregroundStyle(failCount == 0 ? .green : .orange)
            }

            Text(failCount == 0 ? "Tüm dosyalar başarıyla dönüştürüldü!" : "\(successCount) başarılı, \(failCount) başarısız")
                .font(.headline)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private func resultRow(_ result: ConversionResult) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: result.success ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundStyle(result.success ? .green : .red)

                VStack(alignment: .leading, spacing: 2) {
                    Text(result.fileName)
                        .font(.subheadline).bold()
                        .lineLimit(1)
                    Text(result.format.rawValue)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()
            }

            if result.success, let url = result.outputURL {
                HStack(spacing: 12) {
                    DocumentActionButton(title: "Görüntüle", systemImage: "eye") {
                        previewURL = url
                    }

                    DocumentActionButton(title: "Paylaş", systemImage: "square.and.arrow.up") {
                        shareURL = url
                    }

                    DocumentActionButton(title: "Kaydet", systemImage: "folder") {
                        saveToFiles(url: url, format: result.format)
                    }
                }
            }

            if let error = result.error {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private var shareAllButton: some View {
        Button {
            let urls = results.compactMap(\.outputURL)
            if let first = urls.first {
                shareURL = first
            }
        } label: {
            Label("Tümünü Paylaş", systemImage: "square.and.arrow.up.on.square")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding()
                .background(
                    LinearGradient(
                        colors: [AppTheme.navy, AppTheme.navy.opacity(0.8)],
                        startPoint: .leading,
                        endPoint: .trailing
                    ),
                    in: RoundedRectangle(cornerRadius: 12)
                )
                .foregroundStyle(.white)
        }
        .shadow(color: AppTheme.navy.opacity(0.3), radius: 10, y: 5)
    }

    private func saveToFiles(url: URL, format: OutputFormat) {
        guard let data = try? Data(contentsOf: url) else { return }
        exportData = data
        exportFileName = url.lastPathComponent
        exportUTType = format == .pdf ? .pdf : UTType(filenameExtension: "docx") ?? .data
        showExporter = true
    }
}

// MARK: - URL+Identifiable for sheet

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

// MARK: - File Document for Export

struct ExportFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.pdf, .data] }

    let data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

// MARK: - UIKit Share Sheet Wrapper

struct ActivityViewController: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
