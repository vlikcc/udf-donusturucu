package com.velikececi.udfdonusturucu.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.velikececi.udfdonusturucu.BuildConfig

/**
 * AnalyticsService.swift'in Kotlin karşılığı — ham loglama arayüzü. Tip-güvenli 13 olay için
 * [AnalyticsEvents] kullanılır; bu dosya yalnızca "nereye" (Firebase mi, no-op mu) sorusuna cevap
 * verir.
 */
interface Analytics {
    fun log(name: String, params: Map<String, Any?> = emptyMap())
}

/**
 * `google-services.json` build'e dahil değilse (bkz. app/build.gradle.kts `hasFirebaseConfig`)
 * ya da [FirebaseApp] herhangi bir nedenle kurulamamışsa kullanılan yedek — iOS'taki
 * `Bundle.main.path(forResource: "GoogleService-Info")` guard'ının karşılığı. DEBUG derlemede
 * olayları Logcat'e yazar, RELEASE'te sessizdir.
 */
object NoopAnalytics : Analytics {
    private const val TAG = "Analytics"

    override fun log(name: String, params: Map<String, Any?>) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$name $params")
    }
}

class FirebaseAnalyticsService(context: Context) : Analytics {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun log(name: String, params: Map<String, Any?>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Double -> bundle.putDouble(key, value)
                // Firebase Analytics parametreleri Boolean kabul etmez — iOS'un Bool → NSNumber
                // dönüşümüyle aynı BigQuery şemasında kalmak için "true"/"false" String gönderilir.
                is Boolean -> bundle.putString(key, value.toString())
                else -> bundle.putString(key, value.toString())
            }
        }
        firebaseAnalytics.logEvent(name, bundle)
    }
}

/** [FirebaseApp.getApps] boşsa (plugin uygulanmadıysa ya da başlatma başarısızsa) no-op'a düşer. */
fun createAnalytics(context: Context): Analytics =
    if (BuildConfig.HAS_FIREBASE && FirebaseApp.getApps(context).isNotEmpty()) {
        FirebaseAnalyticsService(context)
    } else {
        NoopAnalytics
    }
