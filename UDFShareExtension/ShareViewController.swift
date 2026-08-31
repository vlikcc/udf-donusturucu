import UIKit
import SwiftUI
import UniformTypeIdentifiers

/// Share Extension'ın giriş noktası. Şablonun `SLComposeServiceViewController` +
/// storyboard yapısı yerine SwiftUI arayüzünü barındırır; principal class olarak
/// Info.plist'te bu sınıf tanımlıdır.
final class ShareViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()

        let root = ShareRootView(
            attachments: collectAttachments(),
            onFinish: { [weak self] in self?.complete() },
            onCancel: { [weak self] in self?.cancel() }
        )

        let host = UIHostingController(rootView: root)
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
    }

    /// Paylaşım sayfasından gelen dosya taşıyıcılarını toplar.
    private func collectAttachments() -> [NSItemProvider] {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else { return [] }
        return items.flatMap { $0.attachments ?? [] }
    }

    private func complete() {
        extensionContext?.completeRequest(returningItems: nil)
    }

    private func cancel() {
        extensionContext?.cancelRequest(withError: NSError(domain: "UDFShareExtension", code: 0))
    }
}

// MARK: - Arayüz

private struct ShareRootView: View {
    let attachments: [NSItemProvider]
    let onFinish: () -> Void
    let onCancel: () -> Void

    @State private var state: ShareState = .loading
    @State private var format: ShareOutputFormat = .pdf

    var body: some View {
        NavigationStack {
            Group {
                switch state {
                case .loading:
                    ProgressView("Belge okunuyor…")

                case .ready(let files):
                    readyView(files: files)

                case .working:
                    ProgressView("Dönüştürülüyor…")

                case .done(let count):
                    resultView(count: count)

                case .failed(let message):
                    ContentUnavailableView {
                        Label("Dönüştürülemedi", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text(message)
                    }
                }
            }
            .navigationTitle("Evrak Dönüştürücü")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç", action: onCancel)
                }
            }
        }
        .task { await load() }
    }

    @ViewBuilder
    private func readyView(files: [URL]) -> some View {
        List {
            Section("Belgeler (\(files.count))") {
                ForEach(files, id: \.self) { url in
                    Label(url.lastPathComponent, systemImage: "doc.fill")
                        .font(.subheadline)
                        .lineLimit(1)
                }
            }

            Section("Hedef biçim") {
                Picker("Biçim", selection: $format) {
                    ForEach(ShareOutputFormat.allCases) { option in
                        Text(option.title).tag(option)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section {
                Button("Dönüştür") {
                    Task { await convert(files: files) }
                }
                .bold()
            } footer: {
                Text("Dönüştürülen belgeler Evrak Dönüştürücü uygulamasının geçmişine eklenir.")
            }
        }
    }

    @ViewBuilder
    private func resultView(count: Int) -> some View {
        ContentUnavailableView {
            Label("\(count) belge dönüştürüldü", systemImage: "checkmark.circle.fill")
        } description: {
            Text("Belgeleri Evrak Dönüştürücü uygulamasının Geçmiş sekmesinde bulabilirsiniz.")
        } actions: {
            Button("Bitti", action: onFinish)
                .buttonStyle(.borderedProminent)
        }
    }

    // MARK: - Akış

    private func load() async {
        let urls = await ShareConversion.resolveUDFFiles(from: attachments)
        await MainActor.run {
            if urls.isEmpty {
                state = .failed("Paylaşılan öğeler arasında .udf belgesi bulunamadı. Şu an yalnızca UDF dosyaları desteklenmektedir.")
            } else {
                state = .ready(urls)
            }
        }
    }

    private func convert(files: [URL]) async {
        await MainActor.run { state = .working }
        do {
            let count = try ShareConversion.convert(files: files, to: format)
            await MainActor.run { state = .done(count) }
        } catch {
            await MainActor.run { state = .failed(error.localizedDescription) }
        }
    }
}

private enum ShareState {
    case loading
    case ready([URL])
    case working
    case done(Int)
    case failed(String)
}

enum ShareOutputFormat: String, CaseIterable, Identifiable {
    case pdf
    case docx

    var id: String { rawValue }

    var title: String {
        switch self {
        case .pdf: return "PDF"
        case .docx: return "Word (.docx)"
        }
    }

    var recordFormat: String {
        switch self {
        case .pdf: return "PDF"
        case .docx: return "DOCX"
        }
    }
}
