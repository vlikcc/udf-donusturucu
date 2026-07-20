import SwiftUI
import UniformTypeIdentifiers

struct UDFEditorView: View {
    @State private var editorProxy = RichTextEditorProxy()

    @State private var sourceURL: URL?
    @State private var baseName = ""
    @State private var document = UDFEditDocument(blocks: [])
    @State private var originalFingerprint = ""
    @State private var hasSignature = false
    @State private var showPicker = false
    @State private var isLoading = false
    @State private var isSaving = false
    @State private var resultURL: URL?
    @State private var errorMessage: String?
    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var showDiscardAlert = false
    @State private var showFieldSheet = false
    @State private var activeEditorKey: String?

    private var hasChanges: Bool {
        UDFEditorService.fingerprint(document) != originalFingerprint
    }

    private var canSave: Bool {
        sourceURL != nil
            && hasChanges
            && !document.plainText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !isSaving
    }

    var body: some View {
        VStack(spacing: 0) {
            fileHeader

            if isLoading {
                Spacer()
                ProgressView("Belge yükleniyor...")
                Spacer()
            } else if sourceURL != nil {
                if hasSignature { signatureBanner }

                RichTextFormattingToolbar(proxy: editorProxy) {
                    showFieldSheet = true
                }

                ScrollView {
                    LazyVStack(spacing: 14) {
                        HeaderFooterSectionEditor(
                            title: "Üst Bilgi (Header)",
                            sections: $document.headers,
                            proxy: editorProxy,
                            activeKey: $activeEditorKey,
                            keyPrefix: "header",
                            onChange: markDirty
                        )

                        ForEach(Array(document.blocks.enumerated()), id: \.element.id) { index, block in
                            blockView(at: index, block: block)
                        }

                        HeaderFooterSectionEditor(
                            title: "Alt Bilgi (Footer)",
                            sections: $document.footers,
                            proxy: editorProxy,
                            activeKey: $activeEditorKey,
                            keyPrefix: "footer",
                            onChange: markDirty
                        )

                        insertButtons
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }

                bottomBar
            } else {
                Spacer()
                ContentUnavailableView(
                    "UDF Seçin",
                    systemImage: "doc.fill",
                    description: Text("Düzenlemek istediğiniz UDF dosyasını seçin.")
                )
                Spacer()
            }
        }
        .background(AppTheme.pageBg)
        .navigationTitle("UDF Düzenleme")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if sourceURL != nil {
                    Button("Dosya Değiştir") {
                        if hasChanges { showDiscardAlert = true } else { showPicker = true }
                    }
                    .font(.subheadline)
                }
            }
        }
        .sheet(isPresented: $showPicker) {
            ToolDocumentPicker(types: [.udfType]) { urls in
                showPicker = false
                guard let first = urls.first else { return }
                loadDocument(from: first)
            }
        }
        .sheet(isPresented: $showFieldSheet) {
            InsertFieldSheet { name in
                editorProxy.markField(name: name)
            }
        }
        .sheet(item: $shareURL) { url in
            ActivityViewController(activityItems: [url])
        }
        .navigationDestination(item: $previewURL) { url in
            DocumentPreviewView(url: url)
        }
        .alert("Değişiklikler Kaybolacak", isPresented: $showDiscardAlert) {
            Button("Vazgeç", role: .cancel) {}
            Button("Dosya Değiştir", role: .destructive) {
                resetEditor()
                showPicker = true
            }
        } message: {
            Text("Başka bir dosya seçerseniz kaydedilmemiş değişiklikler silinir.")
        }
        .onAppear {
            if sourceURL == nil { showPicker = true }
        }
    }

    // MARK: - Blok görünümleri

    @ViewBuilder
    private func blockView(at index: Int, block: UDFEditBlock) -> some View {
        switch block {
        case .paragraph:
            ParagraphBlockEditor(
                paragraph: paragraphBinding(at: index),
                proxy: editorProxy,
                isActive: activeEditorKey == "body-\(index)",
                onActivate: { activeEditorKey = "body-\(index)" },
                onChange: markDirty
            )
        case .table:
            TableBlockEditor(
                table: tableBinding(at: index),
                proxy: editorProxy,
                activeCellKey: $activeEditorKey,
                blockKey: "table-\(index)",
                onChange: markDirty
            )
        }
    }

    private var insertButtons: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                Button {
                    document.blocks.append(.paragraph(UDFEditParagraph(runs: [UDFEditRun(text: "")])))
                    markDirty()
                } label: {
                    Label("Paragraf", systemImage: "text.alignleft")
                        .font(.caption).bold()
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button {
                    let table = UDFEditTable(
                        columnCount: 2,
                        rows: [
                            UDFEditTableRow(cells: [
                                UDFEditTableCell(paragraphs: [UDFEditParagraph(runs: [UDFEditRun(text: "")])]),
                                UDFEditTableCell(paragraphs: [UDFEditParagraph(runs: [UDFEditRun(text: "")])])
                            ])
                        ]
                    )
                    document.blocks.append(.table(table))
                    markDirty()
                } label: {
                    Label("Tablo", systemImage: "tablecells")
                        .font(.caption).bold()
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }

            HStack(spacing: 10) {
                Button {
                    document.headers.append(UDFEditHeaderFooter())
                    markDirty()
                } label: {
                    Label("Üst Bilgi", systemImage: "arrow.up.doc")
                        .font(.caption).bold()
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button {
                    document.footers.append(UDFEditHeaderFooter())
                    markDirty()
                } label: {
                    Label("Alt Bilgi", systemImage: "arrow.down.doc")
                        .font(.caption).bold()
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
        }
    }

    // MARK: - Bindings

    private func paragraphBinding(at index: Int) -> Binding<UDFEditParagraph> {
        Binding(
            get: {
                if case .paragraph(let p) = document.blocks[index] { return p }
                return UDFEditParagraph()
            },
            set: { document.blocks[index] = .paragraph($0) }
        )
    }

    private func tableBinding(at index: Int) -> Binding<UDFEditTable> {
        Binding(
            get: {
                if case .table(let t) = document.blocks[index] { return t }
                return UDFEditTable()
            },
            set: { document.blocks[index] = .table($0) }
        )
    }

    private func markDirty() {}

    // MARK: - Alt bileşenler

    private var fileHeader: some View {
        Group {
            if let sourceURL {
                HStack(spacing: 10) {
                    Image(systemName: "doc.fill")
                        .foregroundStyle(AppTheme.navy)
                    Text(sourceURL.lastPathComponent)
                        .font(.subheadline).bold()
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color(.systemBackground))
            }
        }
    }

    private var signatureBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
            Text("Bu dosyada elektronik imza var. Kaydettiğinizde imza geçersiz olur.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.12))
    }

    private var bottomBar: some View {
        VStack(spacing: 10) {
            if let errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }

            if let resultURL {
                HStack(spacing: 10) {
                    Button { previewURL = resultURL } label: {
                        Label("Görüntüle", systemImage: "eye").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    Button { shareURL = resultURL } label: {
                        Label("Paylaş", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }

            Button { save() } label: {
                if isSaving {
                    HStack {
                        ProgressView()
                        Text("Kaydediliyor...")
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    Label("UDF Olarak Kaydet", systemImage: "square.and.arrow.down.fill")
                        .bold()
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.navy)
            .disabled(!canSave)

            if hasChanges {
                Text("Kaydedilmemiş değişiklikler var.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color(.systemBackground))
    }

    // MARK: - İşlemler

    private func loadDocument(from url: URL) {
        isLoading = true
        errorMessage = nil
        resultURL = nil
        sourceURL = url

        Task {
            do {
                let doc = try await Task.detached(priority: .userInitiated) {
                    try UDFEditorService.load(from: url)
                }.value
                await MainActor.run {
                    baseName = doc.baseName
                    document = doc.model
                    originalFingerprint = UDFEditorService.fingerprint(doc.model)
                    hasSignature = doc.hasSignature
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                    sourceURL = nil
                }
            }
        }
    }

    private func save() {
        isSaving = true
        errorMessage = nil
        let model = document
        let name = baseName

        Task {
            do {
                let output = try await Task.detached(priority: .userInitiated) {
                    try UDFEditorService.save(model: model, baseName: name)
                }.value
                await MainActor.run {
                    resultURL = output
                    originalFingerprint = UDFEditorService.fingerprint(model)
                    isSaving = false
                    ConversionStorage.shared.addRecord(
                        ConversionRecord(
                            originalFileName: output.lastPathComponent,
                            outputFormat: "UDF",
                            success: true,
                            outputPath: output.path
                        )
                    )
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isSaving = false
                }
            }
        }
    }

    private func resetEditor() {
        sourceURL = nil
        baseName = ""
        document = UDFEditDocument(blocks: [])
        originalFingerprint = ""
        hasSignature = false
        resultURL = nil
        errorMessage = nil
        activeEditorKey = nil
    }
}
