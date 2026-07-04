import SwiftUI

struct ConversionResult: Identifiable {
    let id = UUID()
    let fileName: String
    let format: OutputFormat
    let outputURL: URL?
    let error: String?
    var success: Bool { outputURL != nil }
}

struct ConversionView: View {
    let files: [URL]
    let formats: [URL: OutputFormat]

    @State private var results: [ConversionResult] = []
    @State private var currentIndex: Int = 0
    @State private var isConverting: Bool = true
    @State private var showResult: Bool = false

    var progress: Double {
        guard !files.isEmpty else { return 1.0 }
        return Double(currentIndex) / Double(files.count)
    }

    var body: some View {
        VStack(spacing: 24) {
            if isConverting {
                conversionProgressView
            }
        }
        .padding()
        .navigationTitle("Dönüştürme")
        .navigationBarBackButtonHidden(isConverting)
        .task {
            await performConversions()
        }
        .navigationDestination(isPresented: $showResult) {
            ResultView(results: results)
        }
    }

    private var conversionProgressView: some View {
        VStack(spacing: 20) {
            Spacer()

            ZStack {
                Circle()
                    .fill(AppTheme.navy.opacity(0.1))
                    .frame(width: 110, height: 110)
                Image(systemName: "doc.on.doc")
                    .font(.system(size: 44))
                    .foregroundStyle(AppTheme.navy)
                    .symbolEffect(.pulse, isActive: isConverting)
            }

            Text("Dosyalar dönüştürülüyor...")
                .font(.title2).bold()

            ProgressView(value: progress)
                .progressViewStyle(.linear)
                .tint(AppTheme.navy)
                .frame(maxWidth: 280)

            Text("\(currentIndex) / \(files.count)")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            if currentIndex < files.count {
                Text(files[currentIndex].lastPathComponent)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()
        }
    }

    private func performConversions() async {
        var conversionResults: [ConversionResult] = []

        for (index, fileURL) in files.enumerated() {
            await MainActor.run { currentIndex = index }

            let format = formats[fileURL] ?? .pdf
            let result = await convertFile(url: fileURL, format: format)
            conversionResults.append(result)

            ConversionStorage.shared.addRecord(
                ConversionRecord(
                    originalFileName: fileURL.lastPathComponent,
                    outputFormat: format.rawValue,
                    success: result.success,
                    outputPath: result.outputURL?.path
                )
            )
        }

        await MainActor.run {
            currentIndex = files.count
            results = conversionResults
            isConverting = false
            showResult = true
        }
    }

    private func convertFile(url: URL, format: OutputFormat) async -> ConversionResult {
        do {
            let outputURL: URL

            switch format {
            case .pdf:
                // UDF → PDF
                let document = try UDFParser.parse(fileURL: url)
                outputURL = try PDFConverter.convert(document: document)

            case .docx:
                // UDF → DOCX
                let document = try UDFParser.parse(fileURL: url)
                outputURL = try WordConverter.convert(document: document)

            case .udf:
                // PDF/DOCX → UDF
                let ext = url.pathExtension.lowercased()
                if ext == "pdf" {
                    outputURL = try UDFCreator.createFromPDF(url: url)
                } else if ext == "docx" || ext == "doc" {
                    outputURL = try UDFCreator.createFromDOCX(url: url)
                } else {
                    throw ExtractionError.invalidFormat
                }
            }

            return ConversionResult(fileName: url.lastPathComponent, format: format, outputURL: outputURL, error: nil)
        } catch {
            return ConversionResult(fileName: url.lastPathComponent, format: format, outputURL: nil, error: error.localizedDescription)
        }
    }
}
