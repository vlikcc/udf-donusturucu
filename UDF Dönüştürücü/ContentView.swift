import SwiftUI
import UniformTypeIdentifiers
import UIKit

// MARK: - Enums

enum OutputFormat: String, CaseIterable, Identifiable {
    case pdf = "PDF"
    case docx = "DOCX"
    case udf = "UDF"
    var id: String { rawValue }
}

enum ConversionDirection: String, CaseIterable {
    case udfToOther = "UDF → PDF / DOCX"
    case otherToUdf = "PDF / DOCX → UDF"
}

// MARK: - Theme Colors

enum AppTheme {
    static let navy = Color("AccentNavy")
    static let cardBg = Color(.systemBackground)
    static let pageBg = Color(.systemGroupedBackground)
    static let cardShadow = Color.black.opacity(0.08)
    static let elevatedCardBg = Color(.secondarySystemGroupedBackground)
}

// MARK: - Document Pickers

struct UDFDocumentPicker: UIViewControllerRepresentable {
    var allowsMultipleSelection = true
    var onPick: ([URL]) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: UTType.udfPickerTypes, asCopy: true)
        picker.allowsMultipleSelection = allowsMultipleSelection
        picker.shouldShowFileExtensions = true
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ vc: UIDocumentPickerViewController, context: Context) {}
    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: ([URL]) -> Void
        init(onPick: @escaping ([URL]) -> Void) { self.onPick = onPick }
        func documentPicker(_ c: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { onPick(urls) }
        func documentPickerWasCancelled(_ c: UIDocumentPickerViewController) { onPick([]) }
    }
}

struct PDFDOCXDocumentPicker: UIViewControllerRepresentable {
    var allowsMultipleSelection = true
    var onPick: ([URL]) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types: [UTType] = [.pdf, UTType(filenameExtension: "docx") ?? .data]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.allowsMultipleSelection = allowsMultipleSelection
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ vc: UIDocumentPickerViewController, context: Context) {}
    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: ([URL]) -> Void
        init(onPick: @escaping ([URL]) -> Void) { self.onPick = onPick }
        func documentPicker(_ c: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { onPick(urls) }
        func documentPickerWasCancelled(_ c: UIDocumentPickerViewController) { onPick([]) }
    }
}

// MARK: - ContentView

struct ContentView: View {
    @ObservedObject var limitService = LimitService.shared
    @ObservedObject var storage = ConversionStorage.shared
    @Environment(\.colorScheme) private var colorScheme

    @State private var conversionDirection: ConversionDirection = .udfToOther
    @State private var selectedFiles: [URL] = []
    @State private var showDocumentPicker = false
    @State private var showPDFDOCXPicker = false
    @State private var formatSelection: [URL: OutputFormat] = [:]
    @State private var navigateToConversion = false
    @State private var showPaywall = false
    @State private var paywallSource = "limit_card"
    @State private var showLimitAlert = false
    @State private var showBatchProAlert = false
    @ObservedObject private var fileRouter = IncomingFileRouter.shared
    @State private var shareURL: URL?
    @State private var showExporter = false
    @State private var exportData: Data?
    @State private var exportFileName = ""
    @State private var exportUTType: UTType = .pdf

    var body: some View {
        ZStack {
            AppTheme.pageBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    directionPicker
                    limitCard
                    selectFileButton

                    if !selectedFiles.isEmpty {
                        fileListCard
                        convertButton
                    }

                    recentConversionsCard

                    BannerAdContainer()
                        .padding(.top, 4)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 100)
            }
        }
        .navigationTitle("")
        .toolbar {
            ToolbarItem(placement: .principal) {
                HStack(spacing: 8) {
                    Image(systemName: "doc.text")
                        .foregroundStyle(AppTheme.navy)
                    Text("UDF Dönüştürücü")
                        .font(.title3).bold()
                        .foregroundStyle(.primary)
                }
            }
        }
        .sheet(isPresented: $showDocumentPicker) {
            UDFDocumentPicker(allowsMultipleSelection: limitService.isPremium) { urls in
                addFiles(urls)
                showDocumentPicker = false
            }
        }
        .sheet(isPresented: $showPDFDOCXPicker) {
            PDFDOCXDocumentPicker(allowsMultipleSelection: limitService.isPremium) { urls in
                addFiles(urls)
                showPDFDOCXPicker = false
            }
        }
        .sheet(isPresented: $showPaywall) { PaywallView(source: paywallSource) }
        .alert("Günlük Limit", isPresented: $showLimitAlert) {
            if limitService.canEarnBonusConversion {
                Button("Reklam İzle (+1 Çeviri)") {
                    if let vc = UIApplication.topViewController() {
                        AdsManager.shared.showRewarded(from: vc)
                    }
                }
            }
            Button("Premium'a Yükselt") {
                paywallSource = "limit_alert"
                showPaywall = true
            }
            Button("Tamam", role: .cancel) {}
        } message: {
            if limitService.canEarnBonusConversion {
                Text("Günlük ücretsiz dönüştürme limitinize ulaştınız. Reklam izleyerek +1 çeviri kazanabilir (günde en fazla 2) veya Premium'a yükselerek sınırsız dönüştürme yapabilirsiniz.")
            } else {
                Text("Bugünkü ücretsiz dönüştürme ve reklam haklarınız doldu. Premium'a yükselerek sınırsız dönüştürme yapabilirsiniz.")
            }
        }
        .navigationDestination(isPresented: $navigateToConversion) {
            ConversionView(files: selectedFiles, formats: formatSelection)
        }
        .onChange(of: conversionDirection) { _, _ in
            selectedFiles.removeAll()
            formatSelection.removeAll()
        }
        .onChange(of: fileRouter.incomingFile) { _, url in
            guard let url else { return }
            conversionDirection = .udfToOther
            addFiles([url])
            fileRouter.incomingFile = nil
        }
        .onAppear {
            if let url = fileRouter.incomingFile {
                conversionDirection = .udfToOther
                addFiles([url])
                fileRouter.incomingFile = nil
            }
        }
        .alert("Toplu Dönüştürme", isPresented: $showBatchProAlert) {
            Button("Pro'ya Yükselt") {
                paywallSource = "batch"
                showPaywall = true
            }
            Button("Tamam", role: .cancel) {}
        } message: {
            Text("Aynı anda birden fazla dosya dönüştürme Pro üyelere özeldir. Ücretsiz sürümde tek dosya seçebilirsiniz.")
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
        .fileExporter(
            isPresented: $showExporter,
            document: ExportFileDocument(data: exportData ?? Data()),
            contentType: exportUTType,
            defaultFilename: exportFileName
        ) { _ in }
    }

    // MARK: - Direction Picker

    private var directionPicker: some View {
        HStack(spacing: 0) {
            directionTab("UDF'den Çevir", icon: "doc.on.doc", dir: .udfToOther)
            directionTab("UDF'ye Çevir", icon: "doc.badge.arrow.up", dir: .otherToUdf)
        }
        .padding(4)
        .background(AppTheme.cardBg, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.primary.opacity(colorScheme == .dark ? 0.1 : 0), lineWidth: 1)
        )
        .shadow(color: AppTheme.cardShadow, radius: 10, y: 5)
    }

    private func directionTab(_ title: String, icon: String, dir: ConversionDirection) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { conversionDirection = dir }
        } label: {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.title3)
                Text(title)
                    .font(.caption).bold()
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                conversionDirection == dir ? AppTheme.navy : Color.clear,
                in: RoundedRectangle(cornerRadius: 10)
            )
            .foregroundStyle(conversionDirection == dir ? .white : .secondary)
        }
    }

    // MARK: - Limit Card

    private var limitCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("GÜNLÜK LİMİT")
                        .font(.caption).bold()
                        .foregroundStyle(.secondary)

                    if limitService.isPremium {
                        Text("Sınırsız")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                    } else {
                        Text("\(limitService.usedConversions) / \(limitService.totalAllowedConversions)")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                    }
                }

                Spacer()

                if limitService.isPremium {
                    Image(systemName: "crown.fill")
                        .font(.title2)
                        .foregroundStyle(
                            LinearGradient(
                                colors: [.yellow, .orange],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                } else {
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("Kalan: \(limitService.remainingConversions) Çeviri")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        Button {
                            paywallSource = "limit_card"
                            showPaywall = true
                        } label: {
                            Text("Yükselt")
                                .font(.caption2).bold()
                                .padding(.horizontal, 12)
                                .padding(.vertical, 5)
                                .background(Color.orange, in: Capsule())
                                .foregroundStyle(.white)
                        }
                    }
                }
            }

            if !limitService.isPremium {
                ProgressView(
                    value: Double(min(limitService.usedConversions, limitService.totalAllowedConversions)),
                    total: Double(limitService.totalAllowedConversions)
                )
                    .tint(AppTheme.navy)

                HStack(spacing: 4) {
                    Image(systemName: "info.circle")
                        .font(.caption2)
                    Text("Bugün için \(limitService.remainingConversions) çeviri hakkınız kaldı.")
                        .font(.caption)
                }
                .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .background(AppTheme.cardBg, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(colorScheme == .dark ? 0.1 : 0), lineWidth: 1)
        )
        .shadow(color: AppTheme.cardShadow, radius: 10, y: 5)
    }

    // MARK: - Select File Button

    private var selectFileButton: some View {
        Button {
            if conversionDirection == .udfToOther {
                showDocumentPicker = true
            } else {
                showPDFDOCXPicker = true
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "plus.circle.fill")
                    .font(.title3)
                Text(conversionDirection == .udfToOther ? "UDF Dosyası Seç" : "PDF / DOCX Dosyası Seç")
                    .font(.headline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(AppTheme.navy, in: RoundedRectangle(cornerRadius: 14))
            .foregroundStyle(.white)
        }
        .shadow(color: AppTheme.navy.opacity(0.35), radius: 12, y: 6)
    }

    // MARK: - File List

    private var fileListCard: some View {
        VStack(spacing: 0) {
            ForEach(Array(selectedFiles.enumerated()), id: \.element) { index, url in
                HStack(spacing: 12) {
                    fileIcon(for: url)
                        .frame(width: 40, height: 40)
                        .background(iconBgColor(for: url).opacity(0.1), in: RoundedRectangle(cornerRadius: 10))

                    VStack(alignment: .leading, spacing: 2) {
                        Text(url.lastPathComponent)
                            .font(.subheadline).bold()
                            .lineLimit(1)

                        if conversionDirection == .udfToOther {
                            Text("\(formatSelection[url]?.rawValue ?? "PDF") Formatı")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("UDF Formatına")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Spacer()

                    if conversionDirection == .udfToOther {
                        Menu {
                            Button("PDF") { formatSelection[url] = .pdf }
                            Button("DOCX") { formatSelection[url] = .docx }
                        } label: {
                            Text(formatSelection[url]?.rawValue ?? "PDF")
                                .font(.caption).bold()
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(AppTheme.navy.opacity(0.1), in: Capsule())
                                .foregroundStyle(AppTheme.navy)
                        }
                    }

                    Button { removeFile(url) } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.quaternary)
                    }
                }
                .padding(.vertical, 12)
                .padding(.horizontal, 14)

                if index < selectedFiles.count - 1 {
                    Divider().padding(.leading, 66)
                }
            }
        }
        .background(AppTheme.cardBg, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.primary.opacity(colorScheme == .dark ? 0.1 : 0), lineWidth: 1)
        )
        .shadow(color: AppTheme.cardShadow, radius: 10, y: 5)
    }

    // MARK: - Convert Button

    private var convertButton: some View {
        Button {
            let count = selectedFiles.count
            if limitService.canConvert && limitService.useConversion(count: count) {
                navigateToConversion = true
            } else {
                AnalyticsService.logLimitHit()
                showLimitAlert = true
            }
        } label: {
            HStack {
                Image(systemName: "arrow.triangle.2.circlepath")
                Text("Dönüştür (\(selectedFiles.count) dosya)")
                    .bold()
            }
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                LinearGradient(colors: [AppTheme.navy, AppTheme.navy.opacity(0.8)],
                               startPoint: .leading, endPoint: .trailing),
                in: RoundedRectangle(cornerRadius: 14)
            )
            .foregroundStyle(.white)
        }
    }

    // MARK: - Recent Conversions

    private var recentConversionsCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Son Çeviriler")
                    .font(.headline)
                Spacer()
                NavigationLink {
                    HistoryView()
                } label: {
                    Text("Tümünü Gör")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.navy)
                }
            }

            if storage.recentRecords.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "tray")
                        .font(.title3)
                        .foregroundStyle(.quaternary)
                    Text("Henüz bir dönüşüm yok.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            } else {
                ForEach(storage.recentRecords.prefix(3)) { record in
                    recentRow(record)
                }
            }
        }
        .padding(16)
        .background(AppTheme.cardBg, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(colorScheme == .dark ? 0.1 : 0), lineWidth: 1)
        )
        .shadow(color: AppTheme.cardShadow, radius: 10, y: 5)
    }

    private func recentRow(_ record: ConversionRecord) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                recentIcon(for: record)
                    .frame(width: 44, height: 44)
                    .background(
                        recentIconBgColor(for: record).opacity(0.1),
                        in: RoundedRectangle(cornerRadius: 10)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(record.originalFileName)
                        .font(.subheadline).bold()
                        .lineLimit(1)
                    HStack(spacing: 6) {
                        Text(record.date, format: .dateTime.month(.abbreviated).day().hour().minute())
                            .font(.caption)
                        Text("\(record.outputFormat) Formatı")
                            .font(.caption)
                    }
                    .foregroundStyle(.secondary)
                }

                Spacer()
            }

            // Show action buttons if file exists
            if record.success && record.fileExists {
                HStack(spacing: 10) {
                    Button {
                        if let url = record.resolvedURL {
                            shareURL = url
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "square.and.arrow.up")
                            Text("Paylaş")
                        }
                        .font(.caption).bold()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(AppTheme.navy.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(AppTheme.navy)
                    }

                    Button {
                        saveRecentToFiles(record: record)
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "folder.badge.plus")
                            Text("Kaydet")
                        }
                        .font(.caption).bold()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.green.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(.green)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func recentIconBgColor(for record: ConversionRecord) -> Color {
        switch record.outputFormat.uppercased() {
        case "PDF": return .red
        case "DOCX": return .blue
        case "UDF": return AppTheme.navy
        default: return .gray
        }
    }

    private func saveRecentToFiles(record: ConversionRecord) {
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

    // MARK: - Helpers

    private func addFiles(_ urls: [URL]) {
        var incoming = urls.filter { !selectedFiles.contains($0) }

        // Toplu dönüştürme Pro özelliğidir: ücretsiz kullanıcı aynı anda tek dosya seçebilir.
        if !limitService.isPremium {
            let capacity = max(0, 1 - selectedFiles.count)
            if incoming.count > capacity {
                incoming = Array(incoming.prefix(capacity))
                showBatchProAlert = true
            }
        }

        for url in incoming {
            selectedFiles.append(url)
            formatSelection[url] = conversionDirection == .udfToOther ? .pdf : .udf
        }
    }

    private func removeFile(_ url: URL) {
        selectedFiles.removeAll { $0 == url }
        formatSelection.removeValue(forKey: url)
    }

    private func fileIcon(for url: URL) -> some View {
        let ext = url.pathExtension.lowercased()
        let icon: String
        let color: Color
        switch ext {
        case "pdf": icon = "doc.richtext.fill"; color = .red
        case "docx", "doc": icon = "doc.text.fill"; color = .blue
        case "udf": icon = "doc.fill"; color = AppTheme.navy
        default: icon = "doc.fill"; color = .gray
        }
        return Image(systemName: icon)
            .font(.title3)
            .foregroundStyle(color)
    }

    private func iconBgColor(for url: URL) -> Color {
        switch url.pathExtension.lowercased() {
        case "pdf": return .red
        case "docx", "doc": return .blue
        case "udf": return AppTheme.navy
        default: return .gray
        }
    }

    private func recentIcon(for record: ConversionRecord) -> some View {
        let icon: String
        let color: Color
        switch record.outputFormat {
        case "PDF": icon = "doc.richtext.fill"; color = .red
        case "DOCX": icon = "doc.text.fill"; color = .blue
        default: icon = "doc.fill"; color = AppTheme.navy
        }
        return Image(systemName: icon)
            .font(.title3)
            .foregroundStyle(color)
    }
}

#Preview {
    NavigationStack {
        ContentView()
    }
}
