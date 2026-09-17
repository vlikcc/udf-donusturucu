package com.velikececi.udfdonusturucu.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** ConversionRecord.swift'in Kotlin karşılığı. */
@Serializable
data class ConversionRecord(
    val id: String = UUID.randomUUID().toString(),
    val originalFileName: String,
    val outputFormat: String,
    val dateEpochMillis: Long,
    val success: Boolean,
    val outputPath: String? = null,
    val outputFileName: String? = null,
) {
    /**
     * Dosyayı çözümler: önce [outputFileName] ile `ConvertedFiles/` altında arar (kalıcı
     * çalıştırmalar arası güvenilir), sonra tam yola geri döner (yalnızca aynı çalıştırma
     * içinde güvenilir) — iOS `resolvedURL` mantığıyla birebir aynı.
     */
    fun resolvedFile(context: Context): File? {
        outputFileName?.let { name ->
            val candidate = File(HistoryRepository.outputDirectory(context), name)
            if (candidate.exists()) return candidate
        }
        outputPath?.let { path ->
            val candidate = File(path)
            if (candidate.exists()) return candidate
        }
        return null
    }

    fun fileExists(context: Context): Boolean = resolvedFile(context) != null

    companion object {
        fun create(
            originalFileName: String,
            outputFormat: String,
            success: Boolean,
            outputPath: String? = null,
        ): ConversionRecord {
            val outputFileName = outputPath?.let { File(it).name }
            return ConversionRecord(
                originalFileName = originalFileName,
                outputFormat = outputFormat,
                dateEpochMillis = System.currentTimeMillis(),
                success = success,
                outputPath = outputPath,
                outputFileName = outputFileName,
            )
        }
    }
}

/**
 * ConversionStorage.swift'in Kotlin karşılığı. Dönüşüm geçmişini (en fazla 200 kayıt) JSON
 * olarak DataStore'da saklar; dosyalar `filesDir/ConvertedFiles/` altında tutulur.
 */
class HistoryRepository(
    private val context: Context,
    externalScope: CoroutineScope,
) {
    companion object {
        private const val MAX_RECORDS = 200
        private const val FREE_VISIBILITY_DAYS = 7L
        private const val PREMIUM_VISIBILITY_DAYS = 30L
        private val HISTORY_KEY = stringPreferencesKey("conversionHistory")
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(ConversionRecord.serializer())

        fun outputDirectory(context: Context): File =
            File(context.filesDir, "ConvertedFiles").apply { mkdirs() }
    }

    private val _records = MutableStateFlow<List<ConversionRecord>>(emptyList())
    val records: StateFlow<List<ConversionRecord>> = _records

    init {
        context.appDataStore.data
            .onEach { prefs ->
                val raw = prefs[HISTORY_KEY]
                _records.value = if (raw != null) {
                    runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            .launchIn(externalScope)
    }

    suspend fun addRecord(record: ConversionRecord) {
        persist(listOf(record) + _records.value)
    }

    suspend fun deleteRecord(record: ConversionRecord) {
        record.resolvedFile(context)?.delete()
        persist(_records.value.filterNot { it.id == record.id })
    }

    suspend fun clearHistory() {
        persist(emptyList())
    }

    /** Görünürlük penceresi: ücretsizde son 7 gün, premiumda son 30 gün. */
    fun recentRecords(isPremium: Boolean): List<ConversionRecord> {
        val windowDays = if (isPremium) PREMIUM_VISIBILITY_DAYS else FREE_VISIBILITY_DAYS
        val earliest = System.currentTimeMillis() - windowDays * 24 * 3600 * 1000
        return _records.value.filter { it.dateEpochMillis >= earliest }
    }

    /** Başarılı ve dosyası hâlâ diskte olan kayıtlar. */
    fun availableRecords(isPremium: Boolean): List<ConversionRecord> =
        recentRecords(isPremium).filter { it.success && it.fileExists(context) }

    private suspend fun persist(newRecords: List<ConversionRecord>) {
        val trimmed = newRecords.take(MAX_RECORDS)
        context.appDataStore.edit { prefs ->
            prefs[HISTORY_KEY] = json.encodeToString(serializer, trimmed)
        }
    }
}
