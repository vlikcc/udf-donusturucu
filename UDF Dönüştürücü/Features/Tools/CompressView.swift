import SwiftUI
import UniformTypeIdentifiers

struct CompressView: View {
    @State private var selectedFile: URL?
    @State private var quality: PDFToolsService.CompressionQuality = .balanced
    @State private var showPicker = false
    @State private var isWorking = false
    @State private var result: PDFToolsService.CompressionResult?
    @State private var errorMessage: String?
    @State private var shareURL: URL?
    @State private var previewURL: URL?

    var body: some View {
        List {
            Section {
                Button {
                    showPicker = true
                } label: {
                    Label(selectedFile?.lastPathComponent ?? "PDF Seç", systemImage: "doc.richtext.fill")
                        .lineLimit(1)
                }
            } footer: {
                Text("Sayfalar sıkıştırılmış görüntüye dönüştürülür; metin seçimi kaybolur. Taranmış veya çok büyük PDF'ler için uygundur.")
            }

            if selectedFile != nil {
                Section("Sıkıştırma Düzeyi") {
                    ForEach(PDFToolsService.CompressionQuality.allCases) { option in
                        Button {
                            quality = option
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(option.title)
                                        .font(.subheadline).bold()
                                        .foregroundStyle(.primary)
                                    Text(option.subtitle)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: quality == option ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(quality == option ? AppTheme.navy : .secondary)
                            }
                        }
                    }
                }

                Section {
                    Button {
                        compress()
                    } label: {
                        if isWorking {
                            HStack {
                                ProgressView()
                                Text("Sıkıştırılıyor...")
                            }
                        } else {
                            Label("Sıkıştır", systemImage: "arrow.down.right.and.arrow.up.left")
                                .bold()
                        }
                    }
                    .disabled(isWorking)
                }
            }

            if let result {
                Section("Sonuç") {
                    HStack {
                        Text("Önce")
                        Spacer()
                        Text(ByteCountFormatter.string(fromByteCount: result.originalBytes, countStyle: .file))
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("Sonra")
                        Spacer()
                        Text(ByteCountFormatter.string(fromByteCount: result.compressedBytes, countStyle: .file))
                            .foregroundStyle(result.compressedBytes < result.originalBytes ? .green : .orange)
                            .bold()
                    }
                    if result.originalBytes > 0 {
                        let ratio = 100 - Int(Double(result.compressedBytes) / Double(result.originalBytes) * 100)
                        Text(ratio > 0 ? "%\(ratio) küçüldü" : "Bu dosya daha fazla küçültülemedi.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Button { previewURL = result.outputURL } label: {
                        Label("Görüntüle", systemImage: "eye")
                    }
                    Button { shareURL = result.outputURL } label: {
                        Label("Paylaş", systemImage: "square.and.arrow.up")
                    }
                }
            }

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("PDF Sıkıştırma")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.pdf]) { urls in
                if let first = urls.first {
                    selectedFile = first
                    result = nil
                    errorMessage = nil
                }
                showPicker = false
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
        .navigationDestination(item: $previewURL) { url in
            DocumentPreviewView(url: url)
        }
    }

    private func compress() {
        guard let file = selectedFile else { return }
        isWorking = true
        errorMessage = nil
        result = nil
        let selectedQuality = quality

        Task {
            do {
                let output = try PDFToolsService.compress(url: file, quality: selectedQuality)
                await MainActor.run {
                    result = output
                    isWorking = false
                    ConversionStorage.shared.addRecord(
                        ConversionRecord(
                            originalFileName: output.outputURL.lastPathComponent,
                            outputFormat: "PDF",
                            success: true,
                            outputPath: output.outputURL.path
                        )
                    )
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isWorking = false
                }
            }
        }
    }
}
