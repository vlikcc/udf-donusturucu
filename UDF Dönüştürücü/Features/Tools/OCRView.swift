import SwiftUI
import UniformTypeIdentifiers

struct OCRView: View {
    @State private var selectedFile: URL?
    @State private var showPicker = false
    @State private var isWorking = false
    @State private var progressText = ""
    @State private var recognizedText: String?
    @State private var errorMessage: String?
    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var exportMessage: String?

    var body: some View {
        List {
            Section {
                Button {
                    showPicker = true
                } label: {
                    Label(selectedFile?.lastPathComponent ?? "PDF veya Görüntü Seç", systemImage: "text.viewfinder")
                        .lineLimit(1)
                }
            } footer: {
                Text("Metin tanıma tamamen cihazınızda çalışır; belgeleriniz hiçbir sunucuya gönderilmez.")
            }

            if selectedFile != nil && recognizedText == nil {
                Section {
                    Button {
                        runOCR()
                    } label: {
                        if isWorking {
                            HStack {
                                ProgressView()
                                Text(progressText.isEmpty ? "Metin tanınıyor..." : progressText)
                            }
                        } else {
                            Label("Metni Tanı", systemImage: "sparkles")
                                .bold()
                        }
                    }
                    .disabled(isWorking)
                }
            }

            if let recognizedText {
                Section("Tanınan Metin") {
                    Text(recognizedText)
                        .font(.caption)
                        .lineLimit(12)
                        .textSelection(.enabled)
                }

                Section("Dışa Aktar") {
                    Button { export(format: "DOCX") } label: {
                        Label("Word (DOCX) Olarak Kaydet", systemImage: "doc.text.fill")
                    }
                    Button { export(format: "PDF") } label: {
                        Label("PDF Olarak Kaydet", systemImage: "doc.richtext.fill")
                    }
                    Button { export(format: "UDF") } label: {
                        Label("UDF Olarak Kaydet", systemImage: "doc.fill")
                    }
                    Button {
                        UIPasteboard.general.string = recognizedText
                        exportMessage = "Metin panoya kopyalandı."
                    } label: {
                        Label("Metni Kopyala", systemImage: "doc.on.clipboard")
                    }

                    if let exportMessage {
                        Text(exportMessage)
                            .font(.caption)
                            .foregroundStyle(.green)
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
        .navigationTitle("Metin Tanıma (OCR)")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.pdf, .image]) { urls in
                if let first = urls.first {
                    selectedFile = first
                    recognizedText = nil
                    errorMessage = nil
                    exportMessage = nil
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

    private func runOCR() {
        guard let file = selectedFile else { return }
        isWorking = true
        errorMessage = nil
        progressText = ""

        Task {
            do {
                let text: String
                if file.pathExtension.lowercased() == "pdf" {
                    text = try await OCRService.recognizeText(pdfURL: file) { page, total in
                        progressText = "Sayfa \(page)/\(total) taranıyor..."
                    }
                } else {
                    text = try await OCRService.recognizeText(imageURL: file)
                }
                await MainActor.run {
                    recognizedText = text
                    isWorking = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isWorking = false
                }
            }
        }
    }

    private func export(format: String) {
        guard let text = recognizedText, let file = selectedFile else { return }
        let baseName = file.deletingPathExtension().lastPathComponent
        exportMessage = nil

        do {
            let output: URL
            switch format {
            case "DOCX": output = try OCRService.exportDOCX(text: text, baseName: baseName)
            case "PDF": output = try OCRService.exportPDF(text: text, baseName: baseName)
            default: output = try OCRService.exportUDF(text: text, baseName: baseName)
            }
            ConversionStorage.shared.addRecord(
                ConversionRecord(
                    originalFileName: output.lastPathComponent,
                    outputFormat: format,
                    success: true,
                    outputPath: output.path
                )
            )
            shareURL = output
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
