package com.velikececi.udfdonusturucu.core.converters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.velikececi.udfdonusturucu.core.model.ConversionException
import com.velikececi.udfdonusturucu.core.model.UdfContentType
import com.velikececi.udfdonusturucu.core.model.UdfDocument
import com.velikececi.udfdonusturucu.core.model.UdfMetadata
import com.velikececi.udfdonusturucu.core.model.UdfPageFormat
import com.velikececi.udfdonusturucu.core.model.UyapParagraph
import java.io.File
import java.util.Locale

/**
 * PDFConverter.swift'in Kotlin karşılığı. iOS tarafı TextKit (NSTextContainer/NSLayoutManager)
 * ile sayfa sayfa metin döşüyordu; Android'de tüm metin için TEK bir [StaticLayout] kurulur,
 * ardından her sayfaya kaç satırın sığdığı kümülatif satır yüksekliğine göre hesaplanıp
 * [Canvas.clipRect] + [Canvas.translate] ile o aralık [android.graphics.pdf.PdfDocument] sayfasına
 * çizilir — StaticLayout'u sayfa başına yeniden kurmaya gerek kalmaz.
 *
 * Bilinen sadeleştirmeler (bkz. ilgili yorumlar): iki yana yaslama (`alignment=3`) ve
 * paragraf öncesi/sonrası boşluk (`spaceAbove`/`spaceBelow`)/satır aralığı Android'in span
 * sisteminde paragraf başına doğrudan karşılığı olmadığından uygulanmaz; sol girinti ve
 * asılı girinti (`hangingIndent`) tam uygulanır.
 */
object PdfConverter {

    private const val PAGE_WIDTH = 595.28f // A4, pt
    private const val PAGE_HEIGHT = 841.89f
    private const val MAX_PAGES = 500

    fun convert(document: UdfDocument, context: Context): File {
        val format = document.pageFormat ?: UdfPageFormat.DEFAULT
        val spanned = buildSpanned(document)

        val outputFile = OutputPaths.outputFile(context, document.fileName, "pdf")

        try {
            val pdf = renderPdf(spanned, format)
            outputFile.outputStream().use { out -> pdf.writeTo(out) }
            pdf.close()
        } catch (e: Exception) {
            throw ConversionException.PdfCreationFailed()
        }

        return outputFile
    }

    // MARK: - Spanned metin inşası

    private fun buildSpanned(document: UdfDocument): Spanned {
        val content = document.content

        if (content.contentType == UdfContentType.UYAP && content.paragraphs.isNotEmpty()) {
            return buildSpannedFromUyap(content.text, content.paragraphs)
        }

        if (content.contentType == UdfContentType.HTML) {
            spannedFromHtml(content.rawContent)?.let { return it }
        }

        // RTF (zaten düz metne indirgenmiş) ve düz metin için ortak "bölüm" yolu.
        return spannedFromSections(document)
    }

    private fun spannedFromHtml(html: String): Spanned? = try {
        val lower = html.lowercase(Locale.ROOT)
        val fullHtml = if (!lower.contains("<html")) "<html><body>$html</body></html>" else html
        Html.fromHtml(fullHtml, Html.FROM_HTML_MODE_COMPACT)
    } catch (e: Exception) {
        null
    }

    private fun spannedFromSections(document: UdfDocument): Spanned {
        val builder = SpannableStringBuilder()
        appendMetadataHeader(builder, document.metadata)

        val sections = document.content.sections
        if (sections.isEmpty()) {
            // UYAP'ta formatlanmamış / RTF'de biçim çıkarılamamış durum: en azından ham metni göster.
            appendBodyParagraph(builder, document.content.text)
        } else {
            for (section in sections) {
                section.title?.let { appendHeading(builder, it) }
                appendBodyParagraph(builder, section.body)
            }
        }
        return builder
    }

    private fun appendMetadataHeader(builder: SpannableStringBuilder, metadata: UdfMetadata?) {
        metadata ?: return
        val parts = mutableListOf<String>()
        metadata.title?.let { parts.add("Başlık: $it") }
        metadata.author?.let { parts.add("Yazar: $it") }
        metadata.creationDate?.let { parts.add("Tarih: $it") }
        if (parts.isEmpty()) return

        val start = builder.length
        builder.append(parts.joinToString(" | ")).append("\n\n")
        val end = builder.length
        builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(AbsoluteSizeSpan(10, false), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(ForegroundColorSpan(Color.DKGRAY), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendHeading(builder: SpannableStringBuilder, title: String) {
        val start = builder.length
        builder.append(title).append("\n")
        val end = builder.length
        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(AbsoluteSizeSpan(14, false), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendBodyParagraph(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text).append("\n\n")
        val end = builder.length
        builder.setSpan(AbsoluteSizeSpan(12, false), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    @Suppress("DEPRECATION") // TypefaceSpan(Typeface) API 28 istiyor; minSdk 26 için String sürümü kullanılıyor.
    private fun buildSpannedFromUyap(plainText: String, paragraphs: List<UyapParagraph>): Spanned {
        val builder = SpannableStringBuilder()
        val totalLength = plainText.length

        for ((pIdx, paragraph) in paragraphs.withIndex()) {
            val firstRun = paragraph.runs.firstOrNull() ?: continue
            val lastRun = paragraph.runs.last()
            val paraStart = firstRun.startOffset
            val paraEnd = (lastRun.startOffset + lastRun.length).coerceAtMost(totalLength)
            if (paraStart >= totalLength || paraStart >= paraEnd) continue

            val paraText = plainText.substring(paraStart, paraEnd)
            val paraSpanStart = builder.length
            builder.append(paraText)
            val paraSpanEnd = builder.length

            val alignment = when (paragraph.alignment) {
                1 -> Layout.Alignment.ALIGN_CENTER
                2 -> Layout.Alignment.ALIGN_OPPOSITE
                // 0 (sol) ve 3 (iki yana yasla — Android span sisteminde paragraf başına
                // desteklenmiyor, sola hizalanmış olarak gösterilir).
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            builder.setSpan(AlignmentSpan.Standard(alignment), paraSpanStart, paraSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (paragraph.hangingIndent > 0) {
                builder.setSpan(
                    LeadingMarginSpan.Standard(0, paragraph.hangingIndent.toInt()),
                    paraSpanStart, paraSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else if (paragraph.leftIndent > 0 || paragraph.firstLineIndent > 0) {
                builder.setSpan(
                    LeadingMarginSpan.Standard(paragraph.firstLineIndent.toInt(), paragraph.leftIndent.toInt()),
                    paraSpanStart, paraSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }

            for (run in paragraph.runs) {
                val runLocalStart = run.startOffset - paraStart
                val runLocalEnd = (runLocalStart + run.length).coerceAtMost(paraText.length)
                if (runLocalStart < 0 || runLocalStart >= paraText.length || runLocalEnd <= runLocalStart) continue

                val spanStart = paraSpanStart + runLocalStart
                val spanEnd = paraSpanStart + runLocalEnd

                val style = when {
                    run.bold && run.italic -> Typeface.BOLD_ITALIC
                    run.bold -> Typeface.BOLD
                    run.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                if (style != Typeface.NORMAL) {
                    builder.setSpan(StyleSpan(style), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (run.underline) {
                    builder.setSpan(UnderlineSpan(), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val size = (run.fontSize ?: 12f).toInt().coerceAtLeast(1)
                builder.setSpan(AbsoluteSizeSpan(size, false), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(TypefaceSpan(mapFontFamily(run.fontFamily)), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (pIdx < paragraphs.size - 1 && !paraText.endsWith("\n")) {
                builder.append("\n")
            }
        }

        if (builder.isEmpty()) {
            builder.append(plainText)
        }

        return builder
    }

    private fun mapFontFamily(family: String?): String {
        val name = (family ?: "").lowercase(Locale.ROOT)
        return when {
            name.contains("times") || name.contains("serif") || name.contains("georgia") ||
                name.contains("garamond") -> "serif"
            name.contains("courier") || name.contains("consolas") || name.contains("mono") -> "monospace"
            else -> "sans-serif"
        }
    }

    // MARK: - Sayfalama ve çizim

    private fun renderPdf(spanned: Spanned, format: UdfPageFormat): PdfDocument {
        val pdf = PdfDocument()

        val leftMargin = format.leftMargin
        val topMargin = format.topMargin
        val rightMargin = format.rightMargin
        val bottomMargin = format.bottomMargin
        val drawableWidth = (PAGE_WIDTH - leftMargin - rightMargin).toInt().coerceAtLeast(1)
        val drawableHeight = PAGE_HEIGHT - topMargin - bottomMargin

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            typeface = Typeface.SERIF
            color = Color.BLACK
        }

        val layout = StaticLayout.Builder
            .obtain(spanned, 0, spanned.length, textPaint, drawableWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        var startLine = 0
        var pageIndex = 0

        while (startLine < layout.lineCount && pageIndex < MAX_PAGES) {
            val pageTopY = layout.getLineTop(startLine)
            var endLine = startLine
            while (endLine < layout.lineCount && layout.getLineBottom(endLine) - pageTopY <= drawableHeight) {
                endLine++
            }
            if (endLine == startLine) endLine = startLine + 1 // tek satır bile sığmıyorsa yine de ilerle

            val pageInfo = PdfDocument.PageInfo
                .Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageIndex + 1)
                .create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            canvas.save()
            canvas.translate(leftMargin, topMargin - pageTopY)
            canvas.clipRect(
                0f,
                pageTopY.toFloat(),
                drawableWidth.toFloat(),
                layout.getLineBottom(endLine - 1).toFloat(),
            )
            layout.draw(canvas)
            canvas.restore()

            drawPageNumber(canvas, pageIndex + 1, format)
            pdf.finishPage(page)

            startLine = endLine
            pageIndex++
        }

        if (pageIndex == 0) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), 1).create()
            val page = pdf.startPage(pageInfo)
            drawPageNumber(page.canvas, 1, format)
            pdf.finishPage(page)
        }

        return pdf
    }

    private fun drawPageNumber(canvas: Canvas, pageNumber: Int, format: UdfPageFormat) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH / 2f, PAGE_HEIGHT - format.bottomMargin + 10f, paint)
    }
}
