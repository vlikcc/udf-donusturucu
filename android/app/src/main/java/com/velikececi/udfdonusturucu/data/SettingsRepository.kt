package com.velikececi.udfdonusturucu.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Genel uygulama ayarları: onboarding tamamlanma durumu ve geçişli reklam sayacı
 * (AdsManager tarafından "her 2. sonuç ekranında" kuralı için kullanılır — Faz 5).
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboardingDone")
        private val INTERSTITIAL_COUNTER_KEY = intPreferencesKey("interstitialCounter")
        const val INTERSTITIAL_FREQUENCY = 2
    }

    val isOnboardingDone: Flow<Boolean> =
        context.appDataStore.data.map { prefs -> prefs[ONBOARDING_DONE_KEY] ?: false }

    suspend fun setOnboardingDone() {
        context.appDataStore.edit { prefs -> prefs[ONBOARDING_DONE_KEY] = true }
    }

    /** Çağrıldığı her seferde sayacı artırır ve interstitial gösterilmesi gerekip gerekmediğini döner. */
    suspend fun shouldShowInterstitial(): Boolean {
        var shouldShow = false
        context.appDataStore.edit { prefs ->
            val next = (prefs[INTERSTITIAL_COUNTER_KEY] ?: 0) + 1
            prefs[INTERSTITIAL_COUNTER_KEY] = next
            shouldShow = next % INTERSTITIAL_FREQUENCY == 0
        }
        return shouldShow
    }
}
