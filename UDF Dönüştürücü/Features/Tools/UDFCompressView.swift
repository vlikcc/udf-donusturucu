import SwiftUI
import UniformTypeIdentifiers

struct UDFCompressView: View {
    @State private var selectedFile: URL?
    @State private var showPicker = false
    @State private var isWorking = false
    @State private var result: UDFCompressionResult?
    @State private var errorMessage: String?
    @State private var shareURL: URL?

    var body: some View {
        List {
            Section {
                Button { showPicker = true } label: {
                    Label(selectedFile?.lastPathComponent ?? "UDF Seç", systemImage: "doc.fill")
                        .lineLimit(1)
                }
            } footer: {
                Text("UDF içindeki XML ve diğer arşiv girdileri yeniden deflate edilir. Belgenin içeriği ve UYAP yapısı korunur.")
            }

            if selectedFile != nil {
                Section {
                    Button { compress() } label: {
                        if isWorking {
                            HStack {
                                ProgressView()
                                Text("UDF sıkıştırılıyor...")
                            }
                        } else {
                            Label("UDF'yi Sıkıştır", systemImage: "arrow.down.right.and.arrow.up.left")
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
                        let saved = 100 - Int(Double(result.compressedBytes) / Double(result.originalBytes) * 100)
                        Text(saved > 0 ? "%\(saved) küçüldü" : "Bu UDF daha fazla küçültülemedi.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Label(result.outputURL.lastPathComponent, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.subheadline)
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
        .navigationTitle("UDF Sıkıştırma")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: UTType.udfPickerTypes) { urls in
                if let url = urls.first(where: { $0.pathExtension.lowercased() == "udf" }) {
                    selectedFile = url
                    result = nil
                    errorMessage = nil
                } else if !urls.isEmpty {
                    errorMessage = "Lütfen bir .udf dosyası seçin."
                }
                showPicker = false
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
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

        Task {
            do {
                let output = try UDFToolsService.compress(url: file)
                await MainActor.run {
                    result = output
                    isWorking = false
                    ConversionStorage.shared.addRecord(
                        ConversionRecord(
                            originalFileName: output.outputURL.lastPathComponent,
                            outputFormat: "UDF",
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
