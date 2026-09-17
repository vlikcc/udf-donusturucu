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
 * ile sayfa sayfa metin döşüyordu. Android'de her [UyapParagraph] (UYAP **ve** biçimlendirmeli
 * RTF içeriği — bkz. `UdfParser.parseRtfFormatted`) kendi [StaticLayout]'una ayrı ayrı dönüştürülür
 * ([RenderBlock]); bu sayede her paragraf kendi hizalamasını (iki yana yasla dahil), satır
 * aralığını ve sağ girintisini bağımsız olarak taşıyabilir — tek büyük ortak `StaticLayout`
 * kullanan önceki yaklaşımda bu paragraf-başına farklılaşma mümkün değildi. Bloklar bir Y imleci
 * ile üst üste dizilir; imleç mevcut sayfanın kalan yüksekliğini aşınca yeni sayfaya geçilir.
 *
 * Kalan bilinen sadeleştirme: HTML ve düz-metin/bölüm yedek yollarında (paragraf modeli
 * bulunmadığından) hizalama/boşluk/girinti uygulanmaz — bunlar tek bir [RenderBlock] olarak
 * (sola hizalı, varsayılan boşluklarla) çizilir.
 */
object PdfConverter {

    private const val PAGE_WIDTH = 595.28f // A4, pt
    private const val PAGE_HEIGHT = 841.89f
    private const val MAX_PAGES = 500

    fun convert(document: UdfDocument, context: Context): File {
        val format = document.pageFormat ?: UdfPageFormat.DEFAULT
        val drawableWidth = (PAGE_WIDTH - format.leftMargin - format.rightMargin).toInt().coerceAtLeast(1)
        val blocks = buildRenderBlocks(document, drawableWidth)

        val outputFile = OutputPaths.outputFile(context, document.fileName, "pdf")

        try {
            val pdf = renderPdf(blocks, format)
            outputFile.outputStream().use { out -> pdf.writeTo(out) }
            pdf.close()
        } catch (e: Exception) {
            throw ConversionException.PdfCreationFailed()
        }

        return outputFile
    }

    // MARK: - Render blokları

    private data class RenderBlock(
        val layout: StaticLayout,
        val width: Int,
        val spaceAbove: Float,
        val spaceBelow: Float,
    )

    private fun buildRenderBlocks(document: UdfDocument, drawableWidth: Int): List<RenderBlock> {
        val content = document.content
        if (content.paragraphs.isNotEmpty()) {
            return buildBlocksFromParagraphs(content.text, content.paragraphs, drawableWidth)
        }
        return listOf(buildSingleBlock(buildFallbackSpanned(document), drawableWidth))
    }

    @Suppress("DEPRECATION") // TypefaceSpan(Typeface) API 28 istiyor; minSdk 26 için String sürümü kullanılıyor.
    private fun buildBlocksFromParagraphs(
        plainText: String,
        paragraphs: List<UyapParagraph>,
        drawableWidth: Int,
    ): List<RenderBlock> {
        val totalLength = plainText.length

        return paragraphs.mapNotNull { paragraph ->
            val firstRun = paragraph.runs.firstOrNull() ?: return@mapNotNull null
            val lastRun = paragraph.runs.last()
            val paraStart = firstRun.startOffset
            val paraEnd = (lastRun.startOffset + lastRun.length).coerceAtMost(totalLength)
            if (paraStart >= totalLength || paraStart >= paraEnd) return@mapNotNull null

            val paraText = plainText.substring(paraStart, paraEnd)
            val builder = SpannableStringBuilder(paraText)

            for (run in paragraph.runs) {
                val localStart = (run.startOffset - paraStart).coerceIn(0, paraText.length)
                val localEnd = (localStart + run.length).coerceIn(localStart, paraText.length)
                if (localEnd <= localStart) continue

                val style = when {
                    run.bold && run.italic -> Typeface.BOLD_ITALIC
                    run.bold -> Typeface.BOLD
                    run.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                if (style != Typeface.NORMAL) {
                    builder.setSpan(StyleSpan(style), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (run.underline) {
                    builder.setSpan(UnderlineSpan(), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val size = (run.fontSize ?: 12f).toInt().coerceAtLeast(1)
                builder.setSpan(AbsoluteSizeSpan(size, false), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(
                    TypefaceSpan(mapFontFamily(run.fontFamily)),
                    localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }

            if (paragraph.hangingIndent > 0) {
                builder.setSpan(
                    LeadingMarginSpan.Standard(0, paragraph.hangingIndent.toInt()),
                    0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else if (paragraph.leftIndent > 0 || paragraph.firstLineIndent > 0) {
                builder.setSpan(
                    LeadingMarginSpan.Standard(paragraph.firstLineIndent.toInt(), paragraph.leftIndent.toInt()),
                    0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }

            // Sağ girinti: paragrafın kendi kutusunu sağdan daraltarak uygulanır (Android'in span
            // sisteminde iOS'un tailIndent'ine doğrudan karşılık gelen bir span yok).
            val boxWidth = (drawableWidth - paragraph.rightIndent.coerceAtLeast(0f)).toInt().coerceAtLeast(40)

            val alignment = when (paragraph.alignment) {
                1 -> Layout.Alignment.ALIGN_CENTER
                2 -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL // 0 (sol) ve 3 (iki yana yasla — ayrıca aşağıda uygulanır)
            }

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 12f
                typeface = Typeface.SERIF
                color = Color.BLACK
            }

            val layoutBuilder = StaticLayout.Builder
                .obtain(builder, 0, builder.length, paint, boxWidth)
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(paragraph.lineSpacing.coerceAtLeast(0f), 1f)

            if (paragraph.alignment == 3) {
                // İki yana yaslama: StaticLayout'un global gerekçelendirme modu (API 26+), tek
                // paragraf kutusuna uygulanır — diğer paragrafların hizalamasını etkilemez çünkü
                // her paragraf kendi StaticLayout'unda.
                layoutBuilder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            }

            RenderBlock(
                layout = layoutBuilder.build(),
                width = boxWidth,
                spaceAbove = paragraph.spaceAbove.coerceAtLeast(0f),
                spaceBelow = paragraph.spaceBelow.coerceAtLeast(0f),
            )
        }
    }

    private fun buildSingleBlock(spanned: Spanned, drawableWidth: Int): RenderBlock {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            typeface = Typeface.SERIF
            color = Color.BLACK
        }
        val layout = StaticLayout.Builder
            .obtain(spanned, 0, spanned.length, paint, drawableWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        return RenderBlock(layout = layout, width = drawableWidth, spaceAbove = 0f, spaceBelow = 0f)
    }

    // MARK: - Yedek (paragraf modeli olmayan) Spanned inşası

    private fun buildFallbackSpanned(document: UdfDocument): Spanned {
        val content = document.content
        if (content.contentType == UdfContentType.HTML) {
            spannedFromHtml(content.rawContent)?.let { return it }
        }
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
            // UYAP'ta formatlanmamış durum: en azından ham metni göster.
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

    private fun renderPdf(blocks: List<RenderBlock>, format: UdfPageFormat): PdfDocument {
        val pdf = PdfDocument()

        val leftMargin = format.leftMargin
        val topMargin = format.topMargin
        val bottomMargin = format.bottomMargin
        val pageBottomY = PAGE_HEIGHT - bottomMargin

        var pageIndex = 1
        var page = pdf.startPage(newPageInfo(pageIndex))
        var canvas = page.canvas
        var cursorY = topMargin

        fun finishCurrentPage() {
            drawPageNumber(canvas, pageIndex, format)
            pdf.finishPage(page)
        }

        fun startNextPage() {
            finishCurrentPage()
            pageIndex++
            page = pdf.startPage(newPageInfo(pageIndex))
            canvas = page.canvas
            cursorY = topMargin
        }

        for (block in blocks) {
            if (pageIndex >= MAX_PAGES) break
            if (cursorY > topMargin) cursorY += block.spaceAbove

            val layout = block.layout
            var lineStart = 0
            while (lineStart < layout.lineCount) {
                val availableHeight = pageBottomY - cursorY
                if (availableHeight <= 0) {
                    startNextPage()
                    continue
                }

                val blockTopY = layout.getLineTop(lineStart)
                var lineEnd = lineStart
                while (lineEnd < layout.lineCount && layout.getLineBottom(lineEnd) - blockTopY <= availableHeight) {
                    lineEnd++
                }
                if (lineEnd == lineStart) {
                    startNextPage()
                    continue
                }

                canvas.save()
                canvas.translate(leftMargin, cursorY - blockTopY)
                canvas.clipRect(
                    0f,
                    blockTopY.toFloat(),
                    block.width.toFloat(),
                    layout.getLineBottom(lineEnd - 1).toFloat(),
                )
                layout.draw(canvas)
                canvas.restore()

                cursorY += (layout.getLineBottom(lineEnd - 1) - blockTopY)
                lineStart = lineEnd
            }

            cursorY += block.spaceBelow
        }

        finishCurrentPage()
        return pdf
    }

    private fun newPageInfo(pageNumber: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber).create()

    private fun drawPageNumber(canvas: Canvas, pageNumber: Int, format: UdfPageFormat) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH / 2f, PAGE_HEIGHT - format.bottomMargin + 10f, paint)
    }
}
