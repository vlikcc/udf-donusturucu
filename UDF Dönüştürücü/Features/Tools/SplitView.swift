import SwiftUI
import UniformTypeIdentifiers

struct SplitView: View {
    private enum Mode: String, CaseIterable, Identifiable {
        case extractRange
        case everyPage

        var id: String { rawValue }

        var title: String {
            switch self {
            case .extractRange: return "Sayfa Aralığı Çıkar"
            case .everyPage: return "Her Sayfayı Ayır"
            }
        }

        var subtitle: String {
            switch self {
            case .extractRange: return "Seçtiğiniz sayfalardan tek bir PDF oluşturur"
            case .everyPage: return "Her sayfayı ayrı bir PDF dosyası yapar"
            }
        }
    }

    @State private var source: SplitService.Source?
    @State private var sourceName: String?
    @State private var mode: Mode = .extractRange
    @State private var rangeText = ""

    @State private var showPicker = false
    @State private var isLoadingSource = false
    @State private var isWorking = false

    @State private var results: [URL] = []
    @State private var errorMessage: String?
    @State private var shareURL: URL?
    @State private var shareAll = false
    @State private var previewURL: URL?

    @FocusState private var rangeFieldFocused: Bool

    var body: some View {
        List {
            fileSection

            if let source {
                modeSection

                if mode == .extractRange {
                    rangeSection(pageCount: source.pageCount)
                }

                actionSection
            }

            resultsSection

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Belge Bölme")
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.pdf] + UTType.udfPickerTypes) { urls in
                showPicker = false
                if let first = urls.first { load(first) }
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
        .sheet(isPresented: $shareAll) {
            ActivityViewController(activityItems: results)
        }
        .navigationDestination(item: $previewURL) { url in
            DocumentPreviewView(url: url)
        }
    }

    // MARK: - Bölümler

    private var fileSection: some View {
        Section {
            Button {
                showPicker = true
            } label: {
                if isLoadingSource {
                    HStack {
                        ProgressView()
                        Text("Belge hazırlanıyor...")
                    }
                } else {
                    Label(sourceName ?? "Dosya Seç (UDF / PDF)", systemImage: "doc.badge.ellipsis")
                        .lineLimit(1)
                }
            }
            .disabled(isLoadingSource || isWorking)

            if let source {
                HStack {
                    Text("Toplam sayfa")
                    Spacer()
                    Text("\(source.pageCount)")
                        .foregroundStyle(.secondary)
                }
            }
        } footer: {
            Text("UDF dosyaları bölünmeden önce otomatik olarak PDF'e dönüştürülür.")
        }
    }

    private var modeSection: some View {
        Section("Ne yapılsın?") {
            ForEach(Mode.allCases) { option in
                Button {
                    mode = option
                    errorMessage = nil
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
                        Image(systemName: mode == option ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(mode == option ? AppTheme.navy : .secondary)
                    }
                }
            }
        }
    }

    private func rangeSection(pageCount: Int) -> some View {
        Section {
            TextField("Örnek: 1-3, 7, 10-12", text: $rangeText)
                .keyboardType(.numbersAndPunctuation)
                .autocorrectionDisabled()
                .focused($rangeFieldFocused)
                .submitLabel(.done)
                .onSubmit { rangeFieldFocused = false }
        } header: {
            Text("Sayfa Aralığı")
        } footer: {
            Text("Tek sayfa veya aralık yazabilir, virgülle ayırabilirsiniz (1-\(pageCount) arası). Yazdığınız sıra korunur — \"3, 1\" yazarsanız sayfalar bu sırayla çıkar.")
        }
    }

    private var actionSection: some View {
        Section {
            Button {
                rangeFieldFocused = false
                run()
            } label: {
                if isWorking {
                    HStack {
                        ProgressView()
                        Text(mode == .extractRange ? "Çıkarılıyor..." : "Bölünüyor...")
                    }
                } else {
                    Label(
                        mode == .extractRange ? "Sayfaları Çıkar" : "Sayfalara Böl",
                        systemImage: "scissors"
                    )
                    .bold()
                }
            }
            .disabled(isWorking || (mode == .extractRange && rangeText.trimmingCharacters(in: .whitespaces).isEmpty))
        }
    }

    @ViewBuilder
    private var resultsSection: some View {
        if !results.isEmpty {
            Section("Sonuç (\(results.count) dosya)") {
                ForEach(results, id: \.self) { url in
                    HStack {
                        Image(systemName: "doc.richtext.fill")
                            .foregroundStyle(.red)
                        Text(url.lastPathComponent)
                            .font(.subheadline)
                            .lineLimit(1)
                        Spacer()
                        Button {
                            previewURL = url
                        } label: {
                            Image(systemName: "eye")
                        }
                        .buttonStyle(.borderless)

                        Button {
                            shareURL = url
                        } label: {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .buttonStyle(.borderless)
                    }
                }

                if results.count > 1 {
                    Button {
                        shareAll = true
                    } label: {
                        Label("Tümünü Paylaş", systemImage: "square.and.arrow.up.on.square")
                    }
                }
            }
        }
    }

    // MARK: - İşlemler

    private func load(_ url: URL) {
        isLoadingSource = true
        errorMessage = nil
        source = nil
        sourceName = url.lastPathComponent
        results = []
        rangeText = ""

        Task {
            do {
                let loaded = try SplitService.loadSource(url: url)
                await MainActor.run {
                    source = loaded
                    isLoadingSource = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    sourceName = nil
                    isLoadingSource = false
                }
            }
        }
    }

    private func run() {
        guard let source else { return }
        isWorking = true
        errorMessage = nil
        results = []
        let selectedMode = mode
        let ranges = rangeText

        Task {
            do {
                let outputs: [URL]
                switch selectedMode {
                case .extractRange:
                    outputs = [try SplitService.extract(source: source, ranges: ranges)]
                case .everyPage:
                    outputs = try SplitService.splitAll(source: source)
                }

                await MainActor.run {
                    results = outputs
                    isWorking = false
                    for output in outputs {
                        ConversionStorage.shared.addRecord(
                            ConversionRecord(
                                originalFileName: output.lastPathComponent,
                                outputFormat: "PDF",
                                success: true,
                                outputPath: output.path
                            )
                        )
                    }
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
