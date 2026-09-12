import SwiftUI
import UniformTypeIdentifiers

private struct CompressionDisplayResult {
    let outputURL: URL
    let originalBytes: Int64
    let compressedBytes: Int64
    let preservedText: Bool
}

struct CompressView: View {
    @State private var selectedFile: URL?
    @State private var quality: PDFToolsService.CompressionQuality = .lossless
    @State private var showPicker = false
    @State private var isWorking = false
    @State private var result: CompressionDisplayResult?
    @State private var errorMessage: String?
    @State private var shareURL: URL?
    @State private var previewURL: URL?

    private var isUDF: Bool {
        selectedFile?.pathExtension.lowercased() == "udf"
    }

    var body: some View {
        List {
            Section {
                Button { showPicker = true } label: {
                    Label(selectedFile?.lastPathComponent ?? "PDF veya UDF Seç", systemImage: isUDF ? "doc.fill" : "doc.richtext.fill")
                        .lineLimit(1)
                }
            } footer: {
                Text(isUDF
                     ? "UDF arşivindeki XML ve diğer girdiler yeniden sıkıştırılır; belge yapısı korunur."
                     : "Kayıpsız mod metin aramasını korur ve gömülü görselleri optimize eder. Dengeli ve maksimum modlar sayfaları görüntüye çevirir; dosya belirgin küçülür ancak metin araması kaybolur.")
            }

            if selectedFile != nil && !isUDF {
                Section("PDF Sıkıştırma Düzeyi") {
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
            }

            if selectedFile != nil {
                Section {
                    Button { compress() } label: {
                        if isWorking {
                            HStack {
                                ProgressView()
                                Text(isUDF ? "UDF sıkıştırılıyor..." : "PDF sıkıştırılıyor...")
                            }
                        } else {
                            Label(isUDF ? "UDF'yi Sıkıştır" : "PDF'yi Sıkıştır", systemImage: "arrow.down.right.and.arrow.up.left")
                                .bold()
                        }
                    }
                    .disabled(isWorking)
                }
            }

            if let result {
                Section("Sonuç") {
                    fileSizeRow(title: "Önce", bytes: result.originalBytes)
                    fileSizeRow(title: "Sonra", bytes: result.compressedBytes)

                    if result.originalBytes > 0 {
                        let ratio = 100 - Int(Double(result.compressedBytes) / Double(result.originalBytes) * 100)
                        Text(ratio > 0 ? "%\(ratio) küçüldü" : "Bu dosya daha fazla küçültülemedi.")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        if result.preservedText && ratio < 5 {
                            Text("Bu belge kayıpsız yöntemle daha fazla küçültülemiyor. Taranmış sayfalardan oluşuyorsa \"Dengeli\" modu belirgin kazanç sağlar — ancak metin araması kaybolur.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    if result.preservedText {
                        Label("Metin araması korundu", systemImage: "checkmark.seal.fill")
                            .font(.caption)
                            .foregroundStyle(.green)
                    } else {
                        Label("Metin araması kaldırıldı", systemImage: "exclamationmark.triangle.fill")
                            .font(.caption)
                            .foregroundStyle(.orange)
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
        .navigationTitle("PDF / UDF Sıkıştırma")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.pdf] + UTType.udfPickerTypes) { urls in
                if let first = urls.first {
                    let ext = first.pathExtension.lowercased()
                    if ext == "pdf" || ext == "udf" {
                        selectedFile = first
                        result = nil
                        errorMessage = nil
                    } else {
                        errorMessage = "Lütfen bir PDF veya UDF dosyası seçin."
                    }
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

    private func fileSizeRow(title: String, bytes: Int64) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file))
                .foregroundStyle(.secondary)
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
                let displayResult: CompressionDisplayResult
                if file.pathExtension.lowercased() == "udf" {
                    let output = try UDFToolsService.compress(url: file)
                    displayResult = CompressionDisplayResult(
                        outputURL: output.outputURL,
                        originalBytes: output.originalBytes,
                        compressedBytes: output.compressedBytes,
                        preservedText: true
                    )
                } else {
                    let output = try PDFToolsService.compress(url: file, quality: selectedQuality)
                    displayResult = CompressionDisplayResult(
                        outputURL: output.outputURL,
                        originalBytes: output.originalBytes,
                        compressedBytes: output.compressedBytes,
                        preservedText: output.preservedText
                    )
                }

                await MainActor.run {
                    result = displayResult
                    isWorking = false
                    ConversionStorage.shared.addRecord(
                        ConversionRecord(
                            originalFileName: displayResult.outputURL.lastPathComponent,
                            outputFormat: displayResult.outputURL.pathExtension.uppercased(),
                            success: true,
                            outputPath: displayResult.outputURL.path
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
