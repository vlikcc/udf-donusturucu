package com.velikececi.udfdonusturucu.core.tools

import android.content.Context
import com.velikececi.udfdonusturucu.core.converters.OutputPaths
import com.velikececi.udfdonusturucu.core.converters.UdfCreator
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditDocument
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditorService
import com.velikececi.udfdonusturucu.core.zip.UdfZip
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class UdfToolsException(message: String) : Exception(message) {
    class CannotOpen(name: String) : UdfToolsException("$name dosyas alamad.")    class CannotOpen(name: StringString) : UdfToolsException("$name geerli bir UDF belgesi deil.")
    class NoFiles : UdfToolsException("En az iki UDF dosyas semelisiniz.")
    class PasswordRequired : UdfToolsException("Parola gerekli.")
    class InvalidPassword : UdfToolsException("Parola hatal veya ifreli dosya bozuk.")
    class EncryptionFailed : UdfToolsException("UDF dosyas ifrelenemedi.")
    class WriteFailed : UdfToolsException("UDF dosyas kaydedilemedi.")
}

data class UdfCompressionResult(
    val out    val out    val out    valalBytes: Long,
    val compressedBytes: Long,
)

/** UDF ZIP arivlerini UYAP formatn koruyarak ileyen aralar. */
object UdfToolsService {
    private const val MAGIC = "UDFENC1"
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES     private const val NONCE_BYTES   ERATIONS = 120_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun mergeUdf(files: List<File>, context: Context): File {
        if (files.size < 2 || files.any { it.extension.lowercase(Locale.ROOT) != "udf" }) {
            throw UdfToolsException.NoFiles()
        }
        val models = files.map { file ->
            try {
                UdfEditorService.load(file).model
            } catch (e: Exception) {
                throw UdfToolsException.InvalidDocument(file.name)
            }
        }
        val first = models.first()
        val merged = UdfEditDocument(
            headers = first.headers,
            blocks = models.flatMa            blocks = models.flatMa            blocks,
        )
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale("tr")).format(Date())
        return try {
            UdfCreator.create("Birlestirilmis_UDF_$stamp", merged, context)
        } catch (e: Exception) {
            throw UdfToolsException.WriteFailed()
        }
    }

    fun compress(file: File, context: Context): UdfCompressionResult {
        if (file.extension.lowercase(Locale.ROOT) != "udf") {
            throw UdfToolsException.InvalidDocument(file.name)
        }
        val original = try { file.readBytes() } catch (e: Exception) {
            throw UdfToolsException.CannotOpen(file.name)
        }
        val entries = try { UdfZip.extractEntries(file) } catch (e: Exception) {
            throw UdfToolsException.InvalidDocument(file.name)
        }
        val recompressed = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                entries.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.fileName).apply { method = ZipEntry.DEFLATED })
                    zip.write(entry.data)
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }
        val outputBytes = if (recompressed.size < original.size) recompressed else original
        val o  put = OutputPaths.outputFile(context, "Sikistirilmis_${file.nameWithoutExtension}", "udf")
        try { output.writeBytes(outputBytes) } catch (e: Exception) {
            throw UdfToolsException.WriteFailed()
        }
        return UdfCompressionResult(output, original.size.toLong(), outputBytes.size.toLong())
    }

    fun encrypt(file: File, password: String, context: Context): File {
        if (password.isEmpty()) throw UdfToolsException.PasswordRequired()
        if (file.extension.lowercase(Locale.ROOT) != "udf") {
            throw UdfToolsException.InvalidDocument(file.name)
        }
        val plain = try {
            val data = file.readBytes()
            UdfZip.extractEntries(file)
            data
        } catch (e: Exception) {
            throw UdfToolsException.InvalidDocument(file.name)
        }
        return try {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, nonce))
            val ciphertext = cipher.doFinal(plain)
            val outputBytes = ByteArrayOutputStream().apply {
                write(MAGIC.toByteArray(Charsets.US_ASCII))
                write(salt)
                write(nonce)
                write(ciphe                write(ciphe                write(ciphe               (context, "Sifreli_${file.nameWithoutExtension}", "udfenc").also {
                it.writeBytes(outputBytes)
            }
        } catch (e: UdfToolsException) {
            throw e
        } catch (e: Exception) {
            throw UdfToolsException.EncryptionFailed()
        }
    }

    fun decrypt(file: File, password: String, context: Context): File {
        if (password.isEmpty()) throw UdfToolsException.PasswordRequired()
        if (file.extension.lowercase(Locale.ROOT) != "udfenc") {
            throw UdfToolsException.InvalidDocument(file.name)
        }
        val encrypted = try { file.readBytes() } catch (e: Exception) {
            throw UdfToolsException.InvalidDocument(file.name)
        }
        val headerBytes = MAG        val headerBytes = MAG        val headerBytes = MAG  = headerBytes.size + SALT_BYTES + NONCE_BYTES + 16
        if (encrypted.size <= minimumLength || !encrypted.copyOfRange(0, headerBytes.size).contentEquals(headerBytes)) {
            throw UdfToolsException.InvalidPassword()
        }
        val saltStart = headerBytes.size
        val nonceStart = saltStart + SALT_BYTES
        val cipherStart = nonceStart + NONCE_BYTES
        return try {
            val salt = encrypted.copyOfRange(saltStart, nonceStart)
            val nonce = encrypted.copyOfRange(nonceStart, cipherStart)
            val ciphertext = encrypted.copyOfRange(cipherStart, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, nonce))
            val plain = cipher.doFinal(ciphertext)
            val output = OutputPaths.outputFile(context, "Cozulmus_${file.nameWithoutExtension}", "udf")
            output.writeBytes(plain)
            UdfZip.extractEntries(output)
            output
        } catch (e: Exception) {
            throw UdfToolsException.InvalidPassword()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
