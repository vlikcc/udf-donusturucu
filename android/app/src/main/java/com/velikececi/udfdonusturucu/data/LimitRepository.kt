package com.velikececi.udfdonusturucu.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * LimitService.swift'in Kotlin karşılığı. Günlük 3 ücretsiz dönüşüm hakkı, ödüllü reklamla
 * kazanılan bonus haklar ve premium durumu DataStore üzerinden yönetir.
 *
 * Gece yarısı sıfırlama iOS'taki `resetIfNewDay()` ile birebir aynı mantıkla çalışır:
 * `bugünEpochGün > sonSıfırlamaEpochGünü` ise sayaçlar sıfırlanır. Kontrol+düşüm işlemi
 * yarış durumlarına karşı [useConversion] içinde tek bir `edit {}` bloğunda yapılır.
 */
class LimitRepository(
    private val context: Context,
    externalScope: CoroutineScope,
) {
    companion object {
        const val MAX_FREE_CONVERSIONS = 3

        private val DAILY_COUNT_KEY = intPreferencesKey("dailyConversionCount")
        private val LAST_RESET_EPOCH_DAY_KEY = longPreferencesKey("lastResetEpochDay")
        private val PREMIUM_KEY = booleanPreferencesKey("isPremiumUser")
        private val BONUS_KEY = intPreferencesKey("bonusConversions")
    }

    data class LimitState(
        val isPremium: Boolean = false,
        val remainingConversions: Int = MAX_FREE_CONVERSIONS,
    ) {
        val canConvert: Boolean get() = isPremium || remainingConversions > 0
    }

    private val _state = MutableStateFlow(LimitState())
    val state: StateFlow<LimitState> = _state

    init {
        context.appDataStore.data
            .onEach { prefs ->
                val isPremium = prefs[PREMIUM_KEY] ?: false
                val remaining = if (isPremium) {
                    Int.MAX_VALUE
                } else {
                    val used = prefs[DAILY_COUNT_KEY] ?: 0
                    val bonus = prefs[BONUS_KEY] ?: 0
                    (MAX_FREE_CONVERSIONS + bonus - used).coerceAtLeast(0)
                }
                _state.value = LimitState(isPremium = isPremium, remainingConversions = remaining)
            }
            .launchIn(externalScope)

        externalScope.launch { resetIfNewDay() }
    }

    /** Uygulama ön plana geldiğinde (ProcessLifecycleOwner ON_START) çağrılır. */
    suspend fun resetIfNewDay() {
        val todayEpochDay = LocalDate.now().toEpochDay()
        context.appDataStore.edit { prefs ->
            val lastReset = prefs[LAST_RESET_EPOCH_DAY_KEY]
            if (lastReset == null) {
                prefs[LAST_RESET_EPOCH_DAY_KEY] = todayEpochDay
            } else if (todayEpochDay > lastReset) {
                prefs[DAILY_COUNT_KEY] = 0
                prefs[BONUS_KEY] = 0
                prefs[LAST_RESET_EPOCH_DAY_KEY] = todayEpochDay
            }
        }
    }

    /** [count] dönüşüm hakkını atomik biçimde kontrol edip düşer. Yeterli hak yoksa false döner. */
    suspend fun useConversion(count: Int = 1): Boolean {
        var success = false
        context.appDataStore.edit { prefs ->
            val isPremium = prefs[PREMIUM_KEY] ?: false
            if (isPremium) {
                success = true
                return@edit
            }

            val todayEpochDay = LocalDate.now().toEpochDay()
            var used = prefs[DAILY_COUNT_KEY] ?: 0
            var bonus = prefs[BONUS_KEY] ?: 0
            val lastReset = prefs[LAST_RESET_EPOCH_DAY_KEY]
            if (lastReset == null || todayEpochDay > lastReset) {
                used = 0
                bonus = 0
                prefs[LAST_RESET_EPOCH_DAY_KEY] = todayEpochDay
            }

            val totalAllowed = MAX_FREE_CONVERSIONS + bonus
            if (used + count > totalAllowed) {
                prefs[DAILY_COUNT_KEY] = used
                prefs[BONUS_KEY] = bonus
                success = false
                return@edit
            }

            prefs[DAILY_COUNT_KEY] = used + count
            prefs[BONUS_KEY] = bonus
            success = true
        }
        return success
    }

    /** Ödüllü reklam izlendiğinde çağrılır: +[count] bonus dönüşüm hakkı ekler. */
    suspend fun addBonusConversions(count: Int) {
        context.appDataStore.edit { prefs ->
            val current = prefs[BONUS_KEY] ?: 0
            prefs[BONUS_KEY] = current + count
        }
    }

    /** Premium satın alma tamamlandığında çağrılır. */
    suspend fun activatePremium() {
        context.appDataStore.edit { prefs -> prefs[PREMIUM_KEY] = true }
    }

    /** Play Billing `queryPurchasesAsync` taramasından (satın alma geri yükleme) çağrılır. */
    suspend fun restorePremiumStatus(isPremium: Boolean) {
        context.appDataStore.edit { prefs -> prefs[PREMIUM_KEY] = isPremium }
    }
}
