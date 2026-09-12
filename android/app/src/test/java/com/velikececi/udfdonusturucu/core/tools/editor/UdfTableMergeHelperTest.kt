package com.velikececi.udfdonusturucu.core.tools.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdfTableMergeHelperTest {

    private fun cell(text: String, colspan: Int = 1, rowspan: Int = 1) = UdfEditTableCell(
        colspan = colspan,
        rowspan = rowspan,
        paragraphs = listOf(UdfEditParagraph(runs = listOf(UdfEditRun(text = text)))),
    )

    private fun table2x2() = UdfEditTable(
        columnCount = 2,
        rows = listOf(
            UdfEditTableRow(cells = listOf(cell("A"), cell("B"))),
            UdfEditTableRow(cells = listOf(cell("C"), cell("D"))),
        ),
    )

    @Test
    fun `mergeRight komsu hucreyi birlestirir ve metni tasir`() {
        val merged = UdfTableMergeHelper.mergeRight(table2x2(), rowIndex = 0, cellIndex = 0)

        assertEquals(1, merged.rows[0].cells.size)
        assertEquals(2, merged.rows[0].cells[0].colspan)
        assertEquals("A\nB", merged.rows[0].cells[0].primaryParagraphValue().runs.first().text)
        // Diğer satır etkilenmez.
        assertEquals(2, merged.rows[1].cells.size)
    }

    @Test
    fun `mergeDown esit colspan ile alttaki hucreyi birlestirir`() {
        val merged = UdfTableMergeHelper.mergeDown(table2x2(), rowIndex = 0, cellIndex = 0)

        assertEquals(2, merged.rows[0].cells[0].rowspan)
        assertEquals("A\nC", merged.rows[0].cells[0].primaryParagraphValue().runs.first().text)
        assertEquals(1, merged.rows[1].cells.size)
    }

    @Test
    fun `farkli colspan'de mergeDown hicbir sey degistirmez`() {
        val wide = UdfEditTable(
            columnCount = 2,
            rows = listOf(
                UdfEditTableRow(cells = listOf(cell("A", colspan = 2))),
                UdfEditTableRow(cells = listOf(cell("C"), cell("D"))),
            ),
        )

        val result = UdfTableMergeHelper.mergeDown(wide, rowIndex = 0, cellIndex = 0)

        assertEquals(wide, result)
    }

    @Test
    fun `splitCell birlesmis hucreyi 1x1'e dondurur`() {
        val merged = UdfTableMergeHelper.mergeRight(table2x2(), rowIndex = 0, cellIndex = 0)
        val split = UdfTableMergeHelper.splitCell(merged, rowIndex = 0, cellIndex = 0)

        assertEquals(1, split.rows[0].cells[0].colspan)
        assertEquals(1, split.rows[0].cells[0].rowspan)
    }

    @Test
    fun `columnStart onceki hucrelerin colspan toplamidir`() {
        val row = UdfEditTableRow(cells = listOf(cell("A", colspan = 2), cell("B"), cell("C")))

        assertEquals(0, UdfTableMergeHelper.columnStart(row, cellIndex = 0))
        assertEquals(2, UdfTableMergeHelper.columnStart(row, cellIndex = 1))
        assertEquals(3, UdfTableMergeHelper.columnStart(row, cellIndex = 2))
    }

    @Test
    fun `setColspan kalan sutunla sinirlanir`() {
        val result = UdfTableMergeHelper.setColspan(table2x2(), rowIndex = 0, cellIndex = 0, colspan = 99)

        // logicalColumnCount = 2, columnStart(0) = 0 → kalan 2, en fazla 2 olabilir.
        assertEquals(2, result.rows[0].cells[0].colspan)
    }

    @Test
    fun `setRowspan kalan satirla sinirlanir`() {
        val result = UdfTableMergeHelper.setRowspan(table2x2(), rowIndex = 0, cellIndex = 0, rowspan = 99)

        assertEquals(2, result.rows[0].cells[0].rowspan)
    }

    @Test
    fun `logicalColumnCount colspan toplamlarinin en genisiyle belirlenir`() {
        val table = UdfEditTable(
            columnCount = 2,
            rows = listOf(UdfEditTableRow(cells = listOf(cell("A", colspan = 3)))),
        )

        assertTrue(UdfTableMergeHelper.logicalColumnCount(table) == 3)
    }
}
