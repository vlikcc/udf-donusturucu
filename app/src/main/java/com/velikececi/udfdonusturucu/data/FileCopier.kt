package com.velikececi.udfdonusturucu.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/**
 * SAF (`ACTION_OPEN_DOCUMENT`) ile seçilen `content://` URI'lerini işlenebilir bir [File]'a
 * kopyalar — iOS'un security-scoped resource kopyalama adımının Android karşılığı.
 */
object FileCopier {

    fun copyToCache(context: Context, uri: Uri): File? {
        val displayName = queryDisplayName(context, uri) ?: "dosya_${UUID.randomUUID()}"
        // Her seçim kendi alt klasörüne kopyalanır: aynı ada sahip iki seçim çakışmaz,
        // ama görünen ad (çıktı dosya adının türetildiği yer) olduğu gibi korunur.
        val pickDir = File(context.cacheDir, "picked/${UUID.randomUUID()}").apply { mkdirs() }
        val target = File(pickDir, displayName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    }
}
