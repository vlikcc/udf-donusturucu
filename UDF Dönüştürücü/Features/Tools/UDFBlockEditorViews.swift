import SwiftUI

// MARK: - Paragraf bloğu

struct ParagraphBlockEditor: View {
    @Binding var paragraph: UDFEditParagraph
    var proxy: RichTextEditorProxy
    var isActive: Bool
    var onActivate: () -> Void
    var onChange: () -> Void

    @State private var attributedContent = NSAttributedString()

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Paragraf")
                .font(.caption2).bold()
                .foregroundStyle(.secondary)

            RichTextEditorView(
                attributedText: $attributedContent,
                proxy: proxy,
                isEditable: true,
                onChange: syncFromEditor
            )
            .frame(minHeight: 100)
            .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isActive ? AppTheme.navy : Color.clear, lineWidth: 2)
            )
            .onTapGesture { onActivate() }
        }
        .onAppear { reloadFromModel() }
        .onChange(of: paragraph.runs) { _, _ in reloadFromModel() }
    }

    private func reloadFromModel() {
        let fresh = UDFAttributedConverter.attributedString(from: paragraph)
        if !attributedContent.isEqual(to: fresh) {
            attributedContent = fresh
        }
    }

    private func syncFromEditor() {
        paragraph = UDFAttributedConverter.paragraph(from: attributedContent, base: paragraph)
        onChange()
    }
}

// MARK: - Üst / alt bilgi

struct HeaderFooterSectionEditor: View {
    let title: String
    @Binding var sections: [UDFEditHeaderFooter]
    var proxy: RichTextEditorProxy
    @Binding var activeKey: String?
    let keyPrefix: String
    var onChange: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title)
                    .font(.caption).bold()
                    .foregroundStyle(AppTheme.navy)
                Spacer()
                Button {
                    sections.append(UDFEditHeaderFooter())
                    onChange()
                } label: {
                    Label("Ekle", systemImage: "plus")
                        .font(.caption)
                }
            }

            if sections.isEmpty {
                Text("Henüz \(title.lowercased()) yok.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            ForEach(Array(sections.enumerated()), id: \.element.id) { index, _ in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("\(title) \(index + 1)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button(role: .destructive) {
                            sections.remove(at: index)
                            onChange()
                        } label: {
                            Image(systemName: "trash")
                                .font(.caption)
                        }
                    }

                    ForEach(Array(sections[index].paragraphs.enumerated()), id: \.element.id) { paraIndex, _ in
                        ParagraphBlockEditor(
                            paragraph: paragraphBinding(sectionIndex: index, paragraphIndex: paraIndex),
                            proxy: proxy,
                            isActive: activeKey == "\(keyPrefix)-\(index)-\(paraIndex)",
                            onActivate: { activeKey = "\(keyPrefix)-\(index)-\(paraIndex)" },
                            onChange: onChange
                        )
                    }

                    Button {
                        sections[index].paragraphs.append(UDFEditParagraph(runs: [UDFEditRun(text: "")]))
                        onChange()
                    } label: {
                        Label("Paragraf Ekle", systemImage: "text.append")
                            .font(.caption)
                    }
                }
                .padding(10)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private func paragraphBinding(sectionIndex: Int, paragraphIndex: Int) -> Binding<UDFEditParagraph> {
        Binding(
            get: { sections[sectionIndex].paragraphs[paragraphIndex] },
            set: { sections[sectionIndex].paragraphs[paragraphIndex] = $0 }
        )
    }
}

// MARK: - Tablo hücresi (zengin metin)

struct TableCellRichEditor: View {
    @Binding var cell: UDFEditTableCell
    var proxy: RichTextEditorProxy
    var isActive: Bool
    var onActivate: () -> Void
    var onChange: () -> Void

    @State private var attributedContent = NSAttributedString()

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let fill = cell.fillColorARGB {
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color(uiColor: UDFColorCodec.uiColor(fromJavaARGB: fill)))
                    .frame(height: 3)
            }

            RichTextEditorView(
                attributedText: $attributedContent,
                proxy: proxy,
                isEditable: true,
                onChange: syncFromEditor
            )
            .frame(minHeight: 72)
            .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 6))
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(isActive ? AppTheme.navy : Color(.separator), lineWidth: isActive ? 2 : 0.5)
            )
            .onTapGesture { onActivate() }
        }
        .onAppear { reloadFromModel() }
        .onChange(of: cell.paragraphs) { _, _ in reloadFromModel() }
    }

    private func reloadFromModel() {
        let fresh = UDFAttributedConverter.attributedString(from: cell.primaryParagraphValue())
        if !attributedContent.isEqual(to: fresh) {
            attributedContent = fresh
        }
    }

    private func syncFromEditor() {
        var updated = cell
        updated.setPrimaryParagraph(
            UDFAttributedConverter.paragraph(from: attributedContent, base: updated.primaryParagraphValue())
        )
        cell = updated
        onChange()
    }
}

// MARK: - Tablo bloğu

struct TableBlockEditor: View {
    @Binding var table: UDFEditTable
    var proxy: RichTextEditorProxy
    @Binding var activeCellKey: String?
    let blockKey: String
    var onChange: () -> Void

    @State private var mergeTarget: MergeTarget?

    private struct MergeTarget: Identifiable {
        let id = UUID()
        let rowIndex: Int
        let cellIndex: Int
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Tablo (\(table.rows.count) satır)")
                    .font(.caption2).bold()
                    .foregroundStyle(.secondary)
                Spacer()
                Menu {
                    Button("Satır Ekle") { addRow(); onChange() }
                    Button("Sütun Ekle") { addColumn(); onChange() }
                } label: {
                    Image(systemName: "plus.circle")
                        .foregroundStyle(AppTheme.navy)
                }
            }

            ForEach(Array(table.rows.enumerated()), id: \.element.id) { rowIndex, row in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Satır \(rowIndex + 1)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button {
                            table.rows.remove(at: rowIndex)
                            onChange()
                        } label: {
                            Image(systemName: "minus.circle.fill")
                                .foregroundStyle(.red.opacity(0.7))
                        }
                    }

                    HStack(alignment: .top, spacing: 6) {
                        ForEach(Array(row.cells.enumerated()), id: \.element.id) { cellIndex, cell in
                            VStack(spacing: 4) {
                                HStack(spacing: 4) {
                                    if cell.colspan > 1 {
                                        badge("C\(cell.colspan)")
                                    }
                                    if cell.rowspan > 1 {
                                        badge("R\(cell.rowspan)")
                                    }
                                    Spacer(minLength: 0)
                                    Button {
                                        mergeTarget = MergeTarget(rowIndex: rowIndex, cellIndex: cellIndex)
                                    } label: {
                                        Image(systemName: "square.split.2x1")
                                            .font(.caption2)
                                    }
                                }

                                TableCellRichEditor(
                                    cell: cellBinding(rowIndex: rowIndex, cellIndex: cellIndex),
                                    proxy: proxy,
                                    isActive: activeCellKey == "\(blockKey)-\(rowIndex)-\(cellIndex)",
                                    onActivate: {
                                        activeCellKey = "\(blockKey)-\(rowIndex)-\(cellIndex)"
                                    },
                                    onChange: onChange
                                )
                            }
                            .frame(maxWidth: .infinity)
                            .layoutPriority(Double(max(cell.colspan, 1)))
                        }
                    }
                }
            }
        }
        .padding(10)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .sheet(item: $mergeTarget) { target in
            CellMergeSheet(
                table: table,
                rowIndex: target.rowIndex,
                cellIndex: target.cellIndex
            ) { updated in
                table = updated
                onChange()
            }
        }
    }

    private func badge(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 9, weight: .bold))
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(AppTheme.navy.opacity(0.12), in: Capsule())
            .foregroundStyle(AppTheme.navy)
    }

    private func cellBinding(rowIndex: Int, cellIndex: Int) -> Binding<UDFEditTableCell> {
        Binding(
            get: { table.rows[rowIndex].cells[cellIndex] },
            set: { table.rows[rowIndex].cells[cellIndex] = $0 }
        )
    }

    private func addRow() {
        let colCount = max(UDFTableMergeHelper.logicalColumnCount(table), 1)
        let cells = (0..<colCount).map { _ in
            UDFEditTableCell(paragraphs: [UDFEditParagraph(runs: [UDFEditRun(text: "")])])
        }
        table.rows.append(UDFEditTableRow(cells: cells))
    }

    private func addColumn() {
        table.columnCount += 1
        for index in table.rows.indices {
            table.rows[index].cells.append(
                UDFEditTableCell(paragraphs: [UDFEditParagraph(runs: [UDFEditRun(text: "")])])
            )
        }
    }
}

// MARK: - Hücre birleştirme sheet

struct CellMergeSheet: View {
    @Environment(\.dismiss) private var dismiss

    let table: UDFEditTable
    let rowIndex: Int
    let cellIndex: Int
    let onApply: (UDFEditTable) -> Void

    @State private var colspan: Int
    @State private var rowspan: Int

    init(table: UDFEditTable, rowIndex: Int, cellIndex: Int, onApply: @escaping (UDFEditTable) -> Void) {
        self.table = table
        self.rowIndex = rowIndex
        self.cellIndex = cellIndex
        self.onApply = onApply
        let cell = table.rows[rowIndex].cells[cellIndex]
        _colspan = State(initialValue: max(cell.colspan, 1))
        _rowspan = State(initialValue: max(cell.rowspan, 1))
    }

    private var canMergeRight: Bool {
        cellIndex + 1 < table.rows[rowIndex].cells.count
    }

    private var canMergeDown: Bool {
        rowIndex + 1 < table.rows.count
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Boyut") {
                    Stepper("Colspan: \(colspan)", value: $colspan, in: 1...max(UDFTableMergeHelper.logicalColumnCount(table), 1))
                    Stepper("Rowspan: \(rowspan)", value: $rowspan, in: 1...max(table.rows.count - rowIndex, 1))
                }

                Section("Hızlı Birleştirme") {
                    Button("Sağdaki Hücreyle Birleştir (colspan)") {
                        apply { UDFTableMergeHelper.mergeRight(table: &$0, rowIndex: rowIndex, cellIndex: cellIndex) }
                    }
                    .disabled(!canMergeRight)

                    Button("Alttaki Hücreyle Birleştir (rowspan)") {
                        apply { UDFTableMergeHelper.mergeDown(table: &$0, rowIndex: rowIndex, cellIndex: cellIndex) }
                    }
                    .disabled(!canMergeDown)

                    Button("Birleştirmeyi Kaldır", role: .destructive) {
                        apply { UDFTableMergeHelper.splitCell(table: &$0, rowIndex: rowIndex, cellIndex: cellIndex) }
                    }
                }
            }
            .navigationTitle("Hücre Birleştir")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Uygula") {
                        apply { table in
                            UDFTableMergeHelper.setColspan(table: &table, rowIndex: rowIndex, cellIndex: cellIndex, colspan: colspan)
                            UDFTableMergeHelper.setRowspan(table: &table, rowIndex: rowIndex, cellIndex: cellIndex, rowspan: rowspan)
                        }
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func apply(_ mutation: (inout UDFEditTable) -> Void) {
        var copy = table
        mutation(&copy)
        onApply(copy)
        dismiss()
    }
}

// MARK: - Alan ekleme sheet

struct InsertFieldSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var fieldName = ""
    let onInsert: (String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("Alan adı (ör. TARIH, AD_SOYAD)", text: $fieldName)
                    .textInputAutocapitalization(.characters)
            }
            .navigationTitle("UYAP Alanı Ekle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Ekle") {
                        let name = fieldName.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !name.isEmpty else { return }
                        onInsert(name)
                        dismiss()
                    }
                    .disabled(fieldName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
