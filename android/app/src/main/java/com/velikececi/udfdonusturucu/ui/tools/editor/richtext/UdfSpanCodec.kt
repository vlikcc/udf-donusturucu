package com.velikececi.udfdonusturucu.ui.tools.editor.richtext

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditParagraph
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditRun
import com.velikececi.udfdonusturucu.core.tools.editor.UdfRunKind

/**
 * UDFAttributedConverter.swift'in Kotlin karşılığı — model ↔ `Spannable` dönüşümü.
 *
 * Renk dönüşümü "bedava": Java işaretli ARGB zaten Android `Color` int'iyle bit-bit aynı
 * olduğundan hiçbir çevrim gerekmez (bkz. `UdfColorCodec`). Yazı tipi ailesi ise TERSİ yönde
 * kayıplıdır — Android'de gerçek "Times New Roman" yoktur (`TypefaceSpan("serif")` yalnızca
 * render içindir); bu yüzden orijinal ad ayrıca [FontFamilySpan] ile taşınır ve geri dönüşte
 * TypefaceSpan'dan DEĞİL bu span'dan okunur.
 */
object UdfSpanCodec {
    /** iOS'un alan vurgusuyla aynı: %35 opak sarı (ARGB). */
    const val FIELD_HIGHLIGHT_COLOR: Int = 0x59FFEB3B.toInt()

    fun toSpannable(paragraph: UdfEditParagraph): Spannable {
        val builder = SpannableStringBuilder()
        for (run in paragraph.runs) {
            val start = builder.length
            builder.append(run.text)
            val end = builder.length
            if (start == end) continue
            applyRunSpans(builder, run, start, end)
        }
        return builder
    }

    private fun applyRunSpans(builder: SpannableStringBuilder, run: UdfEditRun, start: Int, end: Int) {
        val style = when {
            run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
            run.isBold -> Typeface.BOLD
            run.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (style != Typeface.NORMAL) {
            builder.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (run.isUnderline) {
            builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        builder.setSpan(TypefaceSpan(mapFontFamily(run.fontFamily)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(FontFamilySpan(run.fontFamily), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (run.fontSize > 0f) {
            builder.setSpan(AbsoluteSizeSpan(run.fontSize.toInt(), true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        run.foregroundARGB?.let {
            builder.setSpan(ForegroundColorSpan(ensureVisible(it)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        run.backgroundARGB?.let {
            builder.setSpan(BackgroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (run.kind is UdfRunKind.Field) {
            builder.setSpan(UdfFieldSpan(run.kind.name), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(BackgroundColorSpan(FIELD_HIGHLIGHT_COLOR), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** iOS'taki `max(alpha, 0.01)` guard'ının karşılığı — tamamen saydam (a=0) rengi görünür kılar. */
    private fun ensureVisible(argb: Int): Int {
        val alpha = (argb ushr 24) and 0xFF
        return if (alpha == 0) (3 shl 24) or (argb and 0x00FFFFFF) else argb
    }

    /**
     * `enumerateAttributes`in Kotlin karşılığı — span geçişlerinde yürüyerek run listesi
     * üretir. [base]: yalnızca hiçbir [FontFamilySpan] bulunamadığında (ör. sınırda yazılan yeni
     * metin) yedek yazı tipi kaynağı olarak kullanılır — çoğu paragraf uçtan uca tek yazı tipi
     * kullandığından bu pratikte her zaman doğru sonucu verir.
     */
    fun toParagraph(spanned: Spanned, base: UdfEditParagraph): UdfEditParagraph {
        val text = spanned.toString()
        val defaultFamily = base.runs.firstOrNull()?.fontFamily ?: "Times New Roman"

        if (text.isEmpty()) {
            return base.copy(runs = listOf(UdfEditRun(text = "", fontFamily = defaultFamily)))
        }

        val runs = mutableListOf<UdfEditRun>()
        var pos = 0
        while (pos < text.length) {
            var next = spanned.nextSpanTransition(pos, text.length, Any::class.java)
            if (next <= pos) next = text.length

            val styles = spanned.getSpans(pos, next, StyleSpan::class.java)
            val isBold = styles.any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
            val isItalic = styles.any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
            val isUnderline = spanned.getSpans(pos, next, UnderlineSpan::class.java).isNotEmpty()
            val fg = spanned.getSpans(pos, next, ForegroundColorSpan::class.java).lastOrNull()?.foregroundColor
            val bg = spanned.getSpans(pos, next, BackgroundColorSpan::class.java).lastOrNull()?.backgroundColor
            val fieldSpan = spanned.getSpans(pos, next, UdfFieldSpan::class.java).firstOrNull()
            val sizeSpan = spanned.getSpans(pos, next, AbsoluteSizeSpan::class.java).lastOrNull()
            val familySpan = spanned.getSpans(pos, next, FontFamilySpan::class.java).lastOrNull()

            val runText = text.substring(pos, next)
            val kind = if (fieldSpan != null) UdfRunKind.Field(fieldSpan.fieldName) else UdfRunKind.Content
            // Yalnızca boşluk karakterlerinden oluşan, alan OLMAYAN run'lar iOS'ta `.space` sayılır.
            val isSpaceRun = kind is UdfRunKind.Content && runText.isNotEmpty() && runText.isBlank()

            runs.add(
                UdfEditRun(
                    kind = if (isSpaceRun) UdfRunKind.Space else kind,
                    text = runText,
                    isBold = isBold,
                    isItalic = isItalic,
                    isUnderline = isUnderline,
                    fontSize = sizeSpan?.size?.toFloat() ?: 12f,
                    fontFamily = familySpan?.family ?: defaultFamily,
                    foregroundARGB = fg,
                    // Alan vurgusu kendi BackgroundColorSpan'ından geri okunmaz — yalnızca
                    // kullanıcının bilinçli seçtiği vurgu modele geri yazılır.
                    backgroundARGB = if (fieldSpan != null) null else bg,
                ),
            )
            pos = next
        }

        return base.copy(runs = runs)
    }

    fun mapFontFamily(family: String): String = when {
        family.contains("times", ignoreCase = true) -> "serif"
        family.contains("courier", ignoreCase = true) || family.contains("mono", ignoreCase = true) -> "monospace"
        family.contains("arial", ignoreCase = true) || family.contains("helvetica", ignoreCase = true) -> "sans-serif"
        else -> "serif"
    }
}
