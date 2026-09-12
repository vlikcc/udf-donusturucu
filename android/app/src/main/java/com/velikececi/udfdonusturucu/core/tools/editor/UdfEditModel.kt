package com.velikececi.udfdonusturucu.core.tools.editor

import java.util.UUID

/** UDFEditModel.swift'in Kotlin karşılığı — UDF Düzenleyici'nin düzenlenebilir belge modeli. */
data class UdfEditDocument(
    val headers: List<UdfEditHeaderFooter> = emptyList(),
    val blocks: List<UdfEditBlock>,
    val footers: List<UdfEditHeaderFooter> = emptyList(),
)

data class UdfEditHeaderFooter(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "default",
    val paragraphs: List<UdfEditParagraph> = listOf(UdfEditParagraph(runs = listOf(UdfEditRun(text = "")))),
)

sealed class UdfEditBlock {
    abstract val id: String

    data class ParagraphBlock(val paragraph: UdfEditParagraph) : UdfEditBlock() {
        override val id: String get() = "p-${paragraph.id}"
    }

    data class TableBlock(val table: UdfEditTable) : UdfEditBlock() {
        override val id: String get() = "t-${table.id}"
    }
}

data class UdfEditParagraph(
    val id: String = UUID.randomUUID().toString(),
    val alignment: Int = 3, // 0=sol, 1=orta, 2=sağ, 3=iki yana yasla
    val spaceAbove: Float = 1f,
    val spaceBelow: Float = 1f,
    val leftIndent: Float = 0f,
    val rightIndent: Float = 0f,
    val firstLineIndent: Float = 0f,
    val hangingIndent: Float = 0f,
    val lineSpacing: Float = 0f,
    val tabStops: List<Float> = emptyList(),
    val runs: List<UdfEditRun> = emptyList(),
)

sealed class UdfRunKind {
    data object Content : UdfRunKind()
    data class Field(val name: String) : UdfRunKind()
    data object Space : UdfRunKind()
}

data class UdfEditRun(
    val kind: UdfRunKind = UdfRunKind.Content,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val fontSize: Float = 12f,
    val fontFamily: String = "Times New Roman",
    /** Java/UYAP işaretli (signed) ARGB — siyah = -16777216. Android `Color` int'iyle bit-bit aynı. */
    val foregroundARGB: Int? = null,
    val backgroundARGB: Int? = null,
) {
    val isField: Boolean get() = kind is UdfRunKind.Field
    val fieldName: String? get() = (kind as? UdfRunKind.Field)?.name
}

data class UdfEditTable(
    val id: String = UUID.randomUUID().toString(),
    val columnCount: Int = 2,
    val columnSpans: List<Int> = emptyList(),
    val border: String = "borderCell",
    val rows: List<UdfEditTableRow> = emptyList(),
)

data class UdfEditTableRow(
    val id: String = UUID.randomUUID().toString(),
    val rowType: String = "dataRow",
    val cells: List<UdfEditTableCell> = emptyList(),
)

data class UdfEditTableCell(
    val id: String = UUID.randomUUID().toString(),
    val colspan: Int = 1,
    val rowspan: Int = 1,
    val fillColorARGB: Int? = null,
    val paragraphs: List<UdfEditParagraph> = listOf(UdfEditParagraph(runs = listOf(UdfEditRun(text = "")))),
) {
    /** Birleştirilmiş hücrelerde ilk paragrafı döndürür. */
    fun primaryParagraphValue(): UdfEditParagraph =
        paragraphs.firstOrNull() ?: UdfEditParagraph(runs = listOf(UdfEditRun(text = "")))

    fun withPrimaryParagraph(paragraph: UdfEditParagraph): UdfEditTableCell {
        val newParagraphs = if (paragraphs.isEmpty()) {
            listOf(paragraph)
        } else {
            paragraphs.toMutableList().also { it[0] = paragraph }
        }
        return copy(paragraphs = newParagraphs)
    }
}

/**
 * UDFColorCodec.swift'in Kotlin karşılığı. iOS tarafı Java işaretli ARGB'yi `UIColor`'a
 * dönüştürmek zorundaydı; Android `Color` int'i zaten aynı bit düzenine (0xAARRGGBB, işaretli
 * Int) sahip olduğundan bu dönüşüm gerekmez — model değeri render katmanında doğrudan
 * `android.graphics.Color`/span rengi olarak kullanılabilir (bkz. `UdfSpanCodec`).
 */
object UdfColorCodec {
    fun parse(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        raw.trim().toIntOrNull()?.let { return it }
        val parts = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size < 3) return null
        return (255 shl 24) or (parts[0] shl 16) or (parts[1] shl 8) or parts[2]
    }

    fun format(argb: Int): String = argb.toString()
}
