package com.velikececi.udfdonusturucu.core.tools.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdfStructureParserTest {

    @Test
    fun `tek paragraf ve calisma parcalari offsetlerine gore dilimlenir`() {
        val plainText = "Merhaba Dünya"
        val rawXml = """
            <template><elements >
            <paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="3" Hanging="0.0"><content resolver="hvl-default" bold="true" startOffset="0" length="8" family="Times New Roman" /><content resolver="hvl-default" startOffset="8" length="5" family="Times New Roman" /></paragraph>
            </elements></template>
        """.trimIndent()

        val document = UdfStructureParser.parse(rawXml, plainText)

        assertEquals(1, document.blocks.size)
        val paragraph = (document.blocks[0] as UdfEditBlock.ParagraphBlock).paragraph
        assertEquals(3, paragraph.alignment)
        assertEquals(2, paragraph.runs.size)
        assertEquals("Merhaba ", paragraph.runs[0].text)
        assertTrue(paragraph.runs[0].isBold)
        assertEquals("Dünya", paragraph.runs[1].text)
        assertFalse(paragraph.runs[1].isBold)
    }

    @Test
    fun `field calismasi alan adiyla birlikte ayristirilir`() {
        val plainText = "Ad: AHMET"
        val rawXml = """
            <elements ><paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="3" Hanging="0.0"><content resolver="hvl-default" startOffset="0" length="4" family="Times New Roman" /><field resolver="hvl-default" fieldName="AD_SOYAD" startOffset="4" length="5" family="Times New Roman" /></paragraph></elements>
        """.trimIndent()

        val document = UdfStructureParser.parse(rawXml, plainText)
        val paragraph = (document.blocks[0] as UdfEditBlock.ParagraphBlock).paragraph

        assertEquals(2, paragraph.runs.size)
        assertFalse(paragraph.runs[0].isField)
        assertTrue(paragraph.runs[1].isField)
        assertEquals("AD_SOYAD", paragraph.runs[1].fieldName)
        assertEquals("AHMET", paragraph.runs[1].text)
    }

    @Test
    fun `tablo 2x2 satir ve hucreleriyle ayristirilir`() {
        val plainText = "A\nB\nC\nD"
        val rawXml = """
            <elements ><table tableName="Tablo" columnCount="2" border="borderCell">
              <row rowName="row" rowType="dataRow" border="borderCell">
                <cell colspan="1" rowspan="1" align="top" border="borderCell" borderSpec="15"><paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="3" Hanging="0.0"><content resolver="hvl-default" startOffset="0" length="1" family="Times New Roman" /></paragraph></cell>
                <cell colspan="1" rowspan="1" align="top" border="borderCell" borderSpec="15"><paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="3" Hanging="0.0"><content resolver="hvl-default" startOffset="2" length="1" family="Times New Roman" /></paragraph></cell>
              </row>
            </table></elements>
        """.trimIndent()

        val document = UdfStructureParser.parse(rawXml, plainText)
        val table = (document.blocks[0] as UdfEditBlock.TableBlock).table

        assertEquals(2, table.columnCount)
        assertEquals(1, table.rows.size)
        assertEquals(2, table.rows[0].cells.size)
        assertEquals("A", table.rows[0].cells[0].primaryParagraphValue().runs.first().text)
        assertEquals("B", table.rows[0].cells[1].primaryParagraphValue().runs.first().text)
    }

    @Test
    fun `elements etiketi yoksa duz metin satir satir paragrafa donusur`() {
        val document = UdfStructureParser.parse("", "Birinci satır\nİkinci satır")

        assertEquals(2, document.blocks.size)
        val first = (document.blocks[0] as UdfEditBlock.ParagraphBlock).paragraph
        assertEquals(3, first.alignment)
        assertEquals("Birinci satır", first.runs.first().text)
    }

    @Test
    fun `basliktan once tablo gelse bile hem tablo hem paragraf korunur`() {
        // iOS kaynağındaki bilinen sıralama hatasının (tablo paragraf'tan önce gelince
        // sessizce atlanması) burada oluşmadığını doğrular.
        val plainText = "Hücre\nParagraf metni"
        val rawXml = """
            <elements ><table tableName="Tablo" columnCount="1" border="borderCell">
              <row rowName="row" rowType="dataRow" border="borderCell">
                <cell colspan="1" rowspan="1" align="top" border="borderCell" borderSpec="15"><paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="3" Hanging="0.0"><content resolver="hvl-default" startOffset="0" length="5" family="Times New Roman" /></paragraph></cell>
              </row>
            </table>
            <paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="3" Hanging="0.0"><content resolver="hvl-default" startOffset="6" length="15" family="Times New Roman" /></paragraph>
            </elements>
        """.trimIndent()

        val document = UdfStructureParser.parse(rawXml, plainText)

        assertEquals(2, document.blocks.size)
        assertTrue(document.blocks[0] is UdfEditBlock.TableBlock)
        assertTrue(document.blocks[1] is UdfEditBlock.ParagraphBlock)
    }

    @Test
    fun `header ve footer konteynerleri ayristirilir`() {
        val plainText = "Üst Bilgi\nGövde\nAlt Bilgi"
        val rawXml = """
            <headers>
              <header type="default"><paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="1" Hanging="0.0"><content resolver="hvl-default" startOffset="0" length="9" family="Times New Roman" /></paragraph></header>
            </headers>
            <elements ><paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="3" Hanging="0.0"><content resolver="hvl-default" startOffset="10" length="5" family="Times New Roman" /></paragraph></elements>
            <footers>
              <footer type="default"><paragraph SpaceAbove="1.0" SpaceBelow="1.0" LeftIndent="0.0" RightIndent="0.0" LineSpacing="0.0" resolver="hvl-default" Alignment="1" Hanging="0.0"><content resolver="hvl-default" startOffset="16" length="9" family="Times New Roman" /></paragraph></footer>
            </footers>
        """.trimIndent()

        val document = UdfStructureParser.parse(rawXml, plainText)

        assertEquals(1, document.headers.size)
        assertEquals("Üst Bilgi", document.headers[0].paragraphs.first().runs.first().text)
        assertEquals(1, document.footers.size)
        assertEquals("Alt Bilgi", document.footers[0].paragraphs.first().runs.first().text)
    }
}
