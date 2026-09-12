package com.velikececi.udfdonusturucu.data

import com.velikececi.udfdonusturucu.core.zip.UdfZip
import java.io.File
import java.util.Locale

/**
 * Harici bir uygulamadan (Birlikte Aç / Paylaş) gelen dosyanın gerçekten bir UDF belgesi olup
 * olmadığını belirler. `content://` sağlayıcılarının çoğu dosya adını (dolayısıyla uzantıyı)
 * manifest'e yansıtmadığından intent-filter'lara tek başına güvenilemez — burada hem uzantı hem
 * ZIP içerik doğrulaması yapılır (iOS `UTType.isUDFFile(_:)` / geniş `udfPickerTypes` listesinin
 * Android karşılığı: UDF genellikle zip/octet-stream olarak yanlış etiketlenir).
 */
object IncomingFileFilter {
    fun looksLikeUdf(file: File): Boolean {
        if (file.extension.lowercase(Locale.ROOT) == "udf") return true
        return try {
            UdfZip.extractEntries(file).any { entry ->
                val name = entry.fileName.lowercase(Locale.ROOT)
                name.contains("content") && name.endsWith(".xml")
            }
        } catch (e: Exception) {
            false
        }
    }
}
