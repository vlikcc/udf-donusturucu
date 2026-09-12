package com.velikececi.udfdonusturucu.ui.tools.editor.richtext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Paylaşılan biçimlendirme araç çubuğunun "son odaklanılan editör"e uygulanmasını sağlar.
 * [bind] yalnızca ODAK KAZANILDIĞINDA çağrılır, kaybedildiğinde TEMİZLENMEZ — klavye
 * kapandığında araç çubuğunun bir işlevsiz (no-op) haline gelmemesi için (iOS de son proxy'yi
 * tutuyor).
 *
 * Hizalama bir span DEĞİL, paragraf düzeyinde bir model alanıdır (UDF'te öyle); bu yüzden
 * araç çubuğu hizalama değişikliğini doğrudan modele uygular ([onAlignmentChange]), Spannable
 * üzerinden değil.
 */
class EditorFocusCoordinator {
    var activeHandle by mutableStateOf<RichTextHandle?>(null)
        private set
    var activeAlignment by mutableStateOf(3)
        private set
    private var onAlignmentChangeCallback: ((Int) -> Unit)? = null

    fun bind(handle: RichTextHandle, currentAlignment: Int, onAlignmentChange: (Int) -> Unit) {
        activeHandle = handle
        activeAlignment = currentAlignment
        onAlignmentChangeCallback = onAlignmentChange
    }

    /** Odaktaki editörün hizalaması dışarıdan (ör. model güncellemesi sonrası) değiştiğinde çağrılır. */
    fun syncAlignment(alignment: Int) {
        activeAlignment = alignment
    }

    fun setAlignment(alignment: Int) {
        activeAlignment = alignment
        onAlignmentChangeCallback?.invoke(alignment)
    }
}
