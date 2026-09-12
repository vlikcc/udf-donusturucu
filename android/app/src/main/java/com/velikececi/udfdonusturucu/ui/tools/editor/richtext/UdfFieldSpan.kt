package com.velikececi.udfdonusturucu.ui.tools.editor.richtext

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

/**
 * iOS `NSAttributedString.Key.udfFieldName` özel niteliğinin Kotlin karşılığı — bir aralığın
 * UYAP alanı (ör. `AD_SOYAD`, `TARIH`) olduğunu işaretler. Görsel vurgu ayrı bir
 * `BackgroundColorSpan` ile sağlanır (bkz. [RichTextHandle.markField]); bu span salt metadata
 * taşıyıcısıdır.
 */
class UdfFieldSpan(val fieldName: String) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) = Unit
}

/**
 * Model → Spannable dönüşümünde `TypefaceSpan`'ın (render için, kayıplı: "Times New Roman" →
 * "serif") yanında orijinal yazı tipi adını taşıyan saf metadata span'i. Spannable → model geri
 * dönüşümünde yazı tipi adı TypefaceSpan'dan DEĞİL bu span'dan okunur.
 */
class FontFamilySpan(val family: String)
