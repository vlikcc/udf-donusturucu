package com.velikececi.udfdonusturucu.core.tools.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdfStructureWriterTest {

    @Test
    fun `parse yaz parse round-trip parmak izi esit kalir`() {
        val original = UdfEditDocument(
            blocks = listOf(
                UdfEditBlock.ParagraphBlock(
                    UdfEditParagraph(
                        alignment = 1,
                        runs = listOf(
                            UdfEditRun(text = "Kalın ", isBold = true),
                            UdfEditRun(text = "normal"),
                        ),
                    ),
                ),
                UdfEditBlock.TableBlock(
                    UdfEditTable(
                        columnCount = 2,
                        rows = listOf(
                            UdfEditTableRow(
                                cells = listOf(
                                    UdfEditTableCell(paragraphs = listOf(UdfEditParagraph(runs = listOf(UdfEditRun(text = "H1"))))),
                                    UdfEditTableCell(paragraphs = listOf(UdfEditParagraph(runs = listOf(UdfEditRun(text = "H2"))))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val built = UdfStructureWriter.build(original)
        // parse(), UdfCreator'ın gerçek content.xml'inde olduğu gibi <elements> ile sarmalanmış
        // bir XML bekler — BuildResult.elementsXML yalnızca iç içeriktir (bkz. UdfCreator.buildContentXmlFromElements).
        val wrappedXml = "<elements >${built.elementsXML}</elements>"
        val reparsed = UdfStructureParser.parse(wrappedXml, built.cdataText)
        val rebuilt = UdfStructureWriter.build(reparsed)

        assertEquals(built.cdataText, rebuilt.cdataText)
        assertEquals(UdfEditorService.fingerprint(original), UdfEditorService.fingerprint(reparsed))
    }

    @Test
    fun `varsayilan 12 punto boyutu yazilmaz`() {
        val document = UdfEditDocument(
            blocks = listOf(UdfEditBlock.ParagraphBlock(UdfEditParagraph(runs = listOf(UdfEditRun(text = "metin", fontSize = 12f))))),
        )

        val built = UdfStructureWriter.build(document)

        assertFalse(built.elementsXML.contains("size=\""))
    }

    @Test
    fun `12 disi punto boyutu yazilir`() {
        val document = UdfEditDocument(
            blocks = listOf(UdfEditBlock.ParagraphBlock(UdfEditParagraph(runs = listOf(UdfEditRun(text = "metin", fontSize = 16f))))),
        )

        val built = UdfStructureWriter.build(document)

        assertTrue(built.elementsXML.contains("size=\"16\""))
    }

    @Test
    fun `bos paragraf sifir uzunluklu content ile yazilir ve bir satir tuketir`() {
        val document = UdfEditDocument(blocks = listOf(UdfEditBlock.ParagraphBlock(UdfEditParagraph(runs = emptyList()))))

        val built = UdfStructureWriter.build(document)

        assertTrue(built.elementsXML.contains("length=\"0\""))
        assertEquals("\n", built.cdataText)
    }

    @Test
    fun `alan calismasi fieldName ile geri yazilir`() {
        val document = UdfEditDocument(
            blocks = listOf(
                UdfEditBlock.ParagraphBlock(
                    UdfEditParagraph(runs = listOf(UdfEditRun(kind = UdfRunKind.Field("TARIH"), text = "01.01.2026"))),
                ),
            ),
        )

        val built = UdfStructureWriter.build(document)

        assertTrue(built.elementsXML.contains("<field"))
        assertTrue(built.elementsXML.contains("fieldName=\"TARIH\""))
    }

    @Test
    fun `header ve footer olmayan belgede null doner`() {
        val document = UdfEditDocument(blocks = listOf(UdfEditBlock.ParagraphBlock(UdfEditParagraph(runs = listOf(UdfEditRun(text = "x"))))))

        val built = UdfStructureWriter.build(document)

        assertEquals(null, built.headersXML)
        assertEquals(null, built.footersXML)
    }

    @Test
    fun `ofsetler artan ve paragraflar arasi bosluk tutarlidir`() {
        val document = UdfEditDocument(
            blocks = listOf(
                UdfEditBlock.ParagraphBlock(UdfEditParagraph(runs = listOf(UdfEditRun(text = "ilk")))),
                UdfEditBlock.ParagraphBlock(UdfEditParagraph(runs = listOf(UdfEditRun(text = "ikinci")))),
            ),
        )

        val built = UdfStructureWriter.build(document)

        // "ilk\nikinci\n" — ilk paragraf 3 karakter + 1 yeni satır, ikinci 6. karakterden başlar.
        assertEquals("ilk\nikinci\n", built.cdataText)
        assertTrue(built.elementsXML.contains("startOffset=\"0\" length=\"3\""))
        assertTrue(built.elementsXML.contains("startOffset=\"4\" length=\"6\""))
    }
}
