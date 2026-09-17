package com.velikececi.udfdonusturucu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_IS_PREMIUM = booleanPreferencesKey("is_premium")
        val KEY_REMAINING_CONVERSIONS = intPreferencesKey("remaining_conversions")
        val KEY_LAST_RESET_DAY = longPreferencesKey("last_reset_day")
        const val DAILY_FREE_LIMIT = 3
    }

    val isPremium: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_PREMIUM] ?: false
    }

    val remainingConversions: Flow<Int> = context.dataStore.data.map { preferences ->
        val lastReset = preferences[KEY_LAST_RESET_DAY] ?: 0L
        val today = LocalDate.now().toEpochDay()
        if (today > lastReset) {
            DAILY_FREE_LIMIT
        } else {
            preferences[KEY_REMAINING_CONVERSIONS] ?: DAILY_FREE_LIMIT
        }
    }

    suspend fun checkAndResetDailyLimit() {
        val today = LocalDate.now().toEpochDay()
        context.dataStore.edit { preferences ->
            val lastReset = preferences[KEY_LAST_RESET_DAY] ?: 0L
            if (today > lastReset) {
                preferences[KEY_LAST_RESET_DAY] = today
                preferences[KEY_REMAINING_CONVERSIONS] = DAILY_FREE_LIMIT
            }
        }
    }

    suspend fun useConversion(): Boolean {
        checkAndResetDailyLimit()
        val prefs = context.dataStore.data.first()
        val isPrem = prefs[KEY_IS_PREMIUM] ?: false
        if (isPrem) return true

        val remaining = prefs[KEY_REMAINING_CONVERSIONS] ?: DAILY_FREE_LIMIT
        if (remaining > 0) {
            context.dataStore.edit { it[KEY_REMAINING_CONVERSIONS] = remaining - 1 }
            return true
        }
        return false
    }

    suspend fun setPremium(premium: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_PREMIUM] = premium
        }
    }
}
