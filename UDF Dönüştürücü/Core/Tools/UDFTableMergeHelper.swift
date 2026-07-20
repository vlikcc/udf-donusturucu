import Foundation

/// Tablo hücre birleştirme (colspan/rowspan) yardımcıları.
enum UDFTableMergeHelper {

    static func columnStart(in row: UDFEditTableRow, cellIndex: Int) -> Int {
        row.cells.prefix(cellIndex).reduce(0) { $0 + max($1.colspan, 1) }
    }

    static func logicalColumnCount(_ table: UDFEditTable) -> Int {
        max(
            table.columnCount,
            table.rows.map { row in row.cells.reduce(0) { $0 + max($1.colspan, 1) } }.max() ?? 1
        )
    }

    static func mergeRight(table: inout UDFEditTable, rowIndex: Int, cellIndex: Int) {
        guard rowIndex < table.rows.count,
              cellIndex + 1 < table.rows[rowIndex].cells.count else { return }

        var left = table.rows[rowIndex].cells[cellIndex]
        let right = table.rows[rowIndex].cells[cellIndex + 1]

        left.colspan = max(left.colspan, 1) + max(right.colspan, 1)
        left.rowspan = max(left.rowspan, right.rowspan)
        appendParagraphs(from: right, into: &left)

        table.rows[rowIndex].cells[cellIndex] = left
        table.rows[rowIndex].cells.remove(at: cellIndex + 1)
    }

    static func mergeDown(table: inout UDFEditTable, rowIndex: Int, cellIndex: Int) {
        let below = rowIndex + 1
        guard rowIndex < table.rows.count, below < table.rows.count else { return }

        let startCol = columnStart(in: table.rows[rowIndex], cellIndex: cellIndex)
        guard let belowIndex = cellIndexAtColumn(startCol, in: table.rows[below]) else { return }

        var top = table.rows[rowIndex].cells[cellIndex]
        let bottom = table.rows[below].cells[belowIndex]

        guard max(top.colspan, 1) == max(bottom.colspan, 1) else { return }

        top.rowspan = max(top.rowspan, 1) + max(bottom.rowspan, 1)
        appendParagraphs(from: bottom, into: &top)

        table.rows[rowIndex].cells[cellIndex] = top
        table.rows[below].cells.remove(at: belowIndex)
    }

    static func splitCell(table: inout UDFEditTable, rowIndex: Int, cellIndex: Int) {
        guard rowIndex < table.rows.count, cellIndex < table.rows[rowIndex].cells.count else { return }
        table.rows[rowIndex].cells[cellIndex].colspan = 1
        table.rows[rowIndex].cells[cellIndex].rowspan = 1
    }

    static func setColspan(table: inout UDFEditTable, rowIndex: Int, cellIndex: Int, colspan: Int) {
        guard rowIndex < table.rows.count, cellIndex < table.rows[rowIndex].cells.count else { return }
        let maxCols = logicalColumnCount(table)
        let start = columnStart(in: table.rows[rowIndex], cellIndex: cellIndex)
        let remaining = maxCols - start
        table.rows[rowIndex].cells[cellIndex].colspan = min(max(colspan, 1), max(remaining, 1))
    }

    static func setRowspan(table: inout UDFEditTable, rowIndex: Int, cellIndex: Int, rowspan: Int) {
        guard rowIndex < table.rows.count, cellIndex < table.rows[rowIndex].cells.count else { return }
        let remaining = table.rows.count - rowIndex
        table.rows[rowIndex].cells[cellIndex].rowspan = min(max(rowspan, 1), max(remaining, 1))
    }

    private static func cellIndexAtColumn(_ column: Int, in row: UDFEditTableRow) -> Int? {
        var col = 0
        for (index, cell) in row.cells.enumerated() {
            if col == column { return index }
            col += max(cell.colspan, 1)
        }
        return nil
    }

    private static func appendParagraphs(from source: UDFEditTableCell, into target: inout UDFEditTableCell) {
        let extraText = source.paragraphs.map { $0.runs.map(\.text).joined() }.joined(separator: "\n")
        guard !extraText.isEmpty else { return }

        if target.paragraphs.isEmpty {
            target.paragraphs = [UDFEditParagraph(runs: [UDFEditRun(text: extraText)])]
            return
        }

        var primary = target.paragraphs[0]
        let existing = primary.runs.map(\.text).joined()
        let combined = existing.isEmpty ? extraText : existing + "\n" + extraText
        primary.runs = [UDFEditRun(text: combined)]
        target.paragraphs[0] = primary
    }
}
