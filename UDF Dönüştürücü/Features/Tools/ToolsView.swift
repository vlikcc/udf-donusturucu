import SwiftUI
import UniformTypeIdentifiers

// MARK: - Araç tanımları

enum ProTool: String, CaseIterable, Identifiable, Hashable {
    case merge
    case compress
    case encrypt
    case editor
    case ocr
    case signature
    case templates

    var id: String { rawValue }

    var title: String {
        switch self {
        case .merge: return "Belge Birleştirme"
        case .compress: return "PDF Sıkıştırma"
        case .encrypt: return "PDF Şifreleme"
        case .editor: return "UDF Düzenleme"
        case .ocr: return "Metin Tanıma (OCR)"
        case .signature: return "E-İmza Bilgisi"
        case .templates: return "Dilekçe Şablonları"
        }
    }

    var subtitle: String {
        switch self {
        case .merge: return "Birden fazla UDF/PDF'i tek PDF yapın"
        case .compress: return "Büyük PDF'lerin boyutunu küçültün"
        case .encrypt: return "PDF'e parola koruması ekleyin"
        case .editor: return "Metin, tablo, renk ve UYAP alanları"
        case .ocr: return "Taranmış belgeden metin çıkarın"
        case .signature: return "İmzalı UDF'te imzacıyı görün"
        case .templates: return "Hazır şablondan dilekçe oluşturun"
        }
    }

    var icon: String {
        switch self {
        case .merge: return "doc.on.doc.fill"
        case .compress: return "arrow.down.right.and.arrow.up.left"
        case .encrypt: return "lock.doc.fill"
        case .editor: return "pencil.and.outline"
        case .ocr: return "text.viewfinder"
        case .signature: return "signature"
        case .templates: return "doc.badge.plus"
        }
    }

    var tint: Color {
        switch self {
        case .merge: return .blue
        case .compress: return .green
        case .encrypt: return .red
        case .editor: return .indigo
        case .ocr: return .purple
        case .signature: return .orange
        case .templates: return AppTheme.navy
        }
    }
}

// MARK: - Araçlar ekranı

struct ToolsView: View {
    @ObservedObject var limitService = LimitService.shared
    @Environment(\.colorScheme) private var colorScheme

    @State private var showPaywall = false
    @State private var paywallSource = "tools"
    @State private var activeTool: ProTool?

    var body: some View {
        ZStack {
            AppTheme.pageBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 12) {
                    if !limitService.isPremium {
                        proBanner
                    }

                    ForEach(ProTool.allCases) { tool in
                        toolCard(tool)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 40)
            }
        }
        .navigationTitle("Araçlar")
        .sheet(isPresented: $showPaywall) {
            PaywallView(source: paywallSource)
        }
        .navigationDestination(item: $activeTool) { tool in
            destination(for: tool)
        }
    }

    private var proBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "crown.fill")
                .foregroundStyle(.orange)
            Text("Tüm araçlar Pro üyelere özeldir.")
                .font(.subheadline)
            Spacer()
            Button {
                paywallSource = "tools_banner"
                showPaywall = true
            } label: {
                Text("Pro'ya Geç")
                    .font(.caption).bold()
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.orange, in: Capsule())
                    .foregroundStyle(.white)
            }
        }
        .padding(14)
        .background(AppTheme.cardBg, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: AppTheme.cardShadow, radius: 8, y: 4)
    }

    private func toolCard(_ tool: ProTool) -> some View {
        Button {
            if limitService.isPremium {
                AnalyticsService.logToolOpened(tool.rawValue)
                activeTool = tool
            } else {
                AnalyticsService.logToolLockedTap(tool.rawValue)
                paywallSource = "tools_\(tool.rawValue)"
                showPaywall = true
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: tool.icon)
                    .font(.title3)
                    .foregroundStyle(tool.tint)
                    .frame(width: 46, height: 46)
                    .background(tool.tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 3) {
                    Text(tool.title)
                        .font(.subheadline).bold()
                        .foregroundStyle(.primary)
                    Text(tool.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                }

                Spacer()

                if limitService.isPremium {
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                } else {
                    Image(systemName: "lock.fill")
                        .font(.subheadline)
                        .foregroundStyle(.orange)
                }
            }
            .padding(14)
            .background(AppTheme.cardBg, in: RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.primary.opacity(colorScheme == .dark ? 0.1 : 0), lineWidth: 1)
            )
            .shadow(color: AppTheme.cardShadow, radius: 8, y: 4)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func destination(for tool: ProTool) -> some View {
        switch tool {
        case .merge: MergeView()
        case .compress: CompressView()
        case .encrypt: EncryptView()
        case .editor: UDFEditorView()
        case .ocr: OCRView()
        case .signature: SignatureInfoView()
        case .templates: TemplatesView()
        }
    }
}

// MARK: - Araçlar için genel dosya seçici

struct ToolDocumentPicker: UIViewControllerRepresentable {
    let types: [UTType]
    var allowsMultipleSelection = false
    var onPick: ([URL]) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
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

extension UTType {
    static var udfType: UTType { UTType(filenameExtension: "udf") ?? .data }
}
