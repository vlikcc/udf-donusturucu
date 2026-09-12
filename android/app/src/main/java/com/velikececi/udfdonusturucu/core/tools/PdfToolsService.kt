package com.velikececi.udfdonusturucu.core.tools

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.velikececi.udfdonusturucu.core.converters.OutputPaths
import com.velikececi.udfdonusturucu.core.converters.PdfBoxInit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** PDFToolsService.swift'teki `CompressionQuality` enum'unun karşılığı. */
enum class CompressionQuality(val title: String, val subtitle: String, val jpegQuality: Int, val dpi: Float) {
    BALANCED("Dengeli", "Okunabilir kalite, belirgin boyut azalması", 52, 110f),
    AGGRESSIVE("Maksimum Sıkıştırma", "En küçük boyut, daha düşük görüntü kalitesi", 22, 72f),
}

sealed class PdfToolsException(message: String) : Exception(message) {
    class CannotOpen : PdfToolsException("PDF açılamadı.")
    class CompressionFailed(detail: String?) : PdfToolsException("Sıkıştırma başarısız: ${detail ?: "bilinmeyen hata"}")
    class EncryptionFailed(detail: String?) : PdfToolsException("Şifreleme başarısız: ${detail ?: "bilinmeyen hata"}")
    class AlreadyEncrypted : PdfToolsException("Bu PDF zaten şifreli.")
}

/**
 * PDFToolsService.swift'in Kotlin karşılığı — PDF sıkıştırma ve şifreleme. iOS `UIGraphicsPDFRenderer`
 * yerine [PdfRasterizer] (native rasterize) + PDFBox (JPEG gömme) kombinasyonu kullanılır:
 * `android.graphics.pdf.PdfDocument` ile yazmak KASITLI OLARAK tercih edilmedi — Skia, JPEG'den
 * decode edilmiş bir Bitmap'i tekrar kendi (genelde ham/Flate) kodlamasıyla gömer ve JPEG kalite
 * ayarını yok sayar; bu da "sıkıştırılmış" çıktının girdiden BÜYÜK çıkmasına yol açabilir.
 */
object PdfToolsService {

    private const val MAX_COMPRESS_PIXELS = 2600

    data class CompressionResult(val outputFile: File, val originalBytes: Long, val compressedBytes: Long)

    fun compress(file: File, quality: CompressionQuality, context: Context): CompressionResult {
        PdfBoxInit.ensure(context)
        val originalBytes = file.length()
        val outputFile = OutputPaths.outputFile(context, "Sikistirilmis_${file.nameWithoutExtension}", "pdf")

        try {
            PDDocument().use { out ->
                PdfRasterizer.rasterize(file, context, quality.dpi, MAX_COMPRESS_PIXELS) { _, _, page ->
                    try {
                        val jpegBytes = encodeJpeg(page.bitmap, quality.jpegQuality)
                        val pdImage = JPEGFactory.createFromByteArray(out, jpegBytes)
                        val pdPage = PDPage(PDRectangle(page.widthPoints, page.heightPoints))
                        out.addPage(pdPage)
                        PDPageContentStream(out, pdPage).use { cs ->
                            cs.drawImage(pdImage, 0f, 0f, page.widthPoints, page.heightPoints)
                        }
                    } finally {
                        page.bitmap.recycle()
                    }
                }
                out.save(outputFile)
            }
        } catch (e: PdfRasterizer.UnsupportedPdfException) {
            throw PdfToolsException.CompressionFailed(e.message)
        } catch (e: Exception) {
            throw PdfToolsException.CompressionFailed(e.message)
        }

        var compressedBytes = outputFile.length()

        // Zaten optimize edilmiş veya çok küçük PDF'lerde yeni raster çıktı daha büyük
        // olabilir. Kullanıcıya hiçbir zaman orijinalden büyük bir "sıkıştırılmış" dosya
        // vermek yerine, bu durumda orijinali koruruz.
        if (originalBytes > 0L && compressedBytes >= originalBytes) {
            outputFile.delete()
            file.copyTo(outputFile, overwrite = true)
            compressedBytes = originalBytes
        }

        return CompressionResult(outputFile, originalBytes, compressedBytes)
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * [password] hem kullanıcı hem sahip parolası olarak kullanılır (iOS `userPasswordOption` /
     * `ownerPasswordOption` ile aynı davranış — açan herkes aynı zamanda tam yetkiye sahip olur).
     */
    fun encrypt(file: File, password: String, context: Context): File {
        PdfBoxInit.ensure(context)
        val outputFile = OutputPaths.outputFile(context, "Sifreli_${file.nameWithoutExtension}", "pdf")

        val document = try {
            PDDocument.load(file)
        } catch (e: InvalidPasswordException) {
            // Kullanıcı parolalı dosyalar burada InvalidPasswordException fırlatır (isEncrypted'e
            // hiç ulaşılamaz); yalnızca sahip parolalı dosyalar aşağıdaki isEncrypted kontrolüne düşer.
            throw PdfToolsException.AlreadyEncrypted()
        } catch (e: IOException) {
            throw PdfToolsException.CannotOpen()
        }

        try {
            if (document.isEncrypted) throw PdfToolsException.AlreadyEncrypted()

            val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply {
                encryptionKeyLength = 128
                setPreferAES(true)
            }
            document.protect(policy)
            document.save(outputFile)
        } catch (e: PdfToolsException) {
            throw e
        } catch (e: Exception) {
            throw PdfToolsException.EncryptionFailed(e.message)
        } finally {
            document.close()
        }

        return outputFile
    }
}
