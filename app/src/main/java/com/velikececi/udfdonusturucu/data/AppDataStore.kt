package com.velikececi.udfdonusturucu.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** UserDefaults.standard yerine tek paylaşılan DataStore örneği. */
val Context.appDataStore by preferencesDataStore(name = "app_settings")
