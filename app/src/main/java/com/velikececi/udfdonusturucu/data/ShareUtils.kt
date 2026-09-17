package com.velikececi.udfdonusturucu.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** ResultView.swift / HistoryView.swift'teki paylaşım (`UIActivityViewController`) karşılığı. */
object ShareUtils {

    fun mimeTypeFor(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** Dosyayı işleyebilen harici bir uygulamada açar (`ACTION_VIEW`); işleyici yoksa bilgi verir. */
    fun openFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Bu dosyayı açabilecek bir uygulama bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }
}
