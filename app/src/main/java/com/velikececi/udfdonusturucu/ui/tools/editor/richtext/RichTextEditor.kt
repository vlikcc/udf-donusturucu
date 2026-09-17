package com.velikececi.udfdonusturucu.ui.tools.editor.richtext

import android.text.Editable
import android.text.Layout
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditParagraph

/**
 * RichTextEditorView.swift'in Kotlin karşılığı — `EditText` + `Spannable` tabanlı zengin metin
 * bloğu. Compose `BasicTextField`'ın (bu proje Compose sürümünde `TextFieldBuffer` span/
 * `AnnotatedString` API'si sunmadığından) span round-trip'i desteklememesi nedeniyle bilinçli
 * olarak `AndroidView` köprüsü kullanılır — `Editable`/`Spannable`, iOS'un `NSTextStorage`'ının
 * doğrudan karşılığıdır.
 */
@Composable
fun RichTextEditor(
    paragraph: UdfEditParagraph,
    handle: RichTextHandle,
    coordinator: EditorFocusCoordinator,
    onParagraphChange: (UdfEditParagraph) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
) {
    // Bu iki tutucu, `factory` içinde BİR KEZ oluşturulan dinleyicilerin her zaman en güncel
    // paragraf/callback'i görmesini sağlar — `factory` lambda'sı yalnızca ilk oluşturmada
    // çalıştığından, `paragraph` parametresini doğrudan yakalamak sonraki recomposition'ları
    // yanlışlıkla göz ardı ederdi.
    var currentParagraph by remember { mutableStateOf(paragraph) }
    var currentOnChange by remember { mutableStateOf(onParagraphChange) }
    currentOnChange = onParagraphChange

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = (24 * minLines).dp),
        factory = { ctx ->
            EditText(ctx).apply {
                setText(UdfSpanCodec.toSpannable(currentParagraph))
                background = null
                setPadding(24, 16, 24, 16)
                this.minLines = minLines
                textSize = 15f
                applyAlignment(this, currentParagraph.alignment)
                handle.attach(this) {
                    // Araç çubuğundan gelen span-cerrahisi bir mutasyon sonrası (ör. seçili
                    // metni kalınlaştırma) model'i Editable'ın güncel haliyle senkronlar.
                    val updated = UdfSpanCodec.toParagraph(text, currentParagraph)
                    currentParagraph = updated
                    currentOnChange(updated)
                }

                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        coordinator.bind(handle, currentParagraph.alignment) { newAlignment ->
                            val updated = currentParagraph.copy(alignment = newAlignment)
                            currentParagraph = updated
                            currentOnChange(updated)
                        }
                    }
                }

                addTextChangedListener(object : TextWatcher {
                    private var insertStart = -1
                    private var insertEnd = -1

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        if (count > before) {
                            insertStart = start + before
                            insertEnd = start + count
                        } else {
                            insertStart = -1
                            insertEnd = -1
                        }
                    }

                    override fun afterTextChanged(s: Editable) {
                        if (insertStart in 0 until insertEnd && handle.hasPendingAttrs()) {
                            handle.applyPendingTo(s, insertStart, insertEnd)
                        }
                        val updated = UdfSpanCodec.toParagraph(s, currentParagraph)
                        currentParagraph = updated
                        currentOnChange(updated)
                    }
                })
            }
        },
        update = { editText ->
            applyAlignment(editText, paragraph.alignment)
            val contentChanged = paragraph.runs != currentParagraph.runs
            currentParagraph = paragraph
            if (!editText.isFocused && contentChanged) {
                val selection = editText.selectionStart
                editText.setText(UdfSpanCodec.toSpannable(paragraph))
                runCatching { editText.setSelection(selection.coerceIn(0, editText.text?.length ?: 0)) }
            }
        },
    )
}

private fun applyAlignment(editText: EditText, alignment: Int) {
    editText.gravity = when (alignment) {
        1 -> Gravity.CENTER_HORIZONTAL or Gravity.TOP
        2 -> Gravity.END or Gravity.TOP
        else -> Gravity.START or Gravity.TOP
    }
    // UDF hizalaması bir span değil paragraf düzeyinde bir model alanı olduğundan `gravity` +
    // `justificationMode` ile uygulanır; `AlignmentSpan` hiç kullanılmaz.
    editText.justificationMode = if (alignment == 3) {
        Layout.JUSTIFICATION_MODE_INTER_WORD
    } else {
        Layout.JUSTIFICATION_MODE_NONE
    }
}
