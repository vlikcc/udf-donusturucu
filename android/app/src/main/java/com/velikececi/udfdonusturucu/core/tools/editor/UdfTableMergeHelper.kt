package com.velikececi.udfdonusturucu.core.tools.editor

/**
 * UDFTableMergeHelper.swift'in Kotlin karşılığı — tablo hücre birleştirme (colspan/rowspan)
 * yardımcıları. Swift'in `inout` mutasyonu yerine, veri modeli değişmez (immutable) olduğundan
 * her fonksiyon güncellenmiş tabloyu YENİ bir değer olarak döndürür.
 */
object UdfTableMergeHelper {

    fun columnStart(row: UdfEditTableRow, cellIndex: Int): Int =
        row.cells.take(cellIndex).sumOf { maxOf(it.colspan, 1) }

    fun logicalColumnCount(table: UdfEditTable): Int {
        val maxRowSpan = table.rows.maxOfOrNull { row -> row.cells.sumOf { maxOf(it.colspan, 1) } } ?: 1
        return maxOf(table.columnCount, maxRowSpan)
    }

    fun mergeRight(table: UdfEditTable, rowIndex: Int, cellIndex: Int): UdfEditTable {
        if (rowIndex !in table.rows.indices) return table
        val row = table.rows[rowIndex]
        if (cellIndex + 1 !in row.cells.indices) return table

        val left = row.cells[cellIndex]
        val right = row.cells[cellIndex + 1]

        val merged = appendParagraphs(
            from = right,
            into = left.copy(
                colspan = maxOf(left.colspan, 1) + maxOf(right.colspan, 1),
                rowspan = maxOf(left.rowspan, right.rowspan),
            ),
        )

        val newCells = row.cells.toMutableList()
        newCells[cellIndex] = merged
        newCells.removeAt(cellIndex + 1)

        return table.replacingRow(rowIndex, row.copy(cells = newCells))
    }

    fun mergeDown(table: UdfEditTable, rowIndex: Int, cellIndex: Int): UdfEditTable {
        val below = rowIndex + 1
        if (rowIndex !in table.rows.indices || below !in table.rows.indices) return table

        val startCol = columnStart(table.rows[rowIndex], cellIndex)
        val belowIndex = cellIndexAtColumn(startCol, table.rows[below]) ?: return table

        val top = table.rows[rowIndex].cells.getOrNull(cellIndex) ?: return table
        val bottom = table.rows[below].cells.getOrNull(belowIndex) ?: return table

        if (maxOf(top.colspan, 1) != maxOf(bottom.colspan, 1)) return table

        val merged = appendParagraphs(from = bottom, into = top.copy(rowspan = maxOf(top.rowspan, 1) + maxOf(bottom.rowspan, 1)))

        val topRowCells = table.rows[rowIndex].cells.toMutableList().apply { this[cellIndex] = merged }
        val belowRowCells = table.rows[below].cells.toMutableList().apply { removeAt(belowIndex) }

        var newTable = table.replacingRow(rowIndex, table.rows[rowIndex].copy(cells = topRowCells))
        newTable = newTable.replacingRow(below, newTable.rows[below].copy(cells = belowRowCells))
        return newTable
    }

    fun splitCell(table: UdfEditTable, rowIndex: Int, cellIndex: Int): UdfEditTable {
        if (rowIndex !in table.rows.indices) return table
        val row = table.rows[rowIndex]
        if (cellIndex !in row.cells.indices) return table
        val newCells = row.cells.toMutableList()
        newCells[cellIndex] = newCells[cellIndex].copy(colspan = 1, rowspan = 1)
        return table.replacingRow(rowIndex, row.copy(cells = newCells))
    }

    fun setColspan(table: UdfEditTable, rowIndex: Int, cellIndex: Int, colspan: Int): UdfEditTable {
        if (rowIndex !in table.rows.indices) return table
        val row = table.rows[rowIndex]
        if (cellIndex !in row.cells.indices) return table
        val maxCols = logicalColumnCount(table)
        val start = columnStart(row, cellIndex)
        val remaining = maxCols - start
        val newCells = row.cells.toMutableList()
        newCells[cellIndex] = newCells[cellIndex].copy(colspan = colspan.coerceIn(1, maxOf(remaining, 1)))
        return table.replacingRow(rowIndex, row.copy(cells = newCells))
    }

    fun setRowspan(table: UdfEditTable, rowIndex: Int, cellIndex: Int, rowspan: Int): UdfEditTable {
        if (rowIndex !in table.rows.indices) return table
        val row = table.rows[rowIndex]
        if (cellIndex !in row.cells.indices) return table
        val remaining = table.rows.size - rowIndex
        val newCells = row.cells.toMutableList()
        newCells[cellIndex] = newCells[cellIndex].copy(rowspan = rowspan.coerceIn(1, maxOf(remaining, 1)))
        return table.replacingRow(rowIndex, row.copy(cells = newCells))
    }

    private fun cellIndexAtColumn(column: Int, row: UdfEditTableRow): Int? {
        var col = 0
        row.cells.forEachIndexed { index, cell ->
            if (col == column) return index
            col += maxOf(cell.colspan, 1)
        }
        return null
    }

    private fun appendParagraphs(from: UdfEditTableCell, into: UdfEditTableCell): UdfEditTableCell {
        val extraText = from.paragraphs.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }
        if (extraText.isEmpty()) return into

        if (into.paragraphs.isEmpty()) {
            return into.copy(paragraphs = listOf(UdfEditParagraph(runs = listOf(UdfEditRun(text = extraText)))))
        }

        val primary = into.paragraphs[0]
        val existing = primary.runs.joinToString("") { it.text }
        val combined = if (existing.isEmpty()) extraText else "$existing\n$extraText"
        val newParagraphs = into.paragraphs.toMutableList()
        newParagraphs[0] = primary.copy(runs = listOf(UdfEditRun(text = combined)))
        return into.copy(paragraphs = newParagraphs)
    }

    private fun UdfEditTable.replacingRow(index: Int, newRow: UdfEditTableRow): UdfEditTable {
        val newRows = rows.toMutableList()
        newRows[index] = newRow
        return copy(rows = newRows)
    }
}
