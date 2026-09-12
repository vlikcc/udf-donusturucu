package com.velikececi.udfdonusturucu.ui.tools.editor.richtext

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText
import java.lang.ref.WeakReference

/**
 * `RichTextEditorProxy` (iOS) karşılığı — paylaşılan biçimlendirme araç çubuğunun, o an odaklı
 * (ya da en son odaklanmış) `EditText` üzerinde çalışmasını sağlayan ince bir köprü.
 *
 * Android'de iOS'un `typingAttributes`'ı yoktur: imleç tek noktadayken (seçim boş) yapılan bir
 * biçim değişikliği yalnızca SONRADAN yazılacak metne uygulanabilir. Bu, `pendingBold` vb. ile
 * (varsayılan false, her dokunuşta ters çevrilir) taklit edilir; [RichTextEditor]'daki
 * `TextWatcher.afterTextChanged` yeni eklenen karakter aralığına bu bayrakları uygular.
 */
class RichTextHandle {
    private var editTextRef: WeakReference<EditText>? = null

    // setSpan/removeSpan çağrıları TextWatcher'ı TETİKLEMEZ (yalnızca metin İÇERİĞİ değişiklikleri
    // tetikler) — bu yüzden araç çubuğundan gelen aralık-tabanlı biçim değişiklikleri (span
    // cerrahisi) sonrasında model'in senkron kalması için bu geri çağrı elle çağrılır.
    private var onExternalSpanChange: (() -> Unit)? = null

    var pendingBold: Boolean = false
        private set
    var pendingItalic: Boolean = false
        private set
    var pendingUnderline: Boolean = false
        private set
    var pendingForeground: Int? = null
        private set
    var pendingBackground: Int? = null
        private set

    fun attach(editText: EditText, onExternalSpanChange: () -> Unit) {
        editTextRef = WeakReference(editText)
        this.onExternalSpanChange = onExternalSpanChange
    }

    fun hasPendingAttrs(): Boolean =
        pendingBold || pendingItalic || pendingUnderline || pendingForeground != null || pendingBackground != null

    fun applyPendingTo(text: Editable, start: Int, end: Int) {
        if (start >= end) return
        if (pendingBold || pendingItalic) {
            val style = when {
                pendingBold && pendingItalic -> Typeface.BOLD_ITALIC
                pendingBold -> Typeface.BOLD
                else -> Typeface.ITALIC
            }
            text.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (pendingUnderline) {
            text.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        pendingForeground?.let { text.setSpan(ForegroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        pendingBackground?.let { text.setSpan(BackgroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    private fun current(): Triple<Editable, Int, Int>? {
        val et = editTextRef?.get() ?: return null
        val text = et.text ?: return null
        val start = et.selectionStart
        val end = et.selectionEnd
        if (start < 0 || end < 0) return null
        return Triple(text, minOf(start, end), maxOf(start, end))
    }

    fun toggleBold() {
        val (text, start, end) = current() ?: return
        if (start == end) {
            pendingBold = !pendingBold
            return
        }
        toggleStyleSpan(text, start, end, toggling = Typeface.BOLD)
        onExternalSpanChange?.invoke()
    }

    fun toggleItalic() {
        val (text, start, end) = current() ?: return
        if (start == end) {
            pendingItalic = !pendingItalic
            return
        }
        toggleStyleSpan(text, start, end, toggling = Typeface.ITALIC)
        onExternalSpanChange?.invoke()
    }

    fun toggleUnderline() {
        val (text, start, end) = current() ?: return
        if (start == end) {
            pendingUnderline = !pendingUnderline
            return
        }
        val hasUnderline = text.getSpans(start, minOf(start + 1, end), UnderlineSpan::class.java).isNotEmpty()
        removeAndSplit(text, text.getSpans(start, end, UnderlineSpan::class.java), start, end) { UnderlineSpan() }
        if (!hasUnderline) {
            text.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        onExternalSpanChange?.invoke()
    }

    fun setForegroundColor(argb: Int?) {
        val (text, start, end) = current() ?: return
        if (start == end) {
            pendingForeground = argb
            return
        }
        removeAndSplit(text, text.getSpans(start, end, ForegroundColorSpan::class.java), start, end) { ForegroundColorSpan(it.foregroundColor) }
        if (argb != null) {
            text.setSpan(ForegroundColorSpan(argb), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        onExternalSpanChange?.invoke()
    }

    fun setHighlightColor(argb: Int?) {
        val (text, start, end) = current() ?: return
        if (start == end) {
            pendingBackground = argb
            return
        }
        removeAndSplit(text, text.getSpans(start, end, BackgroundColorSpan::class.java), start, end) { BackgroundColorSpan(it.backgroundColor) }
        if (argb != null) {
            text.setSpan(BackgroundColorSpan(argb), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        onExternalSpanChange?.invoke()
    }

    /** UYAP alanı işaretler — iOS gibi boş olmayan bir seçim gerektirir. */
    fun markField(name: String): Boolean {
        val (text, start, end) = current() ?: return false
        if (start == end) return false

        text.getSpans(start, end, UdfFieldSpan::class.java).forEach { text.removeSpan(it) }
        text.setSpan(UdfFieldSpan(name), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(BackgroundColorSpan(UdfSpanCodec.FIELD_HIGHLIGHT_COLOR), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        onExternalSpanChange?.invoke()
        return true
    }

    fun hasSelection(): Boolean {
        val (_, start, end) = current() ?: return false
        return start != end
    }

    /**
     * Aralıktaki TÜM `StyleSpan`'ler (kısmi çakışanlar dahil) kaldırılıp kalıntılar geri
     * eklenir, ardından etkin (kalın, italik) çifti TEK bir birleşik span olarak yeniden yazılır
     * — yoksa kalın'ı açıp kapatmak italic'i de silerdi. "Etkin mi?" sorusu seçimin İLK
     * karakterine bakılarak yanıtlanır (basit editörlerin yaygın yaklaşımı).
     */
    private fun toggleStyleSpan(text: Editable, start: Int, end: Int, toggling: Int) {
        val firstCharStyles = text.getSpans(start, minOf(start + 1, end), StyleSpan::class.java)
        var currentBold = firstCharStyles.any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
        var currentItalic = firstCharStyles.any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }

        text.getSpans(start, end, StyleSpan::class.java).forEach { span ->
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            text.removeSpan(span)
            if (spanStart < start) text.setSpan(StyleSpan(span.style), spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (spanEnd > end) text.setSpan(StyleSpan(span.style), end, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (toggling == Typeface.BOLD) currentBold = !currentBold else currentItalic = !currentItalic

        val newStyle = when {
            currentBold && currentItalic -> Typeface.BOLD_ITALIC
            currentBold -> Typeface.BOLD
            currentItalic -> Typeface.ITALIC
            else -> null
        }
        if (newStyle != null) {
            text.setSpan(StyleSpan(newStyle), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun <T : Any> removeAndSplit(text: Editable, spans: Array<T>, start: Int, end: Int, clone: (T) -> Any) {
        spans.forEach { span ->
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            text.removeSpan(span)
            if (spanStart < start) text.setSpan(clone(span), spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (spanEnd > end) text.setSpan(clone(span), end, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
