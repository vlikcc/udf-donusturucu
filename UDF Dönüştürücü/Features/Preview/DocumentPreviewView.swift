import SwiftUI
import PDFKit
import QuickLook

struct DocumentPreviewView: View {
    let url: URL

    @State private var showShareSheet = false
    @State private var udfState: UDFPreviewState = .loading
    @State private var pdfState: PDFPreviewState = .loading

    private enum UDFPreviewState {
        case loading
        case loaded(AttributedString)
        case failed(String)
    }

    private enum PDFPreviewState {
        case loading
        case loaded(PDFDocument)
        case failed(String)
    }

    var body: some View {
        Group {
            switch url.pathExtension.lowercased() {
            case "pdf":
                pdfPreview
            case "udf":
                udfPreview
            default:
                // For DOCX and other formats, show text content or use QuickLook
                QuickLookPreview(url: url)
            }
        }
        .navigationTitle(url.lastPathComponent)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showShareSheet = true
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
            }
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityViewController(activityItems: [url])
        }
        .task(id: url) {
            switch url.pathExtension.lowercased() {
            case "udf":
                await loadUDFPreview()
            case "pdf":
                await loadPDFPreview()
            default:
                break
            }
        }
    }

    @ViewBuilder
    private var udfPreview: some View {
        switch udfState {
        case .loading:
            ProgressView()
        case .loaded(let text):
            ScrollView {
                Text(text)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
        case .failed(let message):
            ContentUnavailableView(message, systemImage: "exclamationmark.triangle")
        }
    }

    @ViewBuilder
    private var pdfPreview: some View {
        switch pdfState {
        case .loading:
            ProgressView()
        case .loaded(let document):
            PDFKitView(document: document)
        case .failed(let message):
            ContentUnavailableView(message, systemImage: "exclamationmark.triangle")
        }
    }

    private func loadUDFPreview() async {
        udfState = .loading
        let fileURL = url
        let result = await Task.detached(priority: .userInitiated) { () -> Result<UDFDocument, Error> in
            Result { try UDFParser.parse(fileURL: fileURL) }
        }.value

        switch result {
        case .success(let document):
            if let formatted = document.content.formattedString,
               let attributed = try? AttributedString(formatted, including: \.uiKit) {
                udfState = .loaded(attributed)
            } else {
                udfState = .loaded(AttributedString(document.content.text))
            }
        case .failure(let error):
            udfState = .failed(error.localizedDescription)
        }
    }

    private func loadPDFPreview() async {
        pdfState = .loading
        let fileURL = url
        let document = await Task.detached(priority: .userInitiated) { () -> PDFDocument? in
            PDFDocument(url: fileURL)
        }.value

        if let document {
            pdfState = .loaded(document)
        } else {
            pdfState = .failed("PDF açılamadı.")
        }
    }
}

struct PDFKitView: UIViewRepresentable {
    let document: PDFDocument

    func makeUIView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        pdfView.document = document
        return pdfView
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        if uiView.document !== document {
            uiView.document = document
        }
    }
}

struct QuickLookPreview: UIViewControllerRepresentable {
    let url: URL

    func makeCoordinator() -> Coordinator {
        Coordinator(url: url)
    }

    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: QLPreviewController, context: Context) {}

    class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }

        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }

        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
            url as QLPreviewItem
        }
    }
}
