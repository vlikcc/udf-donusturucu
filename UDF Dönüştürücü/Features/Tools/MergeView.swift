import SwiftUI
import UniformTypeIdentifiers

struct MergeView: View {
    @State private var files: [URL] = []
    @State private var showPicker = false
    @State private var isWorking = false
    @State private var resultURL: URL?
    @State private var errorMessage: String?
    @State private var shareURL: URL?
    @State private var previewURL: URL?

    var body: some View {
        List {
            Section {
                Button {
                    showPicker = true
                } label: {
                    Label("Dosya Ekle (UDF / PDF)", systemImage: "plus.circle.fill")
                }
            } footer: {
                Text("Dosyalar aşağıdaki sırayla birleştirilir. Sırayı değiştirmek için basılı tutup sürükleyin.")
            }

            if !files.isEmpty {
                Section("Birleştirilecek Dosyalar (\(files.count))") {
                    ForEach(files, id: \.self) { url in
                        HStack {
                            Image(systemName: url.pathExtension.lowercased() == "udf" ? "doc.fill" : "doc.richtext.fill")
                                .foregroundStyle(url.pathExtension.lowercased() == "udf" ? AppTheme.navy : .red)
                            Text(url.lastPathComponent)
                                .font(.subheadline)
                                .lineLimit(1)
                        }
                    }
                    .onMove { indices, newOffset in
                        files.move(fromOffsets: indices, toOffset: newOffset)
                    }
                    .onDelete { indices in
                        files.remove(atOffsets: indices)
                    }
                }
            }

            if files.count >= 2 {
                Section {
                    Button {
                        merge()
                    } label: {
                        if isWorking {
                            HStack {
                                ProgressView()
                                Text("Birleştiriliyor...")
                            }
                        } else {
                            Label("Birleştir", systemImage: "doc.on.doc.fill")
                                .bold()
                        }
                    }
                    .disabled(isWorking)
                }
            }

            if let resultURL {
                Section("Sonuç") {
                    Label(resultURL.lastPathComponent, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.subheadline)

                    Button { previewURL = resultURL } label: {
                        Label("Görüntüle", systemImage: "eye")
                    }
                    Button { shareURL = resultURL } label: {
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
        .navigationTitle("Belge Birleştirme")
        .toolbar { EditButton() }
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.pdf, .udfType], allowsMultipleSelection: true) { urls in
                for url in urls where !files.contains(url) {
                    files.append(url)
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

    private func merge() {
        isWorking = true
        errorMessage = nil
        resultURL = nil
        let inputs = files

        Task {
            do {
                let output = try MergeService.merge(urls: inputs)
                await MainActor.run {
                    resultURL = output
                    isWorking = false
                    ConversionStorage.shared.addRecord(
                        ConversionRecord(
                            originalFileName: output.lastPathComponent,
                            outputFormat: "PDF",
                            success: true,
                            outputPath: output.path
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
