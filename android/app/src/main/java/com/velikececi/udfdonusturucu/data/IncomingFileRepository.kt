package com.velikececi.udfdonusturucu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * IncomingFileRouter.swift'in Kotlin karşılığı. Başka bir uygulamadan "Birlikte aç"
 * (`ACTION_VIEW`/`ACTION_SEND`) ile gelen ve zaten cihaz önbelleğine kopyalanmış bir .udf
 * dosyasını tutar. [com.velikececi.udfdonusturucu.MainActivity] besler, ana ekran (ANA SAYFA
 * sekmesi) tüketir — iOS'taki `@Published var incomingFile: URL?` ile aynı rol.
 */
class IncomingFileRepository {
    private val _incoming = MutableStateFlow<File?>(null)
    val incoming: StateFlow<File?> = _incoming

    fun submit(file: File) {
        _incoming.value = file
    }

    /** Ana ekran dosyayı işleme aldıktan sonra çağırır — aynı dosya tekrar tetiklenmesin diye. */
    fun consume(): File? {
        val file = _incoming.value
        _incoming.value = null
        return file
    }
}
